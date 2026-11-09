"""박스별 적재 자리 (F12·F13)

Revision ID: d7e2b4a91c30
Revises: a1f3c9d27b10
"""
from alembic import op
import sqlalchemy as sa

revision = "d7e2b4a91c30"
down_revision = "a1f3c9d27b10"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "cargo_units",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("trip_id", sa.String(length=64), sa.ForeignKey("trips.id", ondelete="CASCADE"), nullable=False),
        sa.Column("stop_id", sa.String(length=64), nullable=True),
        sa.Column("cargo_line_id", sa.Integer(), nullable=True),
        sa.Column("pickup_id", sa.Integer(), nullable=True),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("item_name", sa.String(length=200), nullable=False),
        sa.Column("no", sa.Integer(), nullable=False),
        sa.Column("load_seq", sa.Integer(), nullable=False),
        sa.Column("state", sa.String(length=16), nullable=False),
        sa.Column("planned_zone", sa.String(length=16), nullable=True),
        sa.Column("planned_lane", sa.Integer(), nullable=True),
        sa.Column("planned_layer", sa.Integer(), nullable=True),
        sa.Column("zone", sa.String(length=16), nullable=True),
        sa.Column("lane", sa.Integer(), nullable=True),
        sa.Column("layer", sa.Integer(), nullable=True),
        sa.Column("position_source", sa.String(length=32), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_cargo_units_trip_id", "cargo_units", ["trip_id"])
    op.create_table(
        "placement_requests",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("trip_id", sa.String(length=64), sa.ForeignKey("trips.id", ondelete="CASCADE"), nullable=False),
        sa.Column("idempotency_key", sa.String(length=64), nullable=False),
        sa.Column("request_hash", sa.String(length=64), nullable=False),
        sa.Column("source", sa.String(length=32), nullable=False),
        sa.Column("events", sa.JSON(), nullable=False),
        sa.Column("observed_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("trip_id", "idempotency_key", name="uq_placement_idem"),
    )
    op.create_index("ix_placement_requests_trip_id", "placement_requests", ["trip_id"])


def downgrade() -> None:
    op.drop_index("ix_placement_requests_trip_id", table_name="placement_requests")
    op.drop_table("placement_requests")
    op.drop_index("ix_cargo_units_trip_id", table_name="cargo_units")
    op.drop_table("cargo_units")
