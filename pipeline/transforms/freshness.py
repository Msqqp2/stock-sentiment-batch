"""
데이터 기준일(as-of date) 산출.
배치 실행일 ≠ 데이터 기준일.
"""

from datetime import date


def compute_freshness_dates(row: dict, source: str) -> dict:
    """소스별 데이터 기준일 산출."""
    today = date.today().isoformat()

    if source == "yfinance":
        row["asof_price"] = row.get("data_date", today)
        row["asof_technical"] = row.get("data_date", today)
        row["asof_valuation"] = row.get(
            "most_recent_quarter", row.get("data_date", today)
        )
        row["asof_financial"] = row.get("most_recent_quarter")
        row["asof_dividend"] = row.get("last_dividend_date")
        row["asof_short"] = row.get("date_short_interest")
        row["asof_insider"] = row.get("most_recent_quarter")
        row["asof_analyst"] = row.get("data_date", today)

    elif source == "edgar":
        row["asof_insider"] = row.get("insider_latest_date")
        row["asof_inst_13f"] = row.get("report_period")

    elif source == "fmp_etf":
        row["asof_etf"] = row.get("data_date", today)

    return row
