package com.saetasaldo.app.data.repository

import com.google.gson.Gson
import com.saetasaldo.app.data.remote.RedBusContractException
import com.saetasaldo.app.data.remote.RedBusHttpException
import com.saetasaldo.app.data.remote.RedBusNetworkException
import com.saetasaldo.app.data.remote.api.BusMapApiService
import com.saetasaldo.app.data.remote.dto.BusPositionsDto
import com.saetasaldo.app.data.remote.dto.LineGroupsDto
import com.saetasaldo.app.data.remote.dto.LineRouteDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class BusMapRepositoryTest {

    private val gson = Gson()
    private val apiService = mockk<BusMapApiService>()
    private val repository = BusMapRepositoryImpl(apiService)

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("busmap/$name")) {
            "Missing test fixture busmap/$name"
        }.readText()

    private fun gruposDto(): LineGroupsDto =
        gson.fromJson(loadFixture("grupos_lineas.json"), LineGroupsDto::class.java)

    private fun routeDto(): LineRouteDto =
        gson.fromJson(loadFixture("ruta_linea_100.json"), LineRouteDto::class.java)

    @Test
    fun `line tree is fetched once per process and cached`() = runBlocking {
        coEvery { apiService.getGruposLineas() } returns Response.success(gruposDto())

        assertTrue(repository.lineTree().isSuccess)
        assertTrue(repository.lineTree().isSuccess)

        coVerify(exactly = 1) { apiService.getGruposLineas() }
    }

    @Test
    fun `line tree failure is not cached so a retry hits the api`() = runBlocking {
        coEvery { apiService.getGruposLineas() } throws IOException("offline")

        assertTrue(repository.lineTree().isFailure)
        assertTrue(repository.lineTree().isFailure)

        coVerify(exactly = 2) { apiService.getGruposLineas() }
    }

    @Test
    fun `route is cached per codLinea`() = runBlocking {
        coEvery { apiService.getRutaLinea("100") } returns Response.success(routeDto())
        coEvery { apiService.getRutaLinea("101") } returns Response.success(routeDto())

        repository.route("100")
        repository.route("100")
        repository.route("101")

        coVerify(exactly = 1) { apiService.getRutaLinea("100") }
        coVerify(exactly = 1) { apiService.getRutaLinea("101") }
    }

    @Test
    fun `positions are never cached`() = runBlocking {
        coEvery { apiService.getPosicionesBuses("100") } returns
            Response.success(BusPositionsDto(error = 0, posiciones = emptyList()))

        repository.positions("100")
        repository.positions("100")

        coVerify(exactly = 2) { apiService.getPosicionesBuses("100") }
    }

    @Test
    fun `positions filters vehiculoNoVisibles and null proximaParada survives`() = runBlocking {
        val positions = gson.fromJson(loadFixture("posiciones_100.json"), BusPositionsDto::class.java)
            .posiciones.orEmpty() +
            gson.fromJson(
                """{"interno":"X","latitud":-24.8,"longitud":-65.4,"orientacion":0.0,"proximaParada":null,"vehiculoRampa":false,"vehiculoNoVisibles":true}""",
                com.saetasaldo.app.data.remote.dto.BusPositionDto::class.java
            )
        coEvery { apiService.getPosicionesBuses("100") } returns
            Response.success(BusPositionsDto(error = 0, posiciones = positions))

        val result = repository.positions("100")

        assertTrue(result.isSuccess)
        val buses = result.getOrNull().orEmpty()
        assertTrue(buses.none { it.interno == "X" })
        assertTrue(buses.isNotEmpty())
    }

    @Test
    fun `http failure propagates as Result failure`() = runBlocking {
        val errorBody = mockk<ResponseBody>(relaxed = true)
        coEvery { apiService.getPosicionesBuses("100") } returns Response.error(500, errorBody)

        val result = repository.positions("100")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusHttpException)
    }

    @Test
    fun `network failure propagates as Result failure`() = runBlocking {
        coEvery { apiService.getPosicionesBuses("100") } throws IOException("offline")

        val result = repository.positions("100")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusNetworkException)
    }

    @Test
    fun `error non zero body is a contract failure`() = runBlocking {
        coEvery { apiService.getGruposLineas() } returns
            Response.success(LineGroupsDto(error = 1, grupos = null))

        val result = repository.lineTree()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusContractException)
    }

    @Test
    fun `cancellation rethrows instead of being swallowed`() = runBlocking {
        coEvery { apiService.getPosicionesBuses("100") } throws CancellationException("cancelled")

        try {
            repository.positions("100")
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun `empty positions list is a success not a failure`() = runBlocking {
        coEvery { apiService.getPosicionesBuses("999") } returns
            Response.success(BusPositionsDto(error = 0, posiciones = emptyList()))

        val result = repository.positions("999")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().orEmpty().isEmpty())
    }

    @Test
    fun `news are flattened per line and cached`() = runBlocking {
        val newsDto = gson.fromJson(
            loadFixture("novedades_100.json"),
            com.saetasaldo.app.data.remote.dto.LineNewsDto::class.java
        )
        coEvery { apiService.getNovedadesLineas("100") } returns Response.success(newsDto)

        val first = repository.lineNews("100")
        val second = repository.lineNews("100")

        assertTrue(first.isSuccess)
        assertEquals(first.getOrNull(), second.getOrNull())
        coVerify(exactly = 1) { apiService.getNovedadesLineas("100") }
    }
}
