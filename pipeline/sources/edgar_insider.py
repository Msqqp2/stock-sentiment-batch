"""
SEC EDGAR Form 4 내부자 거래 수집.
무료, API키 불필요, 10 req/초 한도.
"""

import logging
import time

import requests
from lxml import etree

from pipeline.config import EDGAR_HEADERS, EDGAR_RATE_LIMIT_SLEEP, EDGAR_TOP_N

logger = logging.getLogger(__name__)

CIK_MAPPING_URL = "https://www.sec.gov/files/company_tickers.json"
EDGAR_SUBMISSIONS = "https://data.sec.gov/submissions/CIK{cik}.json"

TXN_TYPE_MAP = {
    ("A", "A"): "Buy",
    ("S", "D"): "Sale",
    ("M", "A"): "Option Exercise",
    ("M", "D"): "Option Exercise",
    ("P", "A"): "Buy",
}


def load_cik_mapping() -> dict[str, str]:
    """SEC 공식 티커→CIK 매핑 (~15,000건, 1 req)."""
    resp = requests.get(CIK_MAPPING_URL, headers=EDGAR_HEADERS, timeout=30)
    resp.raise_for_status()
    data = resp.json()
    return {v["ticker"]: str(v["cik_str"]) for v in data.values()}


def get_recent_form4_filings(cik: str) -> list[dict]:
    """특정 CIK의 최근 Form 4 파일링 목록 (최대 5건)."""
    url = EDGAR_SUBMISSIONS.format(cik=cik.zfill(10))
    try:
        resp = requests.get(url, headers=EDGAR_HEADERS, timeout=15)
        resp.raise_for_status()
        data = resp.json()
    except Exception as e:
        logger.debug(f"[EDGAR] Form 4 filings 조회 실패 (CIK {cik}): {e}")
        return []

    filings = data.get("filings", {}).get("recent", {})
    forms = filings.get("form", [])

    form4_indices = [i for i, form in enumerate(forms) if form == "4"]
    results = []
    for idx in form4_indices[:5]:
        accession = filings["accessionNumber"][idx]
        primary_doc = filings["primaryDocument"][idx]
        acc_clean = accession.replace("-", "")
        doc_url = (
            f"https://www.sec.gov/Archives/edgar/data/"
            f"{cik.zfill(10)}/{acc_clean}/{primary_doc}"
        )
        results.append(
            {
                "filing_date": filings["filingDate"][idx],
                "accession": accession,
                "doc_url": doc_url,
            }
        )
    return results


def parse_form4_xml(doc_url: str) -> dict | None:
    """Form 4 XML → 내부자 이름, 직위, 거래 유형, 수량, 가격."""
    try:
        resp = requests.get(doc_url, headers=EDGAR_HEADERS, timeout=10)
        resp.raise_for_status()
        root = etree.fromstring(resp.content)

        # 네임스페이스 제거
        for elem in root.iter():
            if "}" in str(elem.tag):
                elem.tag = elem.tag.split("}", 1)[1]

        # 발행사 티커
        issuer_symbol = (
            root.findtext(".//issuerTradingSymbol", "").strip().upper()
        )

        # 내부자 정보
        owner = root.find(".//reportingOwner")
        if owner is None:
            return None

        insider_name = owner.findtext(".//rptOwnerName", "").strip()
        insider_title = (
            owner.findtext(".//officerTitle", "")
            or (
                "Director"
                if owner.findtext(".//isDirector") == "true"
                else ""
            )
        ).strip()

        # 가장 큰 거래 추출
        txns = root.findall(".//nonDerivativeTransaction")
        if not txns:
            return None

        best_txn = None
        best_shares = 0
        for txn in txns:
            shares_str = txn.findtext(".//transactionShares/value", "0")
            shares = int(float(shares_str)) if shares_str else 0
            if shares > best_shares:
                best_shares = shares
                best_txn = txn

        if best_txn is None:
            return None

        txn_code = best_txn.findtext(".//transactionCode", "")
        ad_code = best_txn.findtext(
            ".//transactionAcquiredDisposedCode/value", ""
        )
        txn_type = TXN_TYPE_MAP.get(
            (txn_code, ad_code), f"{txn_code}-{ad_code}"
        )

        price_str = best_txn.findtext(
            ".//transactionPricePerShare/value", "0"
        )
        price = float(price_str) if price_str else None

        shares_after_str = best_txn.findtext(
            ".//sharesOwnedFollowingTransaction/value", "0"
        )
        shares_after = (
            int(float(shares_after_str)) if shares_after_str else None
        )

        txn_date = best_txn.findtext(".//transactionDate/value", "")

        return {
            "issuer_symbol": issuer_symbol,
            "insider_name": insider_name,
            "insider_title": insider_title,
            "transaction_type": txn_type,
            "shares": best_shares,
            "price_per_share": price,
            "transaction_date": txn_date,
            "shares_owned_after": shares_after,
        }
    except Exception as e:
        logger.debug(f"[EDGAR] Form 4 파싱 실패 ({doc_url}): {e}")
        return None


def batch_collect_insider_trades(
    symbols_with_cik: dict[str, str],
) -> list[dict]:
    """
    매일 배치: 상위 종목의 최근 Form 4 수집.
    1,000종목 × 1 req = ~143초 (~2.5분).
    """
    all_trades = []
    total = min(len(symbols_with_cik), EDGAR_TOP_N)
    processed = 0

    for symbol, cik in list(symbols_with_cik.items())[:EDGAR_TOP_N]:
        filings = get_recent_form4_filings(cik)
        time.sleep(EDGAR_RATE_LIMIT_SLEEP)
        for f in filings:
            trade = parse_form4_xml(f["doc_url"])
            time.sleep(EDGAR_RATE_LIMIT_SLEEP)
            if trade:
                trade["symbol"] = symbol
                trade["filing_date"] = f["filing_date"]
                trade["accession_no"] = f["accession"]
                all_trades.append(trade)

        processed += 1
        if processed % 200 == 0:
            logger.info(
                f"[EDGAR] Form 4: {processed}/{total} 종목, "
                f"{len(all_trades)}건 수집"
            )

    logger.info(
        f"[EDGAR] Form 4 완료: {processed}종목, {len(all_trades)}건 수집"
    )
    return all_trades
