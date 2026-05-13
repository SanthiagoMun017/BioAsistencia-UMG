package com.bioasistencia.ui.biometric

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bioasistencia.R
import com.bioasistencia.data.models.*
import com.bioasistencia.data.repository.AppRepository
import com.bioasistencia.data.repository.Result
import com.bioasistencia.databinding.DialogManualRegisterBinding
import com.bioasistencia.databinding.FragmentBiometricBinding
import com.bioasistencia.databinding.ItemRegistroRecienteBinding
import com.bioasistencia.ui.RepoViewModelFactory
import com.bioasistencia.utils.*
import kotlinx.coroutines.*

private const val SENSOR_ID   = 1
private const val POLL_MS     = 2000L

// ── ViewModel ─────────────────────────────────────────────────────────────────

class BiometricViewModel(private val repo: AppRepository) : ViewModel() {

    val registros       = androidx.lifecycle.MutableLiveData<List<Asistencia>>(emptyList())
    val ultimoRegistrado= androidx.lifecycle.MutableLiveData<RegistrarAsistenciaResponse?>()
    val totalCatedraticos = androidx.lifecycle.MutableLiveData(0)
    val error           = androidx.lifecycle.MutableLiveData<String?>()

    private var pollingJob: Job? = null
    private var lastTimestamp = 0L

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                checkSensor()
                delay(POLL_MS)
            }
        }
    }

    fun stopPolling() { pollingJob?.cancel() }

    private suspend fun checkSensor() {
        when (val r = repo.getUltimoEvento(SENSOR_ID)) {
            is Result.Success -> {
                val ev = r.data ?: return
                if (ev.timestamp > lastTimestamp && ev.idCatedratico != null) {
                    lastTimestamp = ev.timestamp
                    registrarDesde(ev.idCatedratico)
                }
            }
            else -> { /* silencioso durante polling */ }
        }
    }

    private suspend fun registrarDesde(idCatedratico: Int) {
        val req = RegistrarAsistenciaRequest(
            idCatedratico   = idCatedratico,
            idSensor        = SENSOR_ID,
            estado          = "presente",
            metodoRegistro  = "biometrico"
        )
        when (val r = repo.registrarAsistencia(req)) {
            is Result.Success -> {
                val current = registros.value?.toMutableList() ?: mutableListOf()
                current.add(0, r.data.asistencia.copy(catedratico = r.data.catedratico))
                registros.postValue(current)
                ultimoRegistrado.postValue(r.data)
            }
            is Result.Error -> error.postValue(r.message)
            else -> {}
        }
    }

    fun registrarManual(idCatedratico: Int, estado: String, obs: String?) {
        viewModelScope.launch {
            val req = RegistrarAsistenciaRequest(
                idCatedratico   = idCatedratico,
                idSensor        = null,
                estado          = estado,
                metodoRegistro  = "manual",
                observacion     = obs
            )
            when (val r = repo.registrarManual(req)) {
                is Result.Success -> {
                    val current = registros.value?.toMutableList() ?: mutableListOf()
                    current.add(0, r.data.asistencia.copy(catedratico = r.data.catedratico))
                    registros.postValue(current)
                    ultimoRegistrado.postValue(r.data)
                }
                is Result.Error -> error.postValue(r.message)
                else -> {}
            }
        }
    }

    fun loadTotalCatedraticos() {
        viewModelScope.launch {
            when (val r = repo.getCatedraticos()) {
                is Result.Success -> totalCatedraticos.postValue(r.data.size)
                else -> {}
            }
        }
    }

    override fun onCleared() { super.onCleared(); stopPolling() }
}

// ── Adapter registros recientes ───────────────────────────────────────────────

class RegistrosRecientesAdapter : RecyclerView.Adapter<RegistrosRecientesAdapter.VH>() {

    private val items = mutableListOf<Asistencia>()

    fun addFirst(a: Asistencia) { items.add(0, a); notifyItemInserted(0) }
    fun submitAll(list: List<Asistencia>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

    inner class VH(val b: ItemRegistroRecienteBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) = VH(
        ItemRegistroRecienteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = items.size.coerceAtMost(10)

    override fun onBindViewHolder(h: VH, pos: Int) {
        val a   = items[pos]
        val cat = a.catedratico
        with(h.b) {
            tvInitials.text = cat?.iniciales ?: "?"
            tvNombre.text   = cat?.nombreCompleto ?: "Catedratico #${a.idCatedratico}"
            tvSub.text      = buildString {
                cat?.departamento?.let { append(it).append(" - ") }
                append(DateUtils.formatTime(a.fechaHora))
            }

            val ctx = root.context
            when (a.estado) {
                EstadoAsistencia.presente -> {
                    tvEstado.text = "Presente"
                    tvEstado.setBackgroundResource(R.drawable.badge_bg_success)
                    tvEstado.setTextColor(ctx.getColor(R.color.success))
                }
                EstadoAsistencia.tardanza -> {
                    tvEstado.text = "Tardanza"
                    tvEstado.setBackgroundResource(R.drawable.badge_bg_warning)
                    tvEstado.setTextColor(ctx.getColor(R.color.warning))
                }
                EstadoAsistencia.ausente -> {
                    tvEstado.text = "Ausente"
                    tvEstado.setBackgroundResource(R.drawable.badge_bg_danger)
                    tvEstado.setTextColor(ctx.getColor(R.color.danger))
                }
                null -> {
                    tvEstado.text = "Desconocido"
                }
            }
        }
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────

class BiometricFragment : Fragment() {

    private var _b: FragmentBiometricBinding? = null
    private val b get() = _b!!
    private val vm: BiometricViewModel by viewModels { RepoViewModelFactory.build(requireContext()) }
    private val adapter = RegistrosRecientesAdapter()
    private val pulseHandler = Handler(Looper.getMainLooper())
    private val pulseRunnable = object : Runnable {
        override fun run() {
            _b?.ivFingerprintRing?.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
            )
            pulseHandler.postDelayed(this, 2200)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentBiometricBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        b.tvFechaInfo.text = DateUtils.currentDayFull()
        b.rvRegistros.layoutManager = LinearLayoutManager(requireContext())
        b.rvRegistros.adapter = adapter

        b.btnManual.setOnClickListener { showManualDialog() }
        b.btnFinalizar.setOnClickListener {
            vm.stopPolling()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        vm.registros.observe(viewLifecycleOwner) { lista ->
            adapter.submitAll(lista)
            val total = vm.totalCatedraticos.value ?: 0
            b.tvProgress.text = "${lista.size} / $total catedraticos registrados"
            b.progressBar.progress = if (total > 0) (lista.size * 100 / total) else 0
        }

        vm.ultimoRegistrado.observe(viewLifecycleOwner) { resp ->
            resp ?: return@observe
            val nombre = resp.catedratico.nombreCompleto
            b.tvStatus.text = "Registrado: $nombre"
            b.tvStatus.setTextColor(requireContext().getColor(R.color.success))
            Handler(Looper.getMainLooper()).postDelayed({
                _b?.tvStatus?.text = getString(R.string.biometric_waiting)
                _b?.tvStatus?.setTextColor(requireContext().getColor(R.color.text_secondary))
            }, 3000)
        }

        vm.totalCatedraticos.observe(viewLifecycleOwner) { total ->
            val reg = vm.registros.value?.size ?: 0
            b.tvProgress.text = "$reg / $total catedraticos registrados"
        }

        vm.error.observe(viewLifecycleOwner) { it?.let { toast(it) } }

        vm.loadTotalCatedraticos()
        vm.startPolling()
        pulseHandler.post(pulseRunnable)
    }

    private fun showManualDialog() {
        val dv = DialogManualRegisterBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle("Registro manual de catedratico")
            .setView(dv.root)
            .setPositiveButton("Registrar") { dialog, _ ->
                val codigo = dv.etCodigo.text.toString().trim()
                val obs    = dv.etObservacion.text.toString().ifBlank { null }
                if (codigo.isBlank()) { toast("Ingresa el codigo del catedratico"); return@setPositiveButton }
                // En produccion: buscar el catedratico por codigo primero
                toast("Buscando catedratico $codigo...")
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pulseHandler.removeCallbacks(pulseRunnable)
        vm.stopPolling()
        _b = null
    }
}
