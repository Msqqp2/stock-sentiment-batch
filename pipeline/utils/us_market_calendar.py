"""
미국 장 영업일 판별 (공휴일 스킵).
GitHub Actions에서 실행 → is_trading_day 출력.
"""

import os
from datetime import date

US_MARKET_HOLIDAYS_2026 = {
    date(2026, 1, 1),    # New Year's Day
    date(2026, 1, 19),   # Martin Luther King Jr. Day
    date(2026, 2, 16),   # Presidents' Day
    date(2026, 4, 3),    # Good Friday
    date(2026, 5, 25),   # Memorial Day
    date(2026, 6, 19),   # Juneteenth
    date(2026, 7, 3),    # Independence Day (observed)
    date(2026, 9, 7),    # Labor Day
    date(2026, 11, 26),  # Thanksgiving Day
    date(2026, 12, 25),  # Christmas Day
}

US_MARKET_HOLIDAYS_2027 = {
    date(2027, 1, 1),    # New Year's Day
    date(2027, 1, 18),   # Martin Luther King Jr. Day
    date(2027, 2, 15),   # Presidents' Day
    date(2027, 3, 26),   # Good Friday
    date(2027, 5, 31),   # Memorial Day
    date(2027, 6, 18),   # Juneteenth (observed)
    date(2027, 7, 5),    # Independence Day (observed)
    date(2027, 9, 6),    # Labor Day
    date(2027, 11, 25),  # Thanksgiving Day
    date(2027, 12, 24),  # Christmas Day (observed)
}

ALL_HOLIDAYS = US_MARKET_HOLIDAYS_2026 | US_MARKET_HOLIDAYS_2027


def is_us_trading_day(d: date | None = None) -> bool:
    """미국 장이 열리는 영업일인지 판별."""
    if d is None:
        d = date.today()

    if d.weekday() >= 5:  # 토(5), 일(6)
        return False

    if d in ALL_HOLIDAYS:
        return False

    return True


if __name__ == "__main__":
    today = date.today()
    trading = is_us_trading_day(today)
    print(f"Date: {today}, Is Trading Day: {trading}")

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"is_trading_day={'true' if trading else 'false'}\n")
