"""Contatori di utilizzo e spesa su SQLite.

Volutamente non registra né il testo ricevuto né le risposte: solo numeri.
"""

from __future__ import annotations

import sqlite3
import threading
import time
from datetime import datetime, timezone

SCHEMA = """
CREATE TABLE IF NOT EXISTS device_usage (
    device_id TEXT NOT NULL,
    day       TEXT NOT NULL,
    requests  INTEGER NOT NULL DEFAULT 0,
    chars     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (device_id, day)
);

CREATE TABLE IF NOT EXISTS daily_spend (
    day       TEXT PRIMARY KEY,
    cost_usd  REAL NOT NULL DEFAULT 0,
    requests  INTEGER NOT NULL DEFAULT 0,
    in_tokens INTEGER NOT NULL DEFAULT 0,
    out_tokens INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS hits (
    device_id TEXT NOT NULL,
    ts        REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_hits ON hits(device_id, ts);

CREATE TABLE IF NOT EXISTS flags (
    name  TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""


def utc_day(at: float | None = None) -> str:
    moment = datetime.fromtimestamp(at or time.time(), tz=timezone.utc)
    return moment.strftime("%Y-%m-%d")


class UsageStore:
    """Accesso sincronizzato ai contatori."""

    def __init__(self, path: str) -> None:
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        with self._lock:
            self._conn.executescript(SCHEMA)
            self._conn.commit()

    # -- lettura ---------------------------------------------------------

    def device_usage(self, device_id: str, day: str | None = None) -> tuple[int, int]:
        day = day or utc_day()
        with self._lock:
            row = self._conn.execute(
                "SELECT requests, chars FROM device_usage WHERE device_id=? AND day=?",
                (device_id, day),
            ).fetchone()
        return (row["requests"], row["chars"]) if row else (0, 0)

    def spend_today(self, day: str | None = None) -> float:
        day = day or utc_day()
        with self._lock:
            row = self._conn.execute(
                "SELECT cost_usd FROM daily_spend WHERE day=?", (day,)
            ).fetchone()
        return float(row["cost_usd"]) if row else 0.0

    def recent_hits(self, device_id: str, window_seconds: int, now: float | None = None) -> int:
        now = now if now is not None else time.time()
        with self._lock:
            row = self._conn.execute(
                "SELECT COUNT(*) AS n FROM hits WHERE device_id=? AND ts >= ?",
                (device_id, now - window_seconds),
            ).fetchone()
        return int(row["n"])

    def flag(self, name: str) -> str | None:
        with self._lock:
            row = self._conn.execute(
                "SELECT value FROM flags WHERE name=?", (name,)
            ).fetchone()
        return row["value"] if row else None

    # -- scrittura -------------------------------------------------------

    def record_hit(self, device_id: str, now: float | None = None) -> None:
        now = now if now is not None else time.time()
        with self._lock:
            self._conn.execute(
                "INSERT INTO hits(device_id, ts) VALUES (?, ?)", (device_id, now)
            )
            # Pulizia opportunistica: la tabella non deve crescere all'infinito.
            self._conn.execute("DELETE FROM hits WHERE ts < ?", (now - 3600,))
            self._conn.commit()

    def record_request(
        self,
        device_id: str,
        chars: int,
        cost_usd: float,
        in_tokens: int,
        out_tokens: int,
        day: str | None = None,
    ) -> None:
        day = day or utc_day()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO device_usage(device_id, day, requests, chars)
                VALUES (?, ?, 1, ?)
                ON CONFLICT(device_id, day) DO UPDATE SET
                    requests = requests + 1,
                    chars = chars + excluded.chars
                """,
                (device_id, day, chars),
            )
            self._conn.execute(
                """
                INSERT INTO daily_spend(day, cost_usd, requests, in_tokens, out_tokens)
                VALUES (?, ?, 1, ?, ?)
                ON CONFLICT(day) DO UPDATE SET
                    cost_usd = cost_usd + excluded.cost_usd,
                    requests = requests + 1,
                    in_tokens = in_tokens + excluded.in_tokens,
                    out_tokens = out_tokens + excluded.out_tokens
                """,
                (day, cost_usd, in_tokens, out_tokens),
            )
            self._conn.commit()

    def set_flag(self, name: str, value: str) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT INTO flags(name, value) VALUES (?, ?) "
                "ON CONFLICT(name) DO UPDATE SET value=excluded.value",
                (name, value),
            )
            self._conn.commit()

    def close(self) -> None:
        with self._lock:
            self._conn.close()
