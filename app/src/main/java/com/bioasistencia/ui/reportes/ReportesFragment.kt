package com.bioasistencia.ui.reportes

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bioasistencia.data.models.*
import com.bioasistencia.data.repository.AppRepository
import com.bioasistencia.data.repository.Result
import com.bioasistencia.databinding.FragmentReportesBinding
import com.bioasistencia.ui.RepoViewModelFactory
import com.bioasistencia.utils.*
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ReportesViewModel(private val repo: AppRepository) : ViewModel() {

    val departamentos = androidx.lifecycle.MutableLiveData<List<Departamento>>(emptyList())
    val reporte       = androidx.lifecycle.MutableLiveData<ReporteResumen?>()
    val isLoading     = androidx.lifecycle.MutableLiveData(false)
    val error         = androidx.lifecycle.MutableLiveData<String?>()

    fun loadDepartamentos() {
        viewModelScope.launch {
            when (val r = repo.getDepartamentos()) {
                is Result.Success -> departamentos.postValue(r.data)
                is Result.Error   -> error.postValue(r.message)
                else -> {}
            }
        }
    }

    fun generar(idDepartamento: Int?, periodo: String, tipo: String) {
        isLoading.value = true
        viewModelScope.launch {
            val req = ReporteRequest(idDepartamento, periodo, tipo)
            when (val r = repo.generarReporte(req)) {
                is Result.Success -> { reporte.postValue(r.data);   isLoading.postValue(false) }
                is Result.Error   -> { error.postValue(r.message);  isLoading.postValue(false) }
                else -> {}
            }
        }
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────

class ReportesFragment : Fragment() {

    private var _b: FragmentReportesBinding? = null
    private val b get() = _b!!
    private val vm: ReportesViewModel by viewModels { RepoViewModelFactory.build(requireContext()) }
    private var depts = listOf<Departamento>()
    private var deptSeleccionado: Departamento? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentReportesBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        setupChart()

        b.btnGenerar.setOnClickListener {
            vm.generar(
                idDepartamento = deptSeleccionado?.id,
                periodo        = DateUtils.currentMonth(),
                tipo           = "por_catedratico"
            )
        }
        b.btnExportPdf.setOnClickListener   { toast("Generando PDF...") }
        b.btnExportExcel.setOnClickListener { toast("Generando Excel...") }

        vm.departamentos.observe(viewLifecycleOwner) { lista ->
            depts = lista
            val nombres = mutableListOf("Todos los departamentos") + lista.map { it.nombre }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, nombres)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spinnerDept.adapter = adapter
            b.spinnerDept.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    deptSeleccionado = if (pos == 0) null else lista.getOrNull(pos - 1)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        vm.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) { b.progressBar.show(); b.btnGenerar.isEnabled = false }
            else         { b.progressBar.hide(); b.btnGenerar.isEnabled = true  }
        }

        vm.reporte.observe(viewLifecycleOwner) { r ->
            r ?: return@observe
            b.reporteContent.show()
            b.tvPromedioGlobal.text = "%.0f%%".format(r.promedioAsistencia)
            b.tvTotalDias.text      = "${r.totalDias} dias"
            renderChart(r)
            renderTopAusencias(r)
        }

        vm.error.observe(viewLifecycleOwner) { it?.let { toast(it) } }

        vm.loadDepartamentos()
    }

    private fun setupChart() {
        b.barChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setScaleEnabled(false)
            legend.isEnabled = false
            setPinchZoom(false)
            setNoDataText("Genera un reporte para ver la grafica")
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 11f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 100f
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}%"
                }
            }
            axisRight.isEnabled = false
        }
    }

    private fun renderChart(r: ReporteResumen) {
        val labels  = r.datosSemana.map { it.dia }
        val entries = r.datosSemana.mapIndexed { i, d -> BarEntry(i.toFloat(), d.porcentaje) }
        val ds = BarDataSet(entries, "Asistencia %").apply {
            color = requireContext().getColor(com.bioasistencia.R.color.primary)
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float) = "${v.toInt()}%"
            }
        }
        b.barChart.apply {
            data = BarData(ds).apply { barWidth = 0.5f }
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size
            animateY(600)
            invalidate()
        }
    }

    private fun renderTopAusencias(r: ReporteResumen) {
        b.llTopAusencias.removeAllViews()
        r.topAusencias.take(5).forEach { cat ->
            val row = LayoutInflater.from(requireContext())
                .inflate(com.bioasistencia.R.layout.item_top_ausencia, b.llTopAusencias, false)
            row.findViewById<android.widget.TextView>(com.bioasistencia.R.id.tvNombreAusencia).text =
                cat.nombreCompleto
            row.findViewById<android.widget.TextView>(com.bioasistencia.R.id.tvDeptAusencia).text =
                cat.departamento
            row.findViewById<android.widget.TextView>(com.bioasistencia.R.id.tvCantAusencias).text =
                "${cat.ausencias} ausencias"
            b.llTopAusencias.addView(row)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
