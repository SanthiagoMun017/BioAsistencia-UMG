package com.bioasistencia.ui.menu

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bioasistencia.R
import com.bioasistencia.databinding.FragmentMenuBinding
import com.bioasistencia.ui.login.LoginActivity
import com.bioasistencia.utils.SessionManager
import com.bioasistencia.utils.toast

class MenuFragment : Fragment() {

    private var _b: FragmentMenuBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMenuBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        val usuario = SessionManager(requireContext()).getUsuario()
        b.tvNombreUsuario.text = usuario?.nombreCompleto ?: "Usuario"
        b.tvRol.text           = usuario?.rol?.replaceFirstChar { it.uppercase() } ?: ""

        // Iniciales en el avatar
        val initials = usuario?.iniciales ?: "?"
        b.tvInitialsUsuario.text = initials

        b.tvGestionCatedraticos.setOnClickListener {
            findNavController().navigate(R.id.gestionCatedraticosFragment)
        }

        b.tvAcerca.setOnClickListener {
            toast("BioAsistencia v1.0  -  VII Ciclo Ing. Sistemas")
        }

        b.tvLogout.setOnClickListener {
            SessionManager(requireContext()).clearSession()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
