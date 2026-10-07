package com.saetasaldo.app.data.repository

import com.saetasaldo.app.data.remote.RedBusContractException
import com.saetasaldo.app.data.remote.RedBusHttpException
import com.saetasaldo.app.data.remote.RedBusNetworkException
import com.saetasaldo.app.data.remote.api.BusMapApiService
import com.saetasaldo.app.data.remote.dto.toDomain
import com.saetasaldo.app.domain.model.BusPosition
import com.saetasaldo.app.domain.model.LineGroup
import com.saetasaldo.app.domain.model.LineRoute
import com.saetasaldo.app.domain.model.MapConfig
import com.saetasaldo.app.domain.repository.BusMapRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.io.IOException

/**
 * Catalog (line tree, routes, news, config) is cached in memory for the
 * process lifetime; positions are never cached so every poll hits the wire.
 */
class BusMapRepositoryImpl(
    private val apiService: BusMapApiService
) : BusMapRepository {

    private var lineTreeCache: LineGroup? = null
    private val lineTreeMutex = Mutex()
    private val routeCache = mutableMapOf<String, LineRoute>()
    private val routeMutex = Mutex()
    private val newsCache = mutableMapOf<String, List<String>>()
    private val newsMutex = Mutex()
    private var configCache: MapConfig? = null
    private val configMutex = Mutex()

    override suspend fun lineTree(): Result<LineGroup> =
        lineTreeMutex.withLock {
            lineTreeCache?.let { Result.success(it) } ?: run {
                fetch { apiService.getGruposLineas() }.mapCatching { dto ->
                    if (dto.error != 0 || dto.grupos == null) throw RedBusContractException()
                    dto.grupos.toDomain()
                }.onSuccess { lineTreeCache = it }
            }
        }

    override suspend fun route(codLinea: String): Result<LineRoute> =
        routeMutex.withLock {
            routeCache[codLinea]?.let { Result.success(it) } ?: run {
                fetch { apiService.getRutaLinea(codLinea) }.mapCatching { dto ->
                    if (dto.error != 0) throw RedBusContractException()
                    LineRoute(dto.nodos.orEmpty().mapNotNull { it.toDomain() })
                }.onSuccess { routeCache[codLinea] = it }
            }
        }

    override suspend fun positions(codLinea: String): Result<List<BusPosition>> =
        fetch { apiService.getPosicionesBuses(codLinea) }.mapCatching { dto ->
            if (dto.error != 0) throw RedBusContractException()
            dto.posiciones.orEmpty().mapNotNull { it.toDomain() }
        }

    override suspend fun lineNews(codLinea: String): Result<List<String>> =
        newsMutex.withLock {
            newsCache[codLinea]?.let { Result.success(it) } ?: run {
                fetch { apiService.getNovedadesLineas(codLinea) }.mapCatching { dto ->
                    if (dto.error != 0) throw RedBusContractException()
                    dto.novedadLineas.orEmpty().flatMap { it.novedades.orEmpty() }
                }.onSuccess { newsCache[codLinea] = it }
            }
        }

    override suspend fun config(): Result<MapConfig> =
        configMutex.withLock {
            configCache?.let { Result.success(it) } ?: run {
                fetch { apiService.getConfiguracion() }.mapCatching { dto ->
                    if (dto.error != 0) throw RedBusContractException()
                    dto.toDomain()
                }.onSuccess { configCache = it }
            }
        }

    private suspend fun <T> fetch(call: suspend () -> Response<T>): Result<T> =
        try {
            val response = call()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                Result.failure(RedBusHttpException(response.code()))
            } else {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(RedBusContractException())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(RedBusNetworkException(e))
        } catch (e: Exception) {
            Result.failure(RedBusContractException(e))
        }
}
