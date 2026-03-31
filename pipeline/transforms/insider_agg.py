"""
Form 4 내부자 거래 → 집계 컬럼 산출.
insider_buy_3m, insider_sell_3m, insider_net_shares_3m 등.
"""

import logging
from collections import defaultdict
from datetime import date, timedelta

logger = logging.getLogger(__name__)


def aggregate_insider_trades(trades: list[dict]) -> dict[str, dict]:
    """
    Form 4 거래 리스트 → 종목별 집계.
    Returns: {symbol: {"insider_buy_3m": N, ...}}
    """
    cutoff = (date.today() - timedelta(days=90)).isoformat()

    by_symbol = defaultdict(list)
    for t in trades:
        sym = t.get("symbol")
        if sym:
            by_symbol[sym].append(t)

    results = {}
    for sym, sym_trades in by_symbol.items():
        buy_count = 0
        sell_count = 0
        net_shares = 0
        latest_date = None
        latest_type = None

        for t in sym_trades:
            txn_date = t.get("transaction_date", "")
            txn_type = t.get("transaction_type", "")
            shares = t.get("shares", 0)

            # 최근 거래 추적
            if latest_date is None or txn_date > latest_date:
                latest_date = txn_date
                latest_type = txn_type

            # 90일 이내 집계
            if txn_date >= cutoff:
                if txn_type == "Buy":
                    buy_count += 1
                    net_shares += shares
                elif txn_type == "Sale":
                    sell_count += 1
                    net_shares -= shares

        results[sym] = {
            "insider_buy_3m": buy_count,
            "insider_sell_3m": sell_count,
            "insider_net_shares_3m": net_shares,
            "insider_latest_date": latest_date,
            "insider_latest_type": latest_type,
        }

    logger.info(f"[InsiderAgg] {len(results)}종목 집계 완료")
    return results
