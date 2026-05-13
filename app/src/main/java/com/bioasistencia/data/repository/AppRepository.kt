package com.bioasistencia.data.repository

import com.bioasistencia.data.api.ApiService
import com.bioasistencia.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

class AppRepository(private val api: ApiService) {

    // Auth
    suspend fun login(correo: String, contrasena: String): Result<LoginResponse> =
        safe { api.login(LoginRequest(correo, contrasena)) }

    // Dashboard
    suspend fun getDashboard(): Result<List<Departamento>> =
        safeList { api.getDashboard() }

    // Departamentos
    suspend fun getDepartamentos(): Result<List<Departamento>> =
        safeList { api.getDepartamentos() }

    // Catedraticos
    suspend fun getCatedraticos(
        idDepartamento: Int? = null,
        buscar: String? = null,
        estado: String? = null
    ): Result<List<Catedratico>> =
        safeList { api.getCatedraticos(idDepartamento, buscar, estado) }

    suspend fun getCatedratico(id: Int): Result<Catedratico> =
        safeData { api.getCatedratico(id) }

    // Asistencias
    suspend fun getAsistencias(
        fecha: String? = null,
        idDepartamento: Int? = null,
        estado: String? = null,
        buscar: String? = null
    ): Result<List<Asistencia>> =
        safeList { api.getAsistencias(fecha, idDepartamento, estado, buscar) }

    suspend fun getAsistenciasCatedratico(
        id: Int,
        periodo: String? = null
    ): Result<List<Asistencia>> =
        safeList { api.getAsistenciasCatedratico(id, periodo) }

    suspend fun registrarAsistencia(req: RegistrarAsistenciaRequest): Result<RegistrarAsistenciaResponse> =
        withContext(Dispatchers.IO) {
            try {
                val r = api.registrarAsistencia(req)
                if (r.isSuccessful && r.body() != null) Result.Success(r.body()!!)
                else Result.Error("Error al registrar asistencia", r.code())
            } catch (e: Exception) {
                Result.Error("Error de red: ${e.localizedMessage}")
            }
        }

    suspend fun registrarManual(req: RegistrarAsistenciaRequest): Result<RegistrarAsistenciaResponse> =
        withContext(Dispatchers.IO) {
            try {
                val r = api.registrarManual(req)
                if (r.isSuccessful && r.body() != null) Result.Success(r.body()!!)
                else Result.Error("Error al registrar manualmente", r.code())
            } catch (e: Exception) {
                Result.Error("Error de red: ${e.localizedMessage}")
            }
        }

    // Sensor polling
    suspend fun getUltimoEvento(idSensor: Int): Result<SensorEvento?> =
        withContext(Dispatchers.IO) {
            try {
                val r = api.getUltimoEvento(idSensor)
                if (r.isSuccessful) Result.Success(r.body()?.data)
                else Result.Error("Sin eventos", r.code())
            } catch (e: Exception) {
                Result.Error("Error de red: ${e.localizedMessage}")
            }
        }

    // Reportes
    suspend fun generarReporte(req: ReporteRequest): Result<ReporteResumen> =
        safeData { api.generarReporte(req) }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun <T> safe(
        call: suspend () -> retrofit2.Response<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val r = call()
            if (r.isSuccessful && r.body() != null) Result.Success(r.body()!!)
            else Result.Error("Error ${r.code()}", r.code())
        } catch (e: Exception) {
            Result.Error("Error de red: ${e.localizedMessage}")
        }
    }

    private suspend fun <T> safeList(
        call: suspend () -> retrofit2.Response<ApiResponse<List<T>>>
    ): Result<List<T>> = withContext(Dispatchers.IO) {
        try {
            val r = call()
            if (r.isSuccessful) Result.Success(r.body()?.data ?: emptyList())
            else Result.Error("Error ${r.code()}", r.code())
        } catch (e: Exception) {
            Result.Error("Error de red: ${e.localizedMessage}")
        }
    }

    private suspend fun <T> safeData(
        call: suspend () -> retrofit2.Response<ApiResponse<T>>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val r = call()
            if (r.isSuccessful && r.body()?.data != null) Result.Success(r.body()!!.data!!)
            else Result.Error("No encontrado", r.code())
        } catch (e: Exception) {
            Result.Error("Error de red: ${e.localizedMessage}")
        }
    }
    suspend fun crearCatedratico(cat: Catedratico): Result<Catedratico> =
        safeData { api.crearCatedratico(cat) }

    suspend fun actualizarCatedratico(id: Int, cat: Catedratico): Result<Catedratico> =
        safeData { api.actualizarCatedratico(id, cat) }

    suspend fun eliminarCatedratico(id: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val r = api.eliminarCatedratico(id)
                if (r.isSuccessful) Result.Success(Unit)
                else Result.Error("Error al eliminar", r.code())
            } catch (e: Exception) {
                Result.Error("Error de red: ${e.localizedMessage}")
            }
        }

    suspend fun solicitarRegistroHuella(idCatedratico: Int): Result<Map<String, Any>> =
        safeData {
            api.iniciarRegistroHuella(
                mapOf("id_catedratico" to idCatedratico, "id_sensor" to 1)
            )
        }

}
