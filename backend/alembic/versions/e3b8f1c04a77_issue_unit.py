"""적재 이상이 가리키는 물품 (F30·F31)

Revision ID: e3b8f1c04a77
Revises: d7e2b4a91c30
"""
from alembic import op
import sqlalchemy as sa

revision = "e3b8f1c04a77"
down_revision = "d7e2b4a91c30"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("issues", sa.Column("unit_id", sa.Integer(), nullable=True))


def downgrade() -> None:
    op.drop_column("issues", "unit_id")
