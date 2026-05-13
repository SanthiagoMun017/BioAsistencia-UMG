package com.bioasistencia.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bioasistencia.R
import com.bioasistencia.databinding.ActivityMainBinding
import com.bioasistencia.ui.login.LoginActivity
import com.bioasistencia.utils.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val session by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.toolbar.title = when (dest.id) {
                R.id.dashboardFragment  -> "BioAsistencia"
                R.id.historialFragment  -> "Historial de Catedraticos"
                R.id.biometricFragment  -> "Registro de Asistencia"
                R.id.reportesFragment   -> "Reportes"
                R.id.menuFragment       -> "Menu"
                R.id.perfilFragment     -> "Perfil del Catedratico"
                R.id.gestionCatedraticosFragment -> "Gestión de Catedráticos"
                else -> "BioAsistencia"
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            session.clearSession()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
