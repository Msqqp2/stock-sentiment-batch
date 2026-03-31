"""
미국 장 영업일 판별 (공휴일 스킵).
GitHub Actions에서 실행 → is_trading_day 출력.
알고리즘 기반으로 연도에 관계없이 공휴일 자동 산출.
"""

import os
from datetime import date, timedelta


def _nth_weekday(year: int, month: int, weekday: int, n: int) -> date:
    """month월의 n번째 weekday (0=월~6=일) 산출."""
    first = date(year, month, 1)
    offset = (weekday - first.weekday()) % 7
    return first + timedelta(days=offset + 7 * (n - 1))


def _easter(year: int) -> date:
    """Anonymous Gregorian Easter algorithm."""
    a = year % 19
    b, c = divmod(year, 100)
    d, e = divmod(b, 4)
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = divmod(c, 4)
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    month = (h + l - 7 * m + 114) // 31
    day = ((h + l - 7 * m + 114) % 31) + 1
    return date(year, month, day)


def _observed(d: date) -> date:
    """토요일→금요일, 일요일→월요일로 대체 관행."""
    if d.weekday() == 5:  # Saturday
        return d - timedelta(days=1)
    if d.weekday() == 6:  # Sunday
        return d + timedelta(days=1)
    return d


def us_market_holidays(year: int) -> set[date]:
    """연도의 미국 증시 휴장일 산출."""
    holidays = set()

    # New Year's Day
    holidays.add(_observed(date(year, 1, 1)))
    # Martin Luther King Jr. Day (1월 3번째 월요일)
    holidays.add(_nth_weekday(year, 1, 0, 3))
    # Presidents' Day (2월 3번째 월요일)
    holidays.add(_nth_weekday(year, 2, 0, 3))
    # Good Friday (부활절 2일 전)
    holidays.add(_easter(year) - timedelta(days=2))
    # Memorial Day (5월 마지막 월요일)
    may31 = date(year, 5, 31)
    offset = (may31.weekday() - 0) % 7  # 0=Monday
    holidays.add(may31 - timedelta(days=offset))
    # Juneteenth
    holidays.add(_observed(date(year, 6, 19)))
    # Independence Day
    holidays.add(_observed(date(year, 7, 4)))
    # Labor Day (9월 1번째 월요일)
    holidays.add(_nth_weekday(year, 9, 0, 1))
    # Thanksgiving Day (11월 4번째 목요일)
    holidays.add(_nth_weekday(year, 11, 3, 4))
    # Christmas Day
    holidays.add(_observed(date(year, 12, 25)))

    return holidays


def is_us_trading_day(d: date | None = None) -> bool:
    """미국 장이 열리는 영업일인지 판별."""
    if d is None:
        d = date.today()

    if d.weekday() >= 5:  # 토(5), 일(6)
        return False

    if d in us_market_holidays(d.year):
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
