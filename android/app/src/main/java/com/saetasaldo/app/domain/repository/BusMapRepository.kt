package com.saetasaldo.app.domain.repository

import com.saetasaldo.app.domain.model.BusPosition
import com.saetasaldo.app.domain.model.LineGroup
import com.saetasaldo.app.domain.model.LineRoute
import com.saetasaldo.app.domain.model.MapConfig

interface BusMapRepository {
    suspend fun lineTree(): Result<LineGroup>
    suspend fun route(codLinea: String): Result<LineRoute>
    suspend fun positions(codLinea: String): Result<List<BusPosition>>
    suspend fun lineNews(codLinea: String): Result<List<String>>
    suspend fun config(): Result<MapConfig>
}
