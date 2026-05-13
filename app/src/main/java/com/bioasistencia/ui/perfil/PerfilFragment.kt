package com.bioasistencia.ui.perfil

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.bioasistencia.R
import com.bioasistencia.data.models.Catedratico
import com.bioasistencia.data.repository.AppRepository
import com.bioasistencia.data.repository.Result
import com.bioasistencia.databinding.FragmentPerfilBinding
import com.bioasistencia.ui.RepoViewModelFactory
import com.bioasistencia.utils.*
import kotlinx.coroutines.launch

class PerfilViewModel(private val repo: AppRepository) : ViewModel() {
    val catedratico = androidx.lifecycle.MutableLiveData<Catedratico?>()
    val isLoading   = androidx.lifecycle.MutableLiveData(false)
    val error       = androidx.lifecycle.MutableLiveData<String?>()

    fun load(id: Int) {
        isLoading.value = true
        viewModelScope.launch {
            when (val r = repo.getCatedratico(id)) {
                is Result.Success -> { catedratico.postValue(r.data); isLoading.postValue(false) }
                is Result.Error   -> { error.postValue(r.message);    isLoading.postValue(false) }
                else -> {}
            }
        }
    }
}

class PerfilFragment : Fragment() {

    private var _b: FragmentPerfilBinding? = null
    private val b get() = _b!!
    private val args: PerfilFragmentArgs by navArgs()
    private val vm: PerfilViewModel by viewModels { RepoViewModelFactory.build(requireContext()) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPerfilBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        vm.isLoading.observe(viewLifecycleOwner) { if (it) b.progressBar.show() else b.progressBar.hide() }
        vm.catedratico.observe(viewLifecycleOwner) { it?.let { render(it) } }
        vm.error.observe(viewLifecycleOwner) { it?.let { toast(it) } }
        vm.load(args.idCatedratico)
    }

    private fun render(cat: Catedratico) {
        b.tvInitials.text = cat.iniciales
        b.tvNombre.text   = cat.nombreCompleto
        b.tvInfo.text     = "Codigo: ${cat.codigo}  |  ${cat.departamento ?: ""}  |  ${cat.tipoContratoDisplay}"

        val pct       = cat.porcentajeAsistencia ?: 0f
        val asistidos = cat.diasAsistidos ?: 0
        val ausencias = cat.totalAusencias ?: 0

        b.tvPorcentaje.text    = "%.0f%%".format(pct)
        b.tvDiasAsistidos.text = asistidos.toString()
        b.tvAusencias.text     = ausencias.toString()

        val color = when {
            pct >= 80 -> requireContext().getColor(R.color.success)
            pct >= 60 -> requireContext().getColor(R.color.warning)
            else      -> requireContext().getColor(R.color.danger)
        }
        b.progressAsistencia.setIndicatorColor(color)
        b.progressAsistencia.progress = pct.toInt()

        b.tvDepartamento.text = cat.departamento ?: "Sin departamento"
        b.tvContrato.text     = cat.tipoContratoDisplay
        b.tvCursos.text       = "${cat.cursosAsignados} cursos asignados"
        b.tvHorario.text      = cat.horario ?: "Sin horario"
        b.tvTurno.text        = cat.turnoDisplay

        if (cat.huellaRegistrada) {
            b.tvHuellaStatus.text = "Huella registrada  |  ID: #${cat.idHuella ?: "--"}"
            b.tvHuellaStatus.setTextColor(requireContext().getColor(R.color.success))
            b.cardHuella.setCardBackgroundColor(requireContext().getColor(R.color.success_light))
        } else {
            b.tvHuellaStatus.text = "Sin huella registrada en el sensor"
            b.tvHuellaStatus.setTextColor(requireContext().getColor(R.color.danger))
            b.cardHuella.setCardBackgroundColor(requireContext().getColor(R.color.danger_light))
        }

        b.btnVerHistorial.setOnClickListener {
            toast("Cargando historial de ${cat.nombre}...")
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
