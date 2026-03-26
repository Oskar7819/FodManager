package com.example.fodmanager.ui.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.ui.aeronaves.AeronaveAdapter
import com.example.fodmanager.ui.aeronaves.DetalleAeronaveActivity
import com.example.fodmanager.ui.aeronaves.NuevaAeronaveActivity
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.example.fodmanager.data.models.Usuario


/**
 * Proyección local para deserializar únicamente el rol de un usuario.
 * Se usa en este fragment para verificar permisos sin cargar el objeto Usuario completo.
 * TODO: considerar reemplazar por [com.example.fodmanager.data.models.UsuarioRol]
 *       para eliminar esta duplicidad.
     */

@Serializable
data class UsuarioRol(val rol: String)

/**
 * Fragment de Aeronaves del Bottom Navigation.
 *
 * Responsabilidades:
 * - Mostrar la lista de aeronaves filtrada según el rol del usuario logueado.
 * - Abrir DetalleAeronaveActivity al pulsar una tarjeta.
 * - Mostrar u ocultar el FAB para añadir aeronaves según el rol.
 * - Recargar la lista automáticamente al volver de cualquier activity hija.
 *
 * Lógica de visibilidad por rol:
 * - rolesGenerales (administrador, head_plant, focal_point_fod) ven todas las aeronaves.
 * - mando_gp4, quality, operario ven solo ven la aeronave a la que están adscritos.
 * - rolesConPermiso (administrador, mando_gp4, focal_point_fod) pueden crear aeronaves (FAB visible).
 */
class AeronaveFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AeronaveAdapter
    private lateinit var fab: FloatingActionButton
    private val aeronaves = mutableListOf<Aeronave>()

    /** Roles que pueden dar de alta nuevas aeronaves (FAB visible). */
    private val rolesConPermiso = listOf("administrador", "mando_gp4", "focal_point_fod")

    /** Roles que tienen visión global de todas las aeronaves del sistema. */
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    /**
     * Launcher para NuevaAeronaveActivity.
     * Si el resultado es Activity.RESULT_OK, recarga la lista para mostrar
     * la aeronave recién creada.
     */
    private val nuevaAeronaveLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            cargarDatos()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_aeronave, container, false)

        recyclerView = view.findViewById(R.id.recyclerAeronaves)
        fab = view.findViewById(R.id.fabNuevaAeronave)

        // Al pulsar una tarjeta se abren los detalles de esa aeronave,
        // pasando todos sus campos como extras del Intent
        adapter = AeronaveAdapter(aeronaves) { aeronave ->
            val intent = Intent(requireContext(), DetalleAeronaveActivity::class.java)
            intent.putExtra("aeronave_id", aeronave.id)
            intent.putExtra("aeronave_modelo", aeronave.modelo)
            intent.putExtra("aeronave_numero_serie", aeronave.numeroSerie)
            intent.putExtra("aeronave_ubicacion", aeronave.ubicacion)
            intent.putExtra("aeronave_activa", aeronave.activa)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // El FAB usa el launcher para detectar si se añadió una aeronave
        // y recargar la lista automáticamente al regresar
        fab.setOnClickListener {
            val intent = Intent(requireContext(), NuevaAeronaveActivity::class.java)
            nuevaAeronaveLauncher.launch(intent)
        }

        return view
    }

    /**
     * Se ejecuta cada vez que el fragment vuelve a ser visible (también al regresar
     * desde DetalleAeronaveActivity), garantizando que la lista esté siempre actualizada.
     */
    override fun onResume() {
        super.onResume()
        cargarDatos()
    }

    /**
     * Obtiene el rol y la aeronave asignada del usuario logueado, y carga
     * la lista de aeronaves correspondiente según los permisos:
     * - rolesGenerales, todas las aeronaves del sistema.
     * - Resto,  solo la aeronave a la que está adscrito el usuario.
     *
     * Si el usuario no tiene aeronave asignada (`aeronaveId == null`), se usa -1
     * como ID ficticio para que la consulta no devuelva resultados.
     */
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email

                val usuarioLogueado = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<Usuario>()

                fab.visibility = if (usuarioLogueado.rol in rolesConPermiso) View.VISIBLE else View.GONE

                val resultado = if (usuarioLogueado.rol in rolesGenerales) {
                    supabase.postgrest["aeronaves"]
                        .select()
                        .decodeList<Aeronave>()
                } else {
                    supabase.postgrest["aeronaves"]
                        .select { filter { eq("id", usuarioLogueado.aeronaveId ?: -1) } }
                        .decodeList<Aeronave>()
                }

                aeronaves.clear()
                aeronaves.addAll(resultado)
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}