"""
기술적 지표 산출 — 가격 히스토리 기반.
RSI(14), ATR(14), MACD, Stochastic, ADX, CCI, Williams %R, 변동성, Performance.
"""

import logging
from datetime import date, timedelta

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)


def compute_technicals(hist: pd.DataFrame) -> dict:
    """
    200일 히스토리 DataFrame → 기술적 지표 딕셔너리.
    hist: columns=[Open, High, Low, Close, Volume]
    """
    if len(hist) < 26:
        return {}

    close = hist["Close"].values.astype(float)
    high = hist["High"].values.astype(float)
    low = hist["Low"].values.astype(float)

    result = {}

    # RSI(14)
    rsi = _rsi(close, 14)
    if rsi is not None:
        result["rsi_14"] = round(rsi, 2)

    # ATR(14)
    atr = _atr(high, low, close, 14)
    if atr is not None:
        result["atr_14"] = round(atr, 4)

    # 변동성 (주간/월간)
    if len(close) >= 5:
        returns = np.diff(np.log(close))
        result["volatility_w"] = round(float(np.std(returns[-5:]) * 100), 4)
    if len(close) >= 21:
        returns = np.diff(np.log(close))
        result["volatility_m"] = round(
            float(np.std(returns[-21:]) * 100), 4
        )

    # 20일 이동평균
    if len(close) >= 20:
        result["ma_20"] = round(float(np.mean(close[-20:])), 4)

    # MACD (12, 26, 9)
    macd_data = _macd(close)
    if macd_data:
        result.update(macd_data)

    # Stochastic (14, 3)
    stoch = _stochastic(high, low, close, 14, 3)
    if stoch:
        result.update(stoch)

    # ADX(14)
    adx = _adx(high, low, close, 14)
    if adx is not None:
        result["adx_14"] = round(adx, 2)

    # CCI(20)
    cci = _cci(high, low, close, 20)
    if cci is not None:
        result["cci_20"] = round(cci, 2)

    # Williams %R (14)
    wr = _williams_r(high, low, close, 14)
    if wr is not None:
        result["williams_r"] = round(wr, 2)

    return result


def compute_performance(hist: pd.DataFrame) -> dict:
    """
    가격 히스토리 → 기간별 수익률.
    1주/1월/3월/6월/1년/YTD.
    """
    if len(hist) < 5:
        return {}

    close = hist["Close"]
    current = float(close.iloc[-1])
    result = {}

    periods = {
        "perf_1w": 5,
        "perf_1m": 21,
        "perf_3m": 63,
        "perf_6m": 126,
        "perf_1y": 252,
    }

    for key, days in periods.items():
        if len(close) > days:
            past = float(close.iloc[-(days + 1)])
            if past > 0:
                result[key] = round((current - past) / past, 4)

    # YTD
    today = date.today()
    year_start = date(today.year, 1, 1)
    ytd_data = close[close.index >= pd.Timestamp(year_start)]
    if len(ytd_data) >= 2:
        first = float(ytd_data.iloc[0])
        if first > 0:
            result["perf_ytd"] = round((current - first) / first, 4)

    return result


def _rsi(close: np.ndarray, period: int = 14) -> float | None:
    if len(close) < period + 1:
        return None
    deltas = np.diff(close)
    gains = np.where(deltas > 0, deltas, 0)
    losses = np.where(deltas < 0, -deltas, 0)

    avg_gain = np.mean(gains[-period:])
    avg_loss = np.mean(losses[-period:])

    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    return 100.0 - (100.0 / (1.0 + rs))


def _atr(
    high: np.ndarray, low: np.ndarray, close: np.ndarray, period: int = 14
) -> float | None:
    if len(close) < period + 1:
        return None
    tr = np.maximum(
        high[1:] - low[1:],
        np.maximum(
            np.abs(high[1:] - close[:-1]),
            np.abs(low[1:] - close[:-1]),
        ),
    )
    return float(np.mean(tr[-period:]))


def _macd(close: np.ndarray) -> dict | None:
    if len(close) < 35:
        return None
    ema12 = _ema(close, 12)
    ema26 = _ema(close, 26)
    if ema12 is None or ema26 is None:
        return None
    macd_line = ema12 - ema26
    ema12_series = _ema_series(close, 12)
    ema26_series = _ema_series(close, 26)
    # 길이를 짧은 쪽에 맞춰 뒤에서부터 정렬
    min_len = min(len(ema12_series), len(ema26_series))
    macd_series = ema12_series[-min_len:] - ema26_series[-min_len:]
    if len(macd_series) < 9:
        return None
    signal = float(np.mean(macd_series[-9:]))
    macd_val = float(macd_line)
    return {
        "macd": round(macd_val, 4),
        "macd_signal": round(signal, 4),
        "macd_hist": round(macd_val - signal, 4),
    }


def _stochastic(
    high: np.ndarray,
    low: np.ndarray,
    close: np.ndarray,
    k_period: int = 14,
    d_period: int = 3,
) -> dict | None:
    if len(close) < k_period + d_period:
        return None

    k_values = []
    for i in range(d_period):
        idx = -(i + 1)
        h = np.max(high[idx - k_period + 1 : len(high) + idx + 1 if idx != -1 else len(high)])
        l = np.min(low[idx - k_period + 1 : len(low) + idx + 1 if idx != -1 else len(low)])
        if h - l == 0:
            k_values.append(50.0)
        else:
            k_values.append((close[idx] - l) / (h - l) * 100)

    stoch_k = k_values[0]
    stoch_d = np.mean(k_values)
    return {
        "stoch_k": round(float(stoch_k), 2),
        "stoch_d": round(float(stoch_d), 2),
    }


def _adx(
    high: np.ndarray, low: np.ndarray, close: np.ndarray, period: int = 14
) -> float | None:
    if len(close) < period * 2:
        return None

    tr = np.maximum(
        high[1:] - low[1:],
        np.maximum(
            np.abs(high[1:] - close[:-1]),
            np.abs(low[1:] - close[:-1]),
        ),
    )

    up_move = high[1:] - high[:-1]
    down_move = low[:-1] - low[1:]

    plus_dm = np.where((up_move > down_move) & (up_move > 0), up_move, 0)
    minus_dm = np.where((down_move > up_move) & (down_move > 0), down_move, 0)

    atr_val = np.mean(tr[-period:])
    if atr_val == 0:
        return None

    plus_di = np.mean(plus_dm[-period:]) / atr_val * 100
    minus_di = np.mean(minus_dm[-period:]) / atr_val * 100

    di_sum = plus_di + minus_di
    if di_sum == 0:
        return None

    dx = abs(plus_di - minus_di) / di_sum * 100
    return float(dx)


def _cci(
    high: np.ndarray, low: np.ndarray, close: np.ndarray, period: int = 20
) -> float | None:
    if len(close) < period:
        return None
    tp = (high + low + close) / 3
    tp_slice = tp[-period:]
    mean_tp = np.mean(tp_slice)
    mad = np.mean(np.abs(tp_slice - mean_tp))
    if mad == 0:
        return None
    return float((tp[-1] - mean_tp) / (0.015 * mad))


def _williams_r(
    high: np.ndarray, low: np.ndarray, close: np.ndarray, period: int = 14
) -> float | None:
    if len(close) < period:
        return None
    h = np.max(high[-period:])
    l = np.min(low[-period:])
    if h - l == 0:
        return None
    return float((h - close[-1]) / (h - l) * -100)


def _ema(data: np.ndarray, period: int) -> float | None:
    if len(data) < period:
        return None
    multiplier = 2 / (period + 1)
    ema = float(np.mean(data[:period]))
    for val in data[period:]:
        ema = (float(val) - ema) * multiplier + ema
    return ema


def _ema_series(data: np.ndarray, period: int) -> np.ndarray:
    if len(data) < period:
        return np.array([])
    multiplier = 2 / (period + 1)
    result = [float(np.mean(data[:period]))]
    for val in data[period:]:
        result.append((float(val) - result[-1]) * multiplier + result[-1])
    return np.array(result)
