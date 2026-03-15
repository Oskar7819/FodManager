package com.example.fodmanager.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.ui.fragments.AeronaveFragment
import com.example.fodmanager.ui.fragments.HomeFragment
import com.example.fodmanager.ui.fragments.IncidenciasFragment
import com.example.fodmanager.ui.fragments.InspeccionesFragment
import com.example.fodmanager.ui.fragments.UsuariosFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Clase auxiliar para deserializar únicamente el rol del usuario desde Supabase
// Se usa para verificar si debe mostrarse la pestaña de Usuarios en el Bottom Navigation (FAB)
@Serializable
data class UsuarioRolHome(val rol: String)

// Activity principal de la aplicación despues del login.
// Contiene el Bottom Navigation con 5 pestañas y gestiona la navegación entre fragments.
// La pestaña Usuarios solo es visible para los roles: administrador, mando_gp4 y focal_point_fod
class HomeActivity : AppCompatActivity() {

    // Roles que pueden ver y gestionar la sección de usuarios
    private val rolesConUsuarios = listOf("administrador", "mando_gp4", "focal_point_fod")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Carga el HomeFragment (Dashboard) como pantalla inicial al entrar a la app
        loadFragment(HomeFragment())

        // Listener que detecta qué pestaña del Bottom Navigation ha pulsado el usuario
        // y carga el fragment correspondiente en el contenedor
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_inspecciones -> loadFragment(InspeccionesFragment())
                R.id.nav_incidencias -> loadFragment(IncidenciasFragment())
                R.id.nav_aeronaves -> loadFragment(AeronaveFragment())
                R.id.nav_usuarios -> loadFragment(UsuariosFragment())
            }
            true
        }

        // Verifica el rol del usuario para mostrar u ocultar la pestaña de Usuarios
        verificarRol(bottomNav)
    }

    // Consulta el rol del usuario logueado en Supabase y muestra la pestaña
    // de Usuarios en el Bottom Navigation solo si tiene permiso para verla.
    // La pestaña está oculta por defecto en el XML (android:visible="false")
    private fun verificarRol(bottomNav: BottomNavigationView) {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email
                val usuario = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<UsuarioRolHome>()

                // Hace visible la pestaña de Usuarios si el rol tiene permiso
                if (usuario.rol in rolesConUsuarios) {
                    bottomNav.menu.findItem(R.id.nav_usuarios).isVisible = true
                }
            } catch (e: Exception) {
                // Si falla la consulta, la pestaña permanece oculta por seguridad
            }
        }
    }

    // Reemplaza el fragment actual en el contenedor fragmentContainer
    // con el nuevo fragment seleccionado en el Bottom Navigation.
    // beginTransaction/commit es la forma estándar de gestionar fragments en Android
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}