package com.bioasistencia.data.models

import com.google.gson.annotations.SerializedName

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val correo: String,
    val contrasena: String
)

data class LoginResponse(
    @SerializedName("token", alternate = ["access_token"]) val token: String = "",
    @SerializedName("usuario", alternate = ["user", "data"]) val usuario: Usuario? = null,
    val message: String? = null
)

// ── Usuario (Admin / Coordinador) ─────────────────────────────────────────────

data class Usuario(
    @SerializedName("id_usuario", alternate = ["id", "user_id"]) val id: Int = 0,
    val nombre: String = "",
    val apellido: String = "",
    val correo: String = "",
    val rol: String = ""   // "admin" | "coordinador"
) {
    val nombreCompleto get() = "$nombre $apellido"
    val iniciales get() = "${nombre.firstOrNull() ?: ""}${apellido.firstOrNull() ?: ""}"
    val esAdmin get() = rol == "admin"
}

// ── Departamento ──────────────────────────────────────────────────────────────

data class Departamento(
    @SerializedName("id_departamento", alternate = ["id", "depto_id"]) val id: Int = 0,
    val nombre: String = "",
    val codigo: String = "",
    val activo: Boolean = true,
    // Calculados por la API
    val presentes: Int = 0,
    val ausentes: Int = 0,
    val tardanzas: Int = 0,
    val total: Int = 0
) {
    val porcentaje: Float get() =
        if (total > 0) (presentes + tardanzas).toFloat() / total * 100f else 0f
}

// ── Catedratico ───────────────────────────────────────────────────────────────

data class Catedratico(
    @SerializedName("id_catedratico", alternate = ["id", "cat_id"]) val id: Int = 0,
    val codigo: String = "",
    val nombre: String = "",
    val apellido: String = "",
    val correo: String? = null,
    @SerializedName("id_departamento", alternate = ["departamento_id", "id_depto"]) val idDepartamento: Int = 0,
    val departamento: String? = null,
    @SerializedName("tipo_contrato") val tipoContrato: String = "",
    val horario: String? = null,
    val turno: String = "",
    @SerializedName("cursos_asignados") val cursosAsignados: Int = 0,
    val activo: Boolean = true,
    // Calculados
    @SerializedName("huella_registrada") val huellaRegistradaRaw: Int = 0,
    @SerializedName("id_huella") val idHuella: Int? = null,
    @SerializedName("porcentaje_asistencia") val porcentajeAsistencia: Float? = null,
    @SerializedName("dias_asistidos") val diasAsistidos: Int? = null,
    @SerializedName("total_ausencias") val totalAusencias: Int? = null,
    @SerializedName("asistencia_hoy") val asistenciaHoy: EstadoAsistencia? = null
) {
    val nombreCompleto get() = "$nombre $apellido"
    val iniciales get() = "${nombre.firstOrNull() ?: ""}${apellido.firstOrNull() ?: ""}"
    val huellaRegistrada: Boolean get() = huellaRegistradaRaw == 1
    val tipoContratoDisplay get() = when (tipoContrato) {
        "tiempo_completo" -> "Tiempo completo"
        "medio_tiempo"    -> "Medio tiempo"
        "por_hora"        -> "Por hora"
        else              -> tipoContrato
    }
    val turnoDisplay get() = turno.replaceFirstChar { it.uppercase() }
}

// ── Asistencia ────────────────────────────────────────────────────────────────

data class Asistencia(
    @SerializedName("id_asistencia", alternate = ["id", "asist_id"]) val id: Int = 0,
    @SerializedName("id_catedratico", alternate = ["catedratico_id", "id_cat"]) val idCatedratico: Int = 0,
    @SerializedName("id_sensor", alternate = ["sensor_id"]) val idSensor: Int? = null,
    @SerializedName("fecha_hora", alternate = ["fecha"]) val fechaHora: String = "",
    val estado: EstadoAsistencia? = null,
    @SerializedName("metodo_registro") val metodoRegistro: String = "",
    val observacion: String? = null,
    val catedratico: Catedratico? = null
)

enum class EstadoAsistencia(val etiqueta: String) {
    @SerializedName("presente", alternate = ["Presente", "P"]) presente("Presente"),
    @SerializedName("ausente", alternate = ["Ausente", "A"]) ausente("Ausente"),
    @SerializedName("tardanza", alternate = ["Tardanza", "T"]) tardanza("Tardanza");

    companion object {
        fun from(value: String?): EstadoAsistencia =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ausente
    }
}

data class RegistrarAsistenciaRequest(
    @SerializedName("id_catedratico") val idCatedratico: Int,
    @SerializedName("id_sensor") val idSensor: Int? = null,
    val estado: String = "presente",
    @SerializedName("metodo_registro") val metodoRegistro: String = "biometrico",
    val observacion: String? = null
)

data class RegistrarAsistenciaResponse(
    val message: String,
    val asistencia: Asistencia,
    val catedratico: Catedratico
)

// ── Sensor ────────────────────────────────────────────────────────────────────

data class SensorEvento(
    @SerializedName("id_sensor") val idSensor: Int,
    @SerializedName("sensor_slot") val sensorSlot: Int,
    @SerializedName("id_catedratico") val idCatedratico: Int?,
    val timestamp: Long,
    val procesado: Boolean = false
)

// ── Reporte ───────────────────────────────────────────────────────────────────

data class ReporteRequest(
    @SerializedName("id_departamento") val idDepartamento: Int?,
    val periodo: String,
    @SerializedName("tipo_reporte") val tipoReporte: String
)

data class ReporteResumen(
    val periodo: String,
    @SerializedName("nombre_departamento") val nombreDepartamento: String,
    @SerializedName("total_catedraticos") val totalCatedraticos: Int,
    @SerializedName("promedio_asistencia") val promedioAsistencia: Float,
    @SerializedName("total_dias") val totalDias: Int,
    @SerializedName("datos_semana") val datosSemana: List<DatoDia>,
    @SerializedName("top_ausencias") val topAusencias: List<CatedraticoAusencia>
)

data class DatoDia(
    val dia: String,
    val fecha: String,
    val porcentaje: Float
)

data class CatedraticoAusencia(
    @SerializedName("id_catedratico") val id: Int,
    val nombre: String,
    val apellido: String,
    val departamento: String,
    val ausencias: Int
) {
    val nombreCompleto get() = "$nombre $apellido"
}

// ── Wrapper generico ──────────────────────────────────────────────────────────

data class ApiResponse<T>(
    val success: Boolean = false,
    val message: String? = null,
    @SerializedName("data", alternate = ["result", "results", "items"]) val data: T? = null
)
