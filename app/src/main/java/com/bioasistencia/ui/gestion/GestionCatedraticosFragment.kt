package com.bioasistencia.ui.gestion

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bioasistencia.R
import com.bioasistencia.data.models.Catedratico
import com.bioasistencia.data.models.Departamento
import com.bioasistencia.data.repository.AppRepository
import com.bioasistencia.data.repository.Result
import com.bioasistencia.databinding.FragmentGestionCatedraticosBinding
import com.bioasistencia.databinding.ItemGestionCatedraticoBinding
import com.bioasistencia.ui.RepoViewModelFactory
import com.bioasistencia.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GestionViewModel(private val repo: AppRepository) : ViewModel() {

    val catedraticos  = androidx.lifecycle.MutableLiveData<List<Catedratico>>(emptyList())
    val departamentos = androidx.lifecycle.MutableLiveData<List<Departamento>>(emptyList())
    val isLoading     = androidx.lifecycle.MutableLiveData(false)
    val mensaje       = androidx.lifecycle.MutableLiveData<String?>()
    val huellaEstado  = androidx.lifecycle.MutableLiveData<String?>()

    fun cargar() {
        isLoading.value = true
        viewModelScope.launch {
            when (val r = repo.getCatedraticos()) {
                is Result.Success -> { catedraticos.postValue(r.data); isLoading.postValue(false) }
                is Result.Error   -> { mensaje.postValue(r.message);   isLoading.postValue(false) }
                else -> {}
            }
        }
        viewModelScope.launch {
            when (val r = repo.getDepartamentos()) {
                is Result.Success -> departamentos.postValue(r.data)
                else -> {}
            }
        }
    }

    fun crear(nombre: String, apellido: String, correo: String, idDept: Int,
              tipo: String, horario: String, turno: String, cursos: Int) {
        isLoading.value = true
        viewModelScope.launch {
            val cat = Catedratico(
                id = 0, codigo = "", nombre = nombre, apellido = apellido,
                correo = correo.ifBlank { null }, idDepartamento = idDept,
                tipoContrato = tipo, horario = horario.ifBlank { null },
                turno = turno, cursosAsignados = cursos
            )
            when (val r = repo.crearCatedratico(cat)) {
                is Result.Success -> { mensaje.postValue("Catedratico registrado"); cargar() }
                is Result.Error   -> { mensaje.postValue(r.message); isLoading.postValue(false) }
                else -> {}
            }
        }
    }

    fun editar(id: Int, nombre: String, apellido: String, correo: String, idDept: Int,
               tipo: String, horario: String, turno: String, cursos: Int) {
        isLoading.value = true
        viewModelScope.launch {
            val cat = Catedratico(
                id = id, codigo = "", nombre = nombre, apellido = apellido,
                correo = correo.ifBlank { null }, idDepartamento = idDept,
                tipoContrato = tipo, horario = horario.ifBlank { null },
                turno = turno, cursosAsignados = cursos
            )
            when (val r = repo.actualizarCatedratico(id, cat)) {
                is Result.Success -> { mensaje.postValue("Catedratico actualizado"); cargar() }
                is Result.Error   -> { mensaje.postValue(r.message); isLoading.postValue(false) }
                else -> {}
            }
        }
    }

    fun eliminar(id: Int) {
        isLoading.value = true
        viewModelScope.launch {
            when (val r = repo.eliminarCatedratico(id)) {
                is Result.Success -> { mensaje.postValue("Catedratico eliminado"); cargar() }
                is Result.Error   -> { mensaje.postValue(r.message); isLoading.postValue(false) }
                else -> {}
            }
        }
    }

    fun solicitarHuella(idCatedratico: Int) {
        huellaEstado.value = "enviando"
        viewModelScope.launch {
            when (val r = repo.solicitarRegistroHuella(idCatedratico)) {
                is Result.Success -> {
                    huellaEstado.postValue("esperando")
                    esperarConfirmacion(idCatedratico)
                }
                is Result.Error -> huellaEstado.postValue("error:${r.message}")
                else -> {}
            }
        }
    }

    private suspend fun esperarConfirmacion(idCatedratico: Int) {
        repeat(20) {
            delay(3000)
            when (val r = repo.getCatedratico(idCatedratico)) {
                is Result.Success -> {
                    if (r.data.huellaRegistrada) {
                        huellaEstado.postValue("completado")
                        cargar()
                        return
                    }
                }
                else -> {}
            }
        }
        huellaEstado.postValue("timeout")
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class GestionAdapter(
    private val onEditar: (Catedratico) -> Unit,
    private val onEliminar: (Catedratico) -> Unit,
    private val onHuella: (Catedratico) -> Unit
) : RecyclerView.Adapter<GestionAdapter.VH>() {

    private var items = listOf<Catedratico>()
    fun submit(list: List<Catedratico>) { items = list; notifyDataSetChanged() }

    inner class VH(val b: ItemGestionCatedraticoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        ItemGestionCatedraticoBinding.inflate(LayoutInflater.from(p.context), p, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val cat = items[pos]
        with(h.b) {
            tvInitials.text = cat.iniciales
            tvNombre.text   = cat.nombreCompleto
            tvInfo.text     = "${cat.codigo}  |  ${cat.departamento ?: ""}  |  ${cat.tipoContratoDisplay}"

            if (cat.huellaRegistrada) {
                tvHuellaStatus.text = "Huella registrada"
                tvHuellaStatus.setTextColor(root.context.getColor(R.color.success))
                btnHuella.text = "Actualizar huella"
            } else {
                tvHuellaStatus.text = "Sin huella"
                tvHuellaStatus.setTextColor(root.context.getColor(R.color.danger))
                btnHuella.text = "Registrar huella"
            }

            btnHuella.setOnClickListener   { onHuella(cat) }
            btnEditar.setOnClickListener   { onEditar(cat) }
            btnEliminar.setOnClickListener { onEliminar(cat) }
        }
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────

class GestionCatedraticosFragment : Fragment() {

    private var _b: FragmentGestionCatedraticosBinding? = null
    private val b get() = _b!!
    private val vm: GestionViewModel by viewModels { RepoViewModelFactory.build(requireContext()) }
    private var dialogoHuella: AlertDialog? = null

    private val adapter = GestionAdapter(
        onEditar   = { mostrarDialogo(it) },
        onEliminar = { confirmarEliminar(it) },
        onHuella   = { mostrarDialogoHuella(it) }
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGestionCatedraticosBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        b.rvCatedraticos.layoutManager = LinearLayoutManager(requireContext())
        b.rvCatedraticos.adapter = adapter

        b.fabAgregar.setOnClickListener { mostrarDialogo(null) }
        b.swipeRefresh.setOnRefreshListener { vm.cargar() }

        b.etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val txt = s.toString().lowercase()
                val lista = vm.catedraticos.value ?: emptyList()
                adapter.submit(lista.filter {
                    it.nombreCompleto.lowercase().contains(txt) ||
                    it.codigo.lowercase().contains(txt) ||
                    (it.departamento?.lowercase()?.contains(txt) == true)
                })
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, a: Int) {}
        })

        vm.isLoading.observe(viewLifecycleOwner) { loading ->
            b.swipeRefresh.isRefreshing = loading
            if (loading) b.progressBar.show() else b.progressBar.hide()
        }

        vm.catedraticos.observe(viewLifecycleOwner) { lista ->
            adapter.submit(lista)
            b.tvConteo.text = "${lista.size} catedraticos"
            if (lista.isEmpty()) { b.tvEmpty.show(); b.rvCatedraticos.hide() }
            else                 { b.tvEmpty.hide(); b.rvCatedraticos.show() }
        }

        vm.mensaje.observe(viewLifecycleOwner) { it?.let { toast(it) } }

        vm.huellaEstado.observe(viewLifecycleOwner) { estado ->
            when {
                estado == "enviando"   -> actualizarDialogoHuella("Enviando solicitud al sensor...")
                estado == "esperando"  -> actualizarDialogoHuella("Coloca el dedo en el sensor\ncuando el LCD lo indique.\n\nEsperando confirmacion...")
                estado == "completado" -> { dialogoHuella?.dismiss(); toast("Huella registrada correctamente") }
                estado == "timeout"    -> { dialogoHuella?.dismiss(); toast("Tiempo agotado. Verifica que el sensor este encendido.") }
                estado?.startsWith("error") == true -> { dialogoHuella?.dismiss(); toast(estado.removePrefix("error:")) }
            }
        }

        vm.cargar()
    }

    // ── Dialogo crear / editar ─────────────────────────────────────────────────

    private fun mostrarDialogo(catEditar: Catedratico?) {
        val depts = vm.departamentos.value ?: emptyList()
        if (depts.isEmpty()) { toast("Cargando departamentos, intenta de nuevo"); return }

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_crear_catedratico, null)

        val etNombre    = view.findViewById<EditText>(R.id.etNombre)
        val etApellido  = view.findViewById<EditText>(R.id.etApellido)
        val etCorreo    = view.findViewById<EditText>(R.id.etCorreo)
        val etHorario   = view.findViewById<EditText>(R.id.etHorario)
        val etCursos    = view.findViewById<EditText>(R.id.etCursos)
        val spDept      = view.findViewById<Spinner>(R.id.spinnerDepartamento)
        val spTipo      = view.findViewById<Spinner>(R.id.spinnerTipo)
        val spTurno     = view.findViewById<Spinner>(R.id.spinnerTurno)

        val tipos        = listOf("tiempo_completo", "medio_tiempo", "por_hora")
        val tiposDisplay = listOf("Tiempo completo", "Medio tiempo", "Por hora")
        val turnos       = listOf("matutino", "vespertino", "nocturno")
        val turnosDisplay= listOf("Matutino", "Vespertino", "Nocturno")

        spDept.adapter  = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, depts.map { it.nombre })
        spTipo.adapter  = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tiposDisplay)
        spTurno.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, turnosDisplay)

        // Si es edicion, prellenar los campos
        catEditar?.let { cat ->
            etNombre.setText(cat.nombre)
            etApellido.setText(cat.apellido)
            etCorreo.setText(cat.correo ?: "")
            etHorario.setText(cat.horario ?: "")
            etCursos.setText(cat.cursosAsignados.toString())
            val deptIdx = depts.indexOfFirst { it.id == cat.idDepartamento }
            if (deptIdx >= 0) spDept.setSelection(deptIdx)
            spTipo.setSelection(tipos.indexOf(cat.tipoContrato).coerceAtLeast(0))
            spTurno.setSelection(turnos.indexOf(cat.turno).coerceAtLeast(0))
        }

        val titulo = if (catEditar == null) "Nuevo catedratico" else "Editar: ${catEditar.nombreCompleto}"

        AlertDialog.Builder(requireContext())
            .setTitle(titulo)
            .setView(view)
            .setPositiveButton(if (catEditar == null) "Registrar" else "Guardar cambios") { _, _ ->
                val nombre   = etNombre.text.toString().trim()
                val apellido = etApellido.text.toString().trim()
                if (nombre.isBlank() || apellido.isBlank()) {
                    toast("Nombre y apellido son requeridos"); return@setPositiveButton
                }
                val idDept = depts[spDept.selectedItemPosition].id
                val tipo   = tipos[spTipo.selectedItemPosition]
                val turno  = turnos[spTurno.selectedItemPosition]
                val cursos = etCursos.text.toString().toIntOrNull() ?: 0

                if (catEditar == null) {
                    vm.crear(nombre, apellido, etCorreo.text.toString().trim(), idDept, tipo, etHorario.text.toString().trim(), turno, cursos)
                } else {
                    vm.editar(catEditar.id, nombre, apellido, etCorreo.text.toString().trim(), idDept, tipo, etHorario.text.toString().trim(), turno, cursos)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Confirmar eliminar ─────────────────────────────────────────────────────

    private fun confirmarEliminar(cat: Catedratico) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar catedratico")
            .setMessage("Estas seguro de eliminar a ${cat.nombreCompleto}?\n\nEsta accion no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> vm.eliminar(cat.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Dialogo registrar huella ───────────────────────────────────────────────

    private fun mostrarDialogoHuella(cat: Catedratico) {
        val msg = "Catedratico: ${cat.nombreCompleto}\n\n" +
                  if (cat.huellaRegistrada) "Ya tiene huella registrada.\nPresiona Iniciar para registrar una nueva."
                  else "Presiona Iniciar y coloca el dedo en el sensor cuando el LCD lo indique."

        dialogoHuella = AlertDialog.Builder(requireContext())
            .setTitle("Registro de huella biometrica")
            .setMessage(msg)
            .setPositiveButton("Iniciar") { _, _ -> vm.solicitarHuella(cat.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun actualizarDialogoHuella(msg: String) {
        dialogoHuella?.setMessage(msg)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
