package com.loadmate.app.feature.records.data.mapper

import com.loadmate.app.feature.records.data.dto.MemoDto
import com.loadmate.app.feature.records.domain.Memo

internal fun MemoDto.toDomain() = Memo(id, scopeType, scopeId, body, tags, visibility == "shared", persistence == "this_trip", source, authorId, updatedAt, updatedBy, category, location)
