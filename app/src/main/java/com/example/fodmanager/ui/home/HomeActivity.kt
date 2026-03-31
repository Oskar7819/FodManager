package com.example.fodmanager.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.repository.UsuarioRepository
import com.example.fodmanager.ui.fragments.AeronaveFragment
import com.example.fodmanager.ui.fragments.HomeFragment
import com.example.fodmanager.ui.fragments.IncidenciasFragment
import com.example.fodmanager.ui.fragments.InspeccionesFragment
import com.example.fodmanager.ui.fragments.UsuariosFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

// Activity principal que contiene la navegación inferior y los fragmentos de la app
class HomeActivity : AppCompatActivity() {

    // Roles que pueden ver la pestaña de usuarios
    private val rolesConUsuarios = listOf("administrador", "mando_gp4", "focal_point_fod")

    // Método que se ejecuta al crear la activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Establece el layout principal de la activity
        setContentView(R.layout.activity_home)

        // Obtiene la referencia al menú de navegación inferior
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Fragment por defecto
        loadFragment(HomeFragment())

        // Gestiona la navegación entre fragmentos al pulsar cada opción del menú
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // Carga el fragmento de inicio
                R.id.nav_home -> loadFragment(HomeFragment())

                // Carga el fragmento de inspecciones
                R.id.nav_inspecciones -> loadFragment(InspeccionesFragment())

                // Carga el fragmento de incidencias
                R.id.nav_incidencias -> loadFragment(IncidenciasFragment())

                // Carga el fragmento de aeronaves
                R.id.nav_aeronaves -> loadFragment(AeronaveFragment())

                // Carga el fragmento de usuarios
                R.id.nav_usuarios -> loadFragment(UsuariosFragment())
            }
            true
        }

        // Comprueba si el usuario tiene permiso para ver la pestaña de usuarios
        verificarRol(bottomNav)
    }

    // Función que verifica el rol del usuario para mostrar u ocultar la pestaña de usuarios
    private fun verificarRol(bottomNav: BottomNavigationView) {
        lifecycleScope.launch {
            try {
                // Obtiene el usuario actual desde el repositorio
                val usuario = UsuarioRepository.getUsuarioActual()

                // Si el rol del usuario tiene permisos, muestra la pestaña de usuarios
                if (usuario.rol in rolesConUsuarios) {
                    bottomNav.menu.findItem(R.id.nav_usuarios).isVisible = true
                }
            } catch (_: Exception) {
                // Por seguridad, si falla la carga del usuario,
                // no mostramos la pestaña de usuarios
            }
        }
    }

    // Función que reemplaza el fragmento actual por otro nuevo
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}