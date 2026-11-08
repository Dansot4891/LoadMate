"""UTC clock and response timestamp helpers shared by all features."""

from datetime import datetime, timezone


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def as_utc(dt: datetime) -> datetime:
    """SQLite drops timezone information; interpret naive values as UTC."""
    return dt.replace(tzinfo=timezone.utc) if dt.tzinfo is None else dt.astimezone(timezone.utc)


def iso_utc(dt: datetime | None) -> str | None:
    return as_utc(dt).strftime("%Y-%m-%dT%H:%M:%SZ") if dt else None
