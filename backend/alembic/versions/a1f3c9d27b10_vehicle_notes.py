"""메모에 종류·위치·해결 시각 추가 (차량 노트)

Revision ID: a1f3c9d27b10
Revises: 573134294bb5
"""
from alembic import op
import sqlalchemy as sa

revision = "a1f3c9d27b10"
down_revision = "573134294bb5"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("memos", sa.Column("category", sa.String(length=16), nullable=False, server_default="other"))
    op.add_column("memos", sa.Column("location", sa.String(length=200), nullable=True))
    op.add_column("memos", sa.Column("resolved_at", sa.DateTime(timezone=True), nullable=True))
    op.execute("UPDATE memos SET category = 'place' WHERE scope_type = 'place'")


def downgrade() -> None:
    op.drop_column("memos", "resolved_at")
    op.drop_column("memos", "location")
    op.drop_column("memos", "category")
