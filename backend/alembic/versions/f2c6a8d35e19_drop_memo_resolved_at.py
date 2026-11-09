"""메모의 해결 시각 제거 (고장·미반납이 끝나면 메모를 삭제한다)

Revision ID: f2c6a8d35e19
Revises: e3b8f1c04a77
"""
from alembic import op
import sqlalchemy as sa

revision = "f2c6a8d35e19"
down_revision = "e3b8f1c04a77"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.drop_column("memos", "resolved_at")


def downgrade() -> None:
    op.add_column("memos", sa.Column("resolved_at", sa.DateTime(timezone=True), nullable=True))
