package com.bioasistencia.utils

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bioasistencia.data.models.Usuario
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

// ── SessionManager ────────────────────────────────────────────────────────────

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bioasistencia_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_TOKEN   = "token"
        private const val KEY_USUARIO = "usuario"
        private const val KEY_LOGGED  = "logged"
    }

    fun saveSession(token: String, usuario: Usuario) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USUARIO, gson.toJson(usuario))
            .putBoolean(KEY_LOGGED, true)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUsuario(): Usuario? =
        prefs.getString(KEY_USUARIO, null)?.let {
            try { gson.fromJson(it, Usuario::class.java) } catch (e: Exception) { null }
        }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED, false)

    fun clearSession() = prefs.edit().clear().apply()
}

// ── View extensions ───────────────────────────────────────────────────────────

fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun View.snack(msg: String, duration: Int = Snackbar.LENGTH_SHORT) =
    Snackbar.make(this, msg, duration).show()

fun Context.toast(msg: String) =
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Fragment.toast(msg: String) = requireContext().toast(msg)

// ── DateUtils ─────────────────────────────────────────────────────────────────

object DateUtils {

    private val fmtServer  = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fmtDisplay = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val fmtDate    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val fmtTime    = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fmtMonth   = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val fmtDayFull = SimpleDateFormat("EEEE d 'de' MMMM, yyyy", Locale("es", "GT"))

    fun today(): String = fmtDate.format(Date())

    fun currentMonth(): String = fmtMonth.format(Date())

    fun currentDayFull(): String =
        fmtDayFull.format(Date()).replaceFirstChar { it.uppercase() }

    fun formatTime(dateStr: String): String =
        try { fmtTime.format(fmtServer.parse(dateStr)!!) } catch (e: Exception) { dateStr }

    fun formatDisplay(dateStr: String): String =
        try { fmtDisplay.format(fmtServer.parse(dateStr)!!) } catch (e: Exception) { dateStr }

    fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "Buenos dias"
            h < 18 -> "Buenas tardes"
            else   -> "Buenas noches"
        }
    }
}

// ── String extensions ─────────────────────────────────────────────────────────

fun String.initials(): String =
    trim().split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

fun Float.toPercent(): String = "%.0f%%".format(this)
