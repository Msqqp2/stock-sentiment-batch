"""
SEC EDGAR 13F 기관 보유 수집.
분기 종료 후 45일간 새 13F 파일링 감지 → 파싱.
"""

import logging
import time
from datetime import datetime

import requests
from lxml import etree

from pipeline.config import EDGAR_HEADERS, EDGAR_RATE_LIMIT_SLEEP

logger = logging.getLogger(__name__)


def check_new_13f_filings(date_from: str) -> list[dict]:
    """
    EDGAR full-text search로 최근 13F-HR 파일링 감지.
    분기 보고 시즌(2/5/8/11월)에만 유의미한 결과.
    """
    try:
        url = "https://efts.sec.gov/LATEST/search-index"
        params = {
            "q": '"form-type":"13F-HR"',
            "dateRange": "custom",
            "startdt": date_from,
            "enddt": datetime.now().strftime("%Y-%m-%d"),
        }
        resp = requests.get(
            url, headers=EDGAR_HEADERS, params=params, timeout=30
        )
        resp.raise_for_status()
        search_results = resp.json()

        filings = []
        for hit in search_results.get("hits", {}).get("hits", []):
            source = hit.get("_source", {})
            filings.append(
                {
                    "cik": source.get("entity_cik"),
                    "entity_name": source.get("entity_name"),
                    "filing_date": source.get("file_date"),
                    "accession": source.get("accession_no"),
                }
            )

        logger.info(
            f"[EDGAR 13F] {date_from} 이후 신규 파일링: {len(filings)}건"
        )
        return filings

    except Exception as e:
        logger.warning(f"[EDGAR 13F] 파일링 조회 실패: {e}")
        return []


def parse_13f_holdings(accession_no: str, cik: str) -> list[dict]:
    """
    개별 13F-HR informationTable XML 파싱.
    Returns: [{"cusip": ..., "issuer": ..., "shares": ..., "value_thousands": ...}]
    """
    try:
        acc_clean = accession_no.replace("-", "")
        base_url = (
            f"https://www.sec.gov/Archives/edgar/data/{cik}/{acc_clean}"
        )

        # filing index에서 infotable 파일 탐색
        index_url = f"{base_url}/index.json"
        resp = requests.get(index_url, headers=EDGAR_HEADERS, timeout=30)
        resp.raise_for_status()
        index_data = resp.json()

        info_table_file = None
        for item in index_data.get("directory", {}).get("item", []):
            name = item.get("name", "").lower()
            if "infotable" in name and name.endswith(".xml"):
                info_table_file = item["name"]
                break

        if not info_table_file:
            return []

        # infotable XML 다운로드 및 파싱
        table_url = f"{base_url}/{info_table_file}"
        resp = requests.get(table_url, headers=EDGAR_HEADERS, timeout=30)
        resp.raise_for_status()
        root = etree.fromstring(resp.content)

        # 네임스페이스 제거
        for elem in root.iter():
            if "}" in str(elem.tag):
                elem.tag = elem.tag.split("}", 1)[1]

        holdings = []
        for info in root.findall(".//infoTable"):
            cusip = info.findtext("cusip", "").strip()
            issuer = info.findtext("nameOfIssuer", "").strip()
            shares_str = info.findtext(".//sshPrnamt", "0")
            value_str = info.findtext("value", "0")

            holdings.append(
                {
                    "cusip": cusip,
                    "issuer": issuer,
                    "shares": int(shares_str) if shares_str else 0,
                    "value_thousands": int(value_str) if value_str else 0,
                }
            )

        return holdings

    except Exception as e:
        logger.debug(
            f"[EDGAR 13F] Holdings 파싱 실패 ({accession_no}): {e}"
        )
        return []


def is_13f_season() -> bool:
    """분기 보고 시즌(2/5/8/11월)인지 확인."""
    return datetime.now().month in (2, 5, 8, 11)
