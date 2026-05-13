package com.bioasistencia.data.api

import com.bioasistencia.BuildConfig
import com.bioasistencia.data.models.*
import com.bioasistencia.utils.SessionManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

// ── ApiService ────────────────────────────────────────────────────────────────

interface ApiService {

    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    // Dashboard
    @GET("dashboard")
    suspend fun getDashboard(): Response<ApiResponse<List<Departamento>>>

    // Catedraticos
    @GET("catedraticos")
    suspend fun getCatedraticos(
        @Query("id_departamento") idDepartamento: Int? = null,
        @Query("buscar") buscar: String? = null,
        @Query("estado") estado: String? = null
    ): Response<ApiResponse<List<Catedratico>>>

    @GET("catedraticos/{id}")
    suspend fun getCatedratico(@Path("id") id: Int): Response<ApiResponse<Catedratico>>

    @POST("catedraticos")
    suspend fun crearCatedratico(@Body c: Catedratico): Response<ApiResponse<Catedratico>>

    @PUT("catedraticos/{id}")
    suspend fun actualizarCatedratico(
        @Path("id") id: Int,
        @Body c: Catedratico
    ): Response<ApiResponse<Catedratico>>

    // Departamentos
    @GET("departamentos")
    suspend fun getDepartamentos(): Response<ApiResponse<List<Departamento>>>

    // Asistencias
    @GET("asistencias")
    suspend fun getAsistencias(
        @Query("fecha") fecha: String? = null,
        @Query("id_departamento") idDepartamento: Int? = null,
        @Query("estado") estado: String? = null,
        @Query("buscar") buscar: String? = null
    ): Response<ApiResponse<List<Asistencia>>>

    @GET("asistencias/catedratico/{id}")
    suspend fun getAsistenciasCatedratico(
        @Path("id") idCatedratico: Int,
        @Query("periodo") periodo: String? = null
    ): Response<ApiResponse<List<Asistencia>>>

    @POST("asistencias")
    suspend fun registrarAsistencia(
        @Body request: RegistrarAsistenciaRequest
    ): Response<RegistrarAsistenciaResponse>

    @POST("asistencias/manual")
    suspend fun registrarManual(
        @Body request: RegistrarAsistenciaRequest
    ): Response<RegistrarAsistenciaResponse>

    // Sensor — polling
    @GET("sensor/ultimo-evento")
    suspend fun getUltimoEvento(
        @Query("id_sensor") idSensor: Int
    ): Response<ApiResponse<SensorEvento?>>

    // Reportes
    @POST("reportes/generar")
    suspend fun generarReporte(@Body request: ReporteRequest): Response<ApiResponse<ReporteResumen>>

    // Eliminar catedratico (desactivar)
    @DELETE("catedraticos/{id}")
    suspend fun eliminarCatedratico(@Path("id") id: Int): Response<ApiResponse<Unit>>

    // Solicitar registro de huella desde la app
    @POST("sensor/iniciar-registro")
    suspend fun iniciarRegistroHuella(@Body body: Map<String, Int>): Response<ApiResponse<Map<String, Any>>>

}

// ── RetrofitClient ────────────────────────────────────────────────────────────

object RetrofitClient {

    // MySQL devuelve 0/1 como número; este adaptador convierte
    // 0 → false, cualquier otro número → true, "true"/"1" → true
    private val booleanAdapter = object : JsonDeserializer<Boolean> {
        override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): Boolean {
            val prim = json.asJsonPrimitive
            return when {
                prim.isBoolean -> prim.asBoolean
                prim.isNumber  -> prim.asInt != 0
                prim.isString  -> prim.asString == "1" || prim.asString.equals("true", ignoreCase = true)
                else           -> false
            }
        }
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(Boolean::class.java, booleanAdapter)
        .registerTypeAdapter(Boolean::class.javaObjectType, booleanAdapter)
        .create()

    fun create(sessionManager: SessionManager): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val token = sessionManager.getToken()
                val req = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        if (token != null) addHeader("Authorization", "Bearer $token")
                    }
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
