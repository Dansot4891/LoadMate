"""stop coordinates optional

검색으로 추가한 방문지는 주소만 저장하고 좌표는 내비 전달 때 조회한다.

Revision ID: b9d4e1a7c2f3
Revises: f2c6a8d35e19
"""
from alembic import op
import sqlalchemy as sa

revision = "b9d4e1a7c2f3"
down_revision = "f2c6a8d35e19"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.alter_column("stops", "latitude", existing_type=sa.Float(), nullable=True)
    op.alter_column("stops", "longitude", existing_type=sa.Float(), nullable=True)


def downgrade() -> None:
    # 좌표 없는 방문지가 있으면 실패한다. 임의 좌표를 채우지 않는다
    op.alter_column("stops", "longitude", existing_type=sa.Float(), nullable=False)
    op.alter_column("stops", "latitude", existing_type=sa.Float(), nullable=False)
