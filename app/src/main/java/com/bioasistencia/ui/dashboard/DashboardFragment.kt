package com.bioasistencia.ui.dashboard

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bioasistencia.data.models.Departamento
import com.bioasistencia.data.repository.AppRepository
import com.bioasistencia.data.repository.Result
import com.bioasistencia.databinding.FragmentDashboardBinding
import com.bioasistencia.databinding.ItemDepartamentoBinding
import com.bioasistencia.ui.RepoViewModelFactory
import com.bioasistencia.utils.*
import kotlinx.coroutines.launch
import java.util.Locale

// ── ViewModel ─────────────────────────────────────────────────────────────────

class DashboardViewModel(private val repo: AppRepository) : ViewModel() {

    val departamentos   = androidx.lifecycle.MutableLiveData<List<Departamento>>()
    val isLoading       = androidx.lifecycle.MutableLiveData(false)
    val error           = androidx.lifecycle.MutableLiveData<String?>()

    fun load() {
        isLoading.value = true
        viewModelScope.launch {
            when (val r = repo.getDashboard()) {
                is Result.Success -> { departamentos.postValue(r.data); isLoading.postValue(false) }
                is Result.Error   -> { error.postValue(r.message);      isLoading.postValue(false) }
                else -> {}
            }
        }
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class DepartamentosAdapter(
    private val onClick: (Departamento) -> Unit
) : RecyclerView.Adapter<DepartamentosAdapter.VH>() {

    private var items = listOf<Departamento>()

    fun submit(list: List<Departamento>) { items = list; notifyDataSetChanged() }

    inner class VH(val b: ItemDepartamentoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemDepartamentoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val d = items[pos]
        with(h.b) {
            tvNombreDept.text  = d.nombre
            tvStatsCats.text   = "${d.presentes}/${d.total} catedraticos presentes"
            val pct = d.porcentaje
            tvPorcentaje.text  = "%.0f%%".format(pct)
            progressDept.progress = pct.toInt()

            val ctx = root.context
            val (badgeBg, badgeFg, barColor) = when {
                pct >= 80 -> Triple(
                    ctx.getColor(com.bioasistencia.R.color.success_light),
                    ctx.getColor(com.bioasistencia.R.color.success),
                    ctx.getColor(com.bioasistencia.R.color.success)
                )
                pct >= 60 -> Triple(
                    ctx.getColor(com.bioasistencia.R.color.warning_light),
                    ctx.getColor(com.bioasistencia.R.color.warning),
                    ctx.getColor(com.bioasistencia.R.color.warning)
                )
                else -> Triple(
                    ctx.getColor(com.bioasistencia.R.color.danger_light),
                    ctx.getColor(com.bioasistencia.R.color.danger),
                    ctx.getColor(com.bioasistencia.R.color.danger)
                )
            }
            tvPorcentaje.setTextColor(badgeFg)
            progressDept.setIndicatorColor(barColor)

            root.setOnClickListener { onClick(d) }
        }
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────

class DashboardFragment : Fragment() {

    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val vm: DashboardViewModel by viewModels { RepoViewModelFactory.build(requireContext()) }
    private val adapter = DepartamentosAdapter { dept ->
        val action = DashboardFragmentDirections.actionDashboardToHistorial(
            idDepartamento = dept.id,
            nombreDepartamento = dept.nombre
        )
        findNavController().navigate(action)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDashboardBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        val usuario = com.bioasistencia.utils.SessionManager(requireContext()).getUsuario()
        b.tvGreeting.text = "${DateUtils.greeting()}, ${usuario?.nombre ?: "Coordinador"}"
        b.tvDate.text = DateUtils.currentDayFull()

        b.rvDepartamentos.layoutManager = LinearLayoutManager(requireContext())
        b.rvDepartamentos.adapter = adapter

        b.swipeRefresh.setOnRefreshListener { vm.load() }
        b.btnRegistrar.setOnClickListener {
            findNavController().navigate(
                DashboardFragmentDirections.actionDashboardToBiometric()
            )
        }

        vm.isLoading.observe(viewLifecycleOwner) { loading ->
            b.swipeRefresh.isRefreshing = loading
            if (loading) b.progressBar.show() else b.progressBar.hide()
        }

        vm.departamentos.observe(viewLifecycleOwner) { lista ->
            adapter.submit(lista)
            val presentes = lista.sumOf { it.presentes }
            val ausentes  = lista.sumOf { it.ausentes }
            val total     = lista.sumOf { it.total }
            b.tvTotalCats.text  = total.toString()
            b.tvPresentes.text  = presentes.toString()
            b.tvAusentes.text   = ausentes.toString()
            if (lista.isEmpty()) { b.tvEmpty.show(); b.rvDepartamentos.hide() }
            else                 { b.tvEmpty.hide(); b.rvDepartamentos.show() }
        }

        vm.error.observe(viewLifecycleOwner) { it?.let { toast(it) } }

        vm.load()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
