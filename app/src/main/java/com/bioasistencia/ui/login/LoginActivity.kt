package com.bioasistencia.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bioasistencia.data.repository.AppRepository
import com.bioasistencia.data.repository.Result
import com.bioasistencia.databinding.ActivityLoginBinding
import com.bioasistencia.ui.MainActivity
import com.bioasistencia.ui.RepoViewModelFactory
import com.bioasistencia.utils.SessionManager
import com.bioasistencia.utils.hide
import com.bioasistencia.utils.show
import com.bioasistencia.utils.toast
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

class LoginViewModel(private val repo: AppRepository) : ViewModel() {

    sealed class State {
        object Idle : State()
        object Loading : State()
        object Success : State()
        data class Error(val msg: String) : State()
    }

    val state = androidx.lifecycle.MutableLiveData<State>(State.Idle)
    private lateinit var session: SessionManager

    fun init(s: SessionManager) { session = s }

    fun login(correo: String, pass: String) {
        if (correo.isBlank() || pass.isBlank()) {
            state.value = State.Error("Completa todos los campos")
            return
        }
        state.value = State.Loading
        viewModelScope.launch {
            when (val r = repo.login(correo, pass)) {
                is Result.Success -> {
                    val user = r.data.usuario
                    if (user != null) {
                        session.saveSession(r.data.token, user)
                        state.postValue(State.Success)
                    } else {
                        state.postValue(State.Error("Error al obtener datos del usuario"))
                    }
                }
                is Result.Error -> state.postValue(State.Error(r.message))
                else -> {}
            }
        }
    }
}

// ── Activity ──────────────────────────────────────────────────────────────────

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val vm: LoginViewModel by viewModels {
        RepoViewModelFactory.build(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vm.init(SessionManager(this))

        binding.btnLogin.setOnClickListener {
            vm.login(
                binding.etEmail.text.toString().trim(),
                binding.etPassword.text.toString()
            )
        }

        binding.btnLoginCoord.setOnClickListener {
            binding.etEmail.setText("coordinador@bioasistencia.edu")
            binding.etPassword.setText("admin1234")
            vm.login("coordinador@bioasistencia.edu", "admin1234")
        }

        binding.tvForgotPassword.setOnClickListener {
            toast("Contacta al administrador del sistema")
        }

        vm.state.observe(this) { s ->
            when (s) {
                is LoginViewModel.State.Loading -> {
                    binding.progressBar.show()
                    binding.btnLogin.isEnabled = false
                    binding.btnLoginCoord.isEnabled = false
                }
                is LoginViewModel.State.Success -> {
                    binding.progressBar.hide()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is LoginViewModel.State.Error -> {
                    binding.progressBar.hide()
                    binding.btnLogin.isEnabled = true
                    binding.btnLoginCoord.isEnabled = true
                    toast(s.msg)
                }
                else -> {
                    binding.progressBar.hide()
                    binding.btnLogin.isEnabled = true
                    binding.btnLoginCoord.isEnabled = true
                }
            }
        }
    }
}
