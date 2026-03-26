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

/**
 * Proyección mínima para verificar el rol del usuario sin deserializar el objeto completo.
 * Se usa solo en HomeActivity para decidir si mostrar la pestaña de Usuarios.
 */
@Serializable
data class UsuarioRolHome(val rol: String)

/**
 * Activity principal de la aplicación tras el login.
 *
 * Contiene el Bottom Navigation con cinco pestañas y gestiona la
 * navegación entre fragments mediante reemplazos en el contenedor `fragmentContainer`.
 *
 * Pestañas disponibles:
 * - Home (Dashboard) → HomeFragment
 * - Inspecciones     → InspeccionesFragment
 * - Incidencias      → IncidenciasFragment
 * - Aeronaves        → AeronaveFragment
 * - Usuarios         → UsuariosFragment *(visible solo para rolesConUsuarios)*
 *
 * La pestaña "Usuarios" está oculta por defecto en el XML (`android:visible="false"`)
 * y se hace visible dinámicamente si el rol del usuario tiene permiso.
 * Si la consulta de rol falla, la pestaña permanece oculta por seguridad.
 */
class HomeActivity : AppCompatActivity() {

    /** Roles que pueden ver y gestionar la sección de usuarios. */
    private val rolesConUsuarios = listOf("administrador", "mando_gp4", "focal_point_fod")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Carga el Dashboard como pantalla de inicio al entrar en la app
        loadFragment(HomeFragment())

        // Gestiona la selección de pestañas del Bottom Navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home        -> loadFragment(HomeFragment())
                R.id.nav_inspecciones -> loadFragment(InspeccionesFragment())
                R.id.nav_incidencias  -> loadFragment(IncidenciasFragment())
                R.id.nav_aeronaves   -> loadFragment(AeronaveFragment())
                R.id.nav_usuarios    -> loadFragment(UsuariosFragment())
            }
            true
        }

        verificarRol(bottomNav)
    }

    /**
     * Consulta el rol del usuario logueado y hace visible la pestaña Usuarios
     * si el rol pertenece a rolesConUsuarios.
     * Cualquier error en la consulta mantiene la pestaña oculta.
     */
    private fun verificarRol(bottomNav: BottomNavigationView) {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email
                val usuario = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<UsuarioRolHome>()

                if (usuario.rol in rolesConUsuarios) {
                    bottomNav.menu.findItem(R.id.nav_usuarios).isVisible = true
                }
            } catch (e: Exception) {
                // Fallo silencioso: la pestaña permanece oculta por seguridad
            }
        }
    }

    /**
     * Reemplaza el fragment activo en fragmentContainer con fragment.
     * Usar `replace` en lugar de `add` garantiza que solo haya un fragment activo
     * en cada momento.
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}