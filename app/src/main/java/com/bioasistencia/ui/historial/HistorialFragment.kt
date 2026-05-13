package com.bioasistencia.ui.historial

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bioasistencia.R
import com.bioasistencia.data.models.*
import com.bioasistencia.data.repository.AppRepository
import com.bioasistencia.data.repository.Result
import com.bioasistencia.databinding.FragmentHistorialBinding
import com.bioasistencia.databinding.ItemAsistenciaBinding
import com.bioasistencia.ui.RepoViewModelFactory
import com.bioasistencia.utils.*
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

class HistorialViewModel(private val repo: AppRepository) : ViewModel() {

    val asistencias         = androidx.lifecycle.MutableLiveData<List<Asistencia>>(emptyList())
    val asistenciasFiltradas= androidx.lifecycle.MutableLiveData<List<Asistencia>>(emptyList())
    val isLoading           = androidx.lifecycle.MutableLiveData(false)
    val error               = androidx.lifecycle.MutableLiveData<String?>()

    private var filtroEstado: String? = null
    private var busqueda = ""

    fun load(idDepartamento: Int?, fecha: String? = null) {
        isLoading.value = true
        viewModelScope.launch {
            when (val r = repo.getAsistencias(
                fecha          = fecha ?: DateUtils.today(),
                idDepartamento = idDepartamento
            )) {
                is Result.Success -> {
                    asistencias.postValue(r.data)
                    filtrar(r.data)
                    isLoading.postValue(false)
                }
                is Result.Error -> { error.postValue(r.message); isLoading.postValue(false) }
                else -> {}
            }
        }
    }

    fun setEstado(estado: String?) { filtroEstado = estado; filtrar(asistencias.value ?: emptyList()) }
    fun setBusqueda(t: String)    { busqueda = t.lowercase(); filtrar(asistencias.value ?: emptyList()) }

    private fun filtrar(lista: List<Asistencia>) {
        var r = lista
        filtroEstado?.let { e -> r = r.filter { it.estado?.name == e } }
        if (busqueda.isNotBlank()) {
            r = r.filter { a ->
                val c = a.catedratico
                c != null && (
                    c.nombreCompleto.lowercase().contains(busqueda) ||
                    c.codigo.lowercase().contains(busqueda) ||
                    (c.departamento?.lowercase()?.contains(busqueda) == true)
                )
            }
        }
        asistenciasFiltradas.postValue(r)
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class AsistenciaAdapter(
    private val onClick: (Asistencia) -> Unit
) : RecyclerView.Adapter<AsistenciaAdapter.VH>() {

    private var items = listOf<Asistencia>()
    fun submit(list: List<Asistencia>) { items = list; notifyDataSetChanged() }

    inner class VH(val b: ItemAsistenciaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        ItemAsistenciaBinding.inflate(LayoutInflater.from(p.context), p, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val a   = items[pos]
        val cat = a.catedratico
        with(h.b) {
            tvInitials.text = cat?.iniciales ?: "?"
            tvNombre.text   = cat?.nombreCompleto ?: "Catedratico #${a.idCatedratico}"
            tvSub.text      = buildString {
                cat?.let { append(it.codigo).append("  |  ").append(it.departamento ?: "") }
                if (a.fechaHora.isNotBlank()) append("  |  ").append(DateUtils.formatTime(a.fechaHora))
            }

            val ctx = root.context
            when (a.estado) {
                EstadoAsistencia.presente -> {
                    tvEstado.text = "Presente"
                    tvEstado.setBackgroundResource(R.drawable.badge_bg_success)
                    tvEstado.setTextColor(ctx.getColor(R.color.success))
                }
                EstadoAsistencia.ausente -> {
                    tvEstado.text = "Ausente"
                    tvEstado.setBackgroundResource(R.drawable.badge_bg_danger)
                    tvEstado.setTextColor(ctx.getColor(R.color.danger))
                }
                EstadoAsistencia.tardanza -> {
                    tvEstado.text = "Tardanza"
                    tvEstado.setBackgroundResource(R.drawable.badge_bg_warning)
                    tvEstado.setTextColor(ctx.getColor(R.color.warning))
                }
                else -> {
                    tvEstado.text = ""
                    tvEstado.background = null
                }
            }
            root.setOnClickListener { onClick(a) }
        }
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────

class HistorialFragment : Fragment() {

    private var _b: FragmentHistorialBinding? = null
    private val b get() = _b!!
    private val args: HistorialFragmentArgs by navArgs()
    private val vm: HistorialViewModel by viewModels { RepoViewModelFactory.build(requireContext()) }
    private val adapter = AsistenciaAdapter { a ->
        a.catedratico?.let {
            findNavController().navigate(
                HistorialFragmentDirections.actionHistorialToPerfil(it.id)
            )
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHistorialBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        b.tvDeptHeader.text  = args.nombreDepartamento
        b.tvFechaHeader.text = DateUtils.currentDayFull()

        b.rvAsistencias.layoutManager = LinearLayoutManager(requireContext())
        b.rvAsistencias.adapter = adapter

        setupFiltros()
        b.etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = vm.setBusqueda(s.toString())
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, a: Int) {}
        })

        vm.isLoading.observe(viewLifecycleOwner) { if (it) b.progressBar.show() else b.progressBar.hide() }

        vm.asistenciasFiltradas.observe(viewLifecycleOwner) { lista ->
            adapter.submit(lista)
            if (lista.isEmpty()) { b.tvEmpty.show(); b.rvAsistencias.hide() }
            else                 { b.tvEmpty.hide(); b.rvAsistencias.show() }
            val all = vm.asistencias.value ?: emptyList()
            b.tvPresentes.text = all.count { it.estado == EstadoAsistencia.presente }.toString()
            b.tvAusentes.text  = all.count { it.estado == EstadoAsistencia.ausente  }.toString()
            b.tvTardanza.text  = all.count { it.estado == EstadoAsistencia.tardanza }.toString()
        }

        vm.error.observe(viewLifecycleOwner) { it?.let { toast(it) } }

        val idDept = if (args.idDepartamento == 0) null else args.idDepartamento
        vm.load(idDept)
    }

    private fun setupFiltros() {
        val pairs = listOf(
            b.btnFiltroTodos     to null,
            b.btnFiltroPresentes to "presente",
            b.btnFiltroAusentes  to "ausente",
            b.btnFiltroTardanza  to "tardanza"
        )
        pairs.forEach { (btn, estado) ->
            btn.setOnClickListener {
                pairs.forEach { (b2, _) -> b2.isSelected = false }
                btn.isSelected = true
                vm.setEstado(estado)
            }
        }
        b.btnFiltroTodos.isSelected = true
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
