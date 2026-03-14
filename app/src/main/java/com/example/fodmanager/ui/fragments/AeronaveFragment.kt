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

// Clase auxiliar para deserializar únicamente el rol del usuario desde Supabase
@Serializable
data class UsuarioRol(val rol: String)

// Fragment que muestra la lista de aeronaves en el tab "Aeronaves" del Bottom Navigation.
// La información mostrada y las acciones disponibles varían según el rol del usuario:
// - rolesGenerales (administrador, head_plant, focal_point_fod) → ven todas las aeronaves
// - mando_gp4, quality, operario → solo ven la aeronave a la que están adscritos
// - rolesConPermiso (administrador, mando_gp4, focal_point_fod) → pueden añadir aeronaves
class AeronaveFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AeronaveAdapter
    private lateinit var fab: FloatingActionButton
    private val aeronaves = mutableListOf<Aeronave>()

    // Roles que pueden dar de alta nuevas aeronaves (ven el botón FAB)
    private val rolesConPermiso = listOf("administrador", "mando_gp4", "focal_point_fod")

    // Roles que ven todas las aeronaves del sistema (visión general)
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    // Launcher para abrir NuevaAeronaveActivity y recargar la lista si se añadió una aeronave
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

        // Configura el adapter con la lista de aeronaves.
        // Al pulsar una tarjeta abre DetalleAeronaveActivity pasando los datos
        // de la aeronave seleccionada mediante el Intent
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

        // Al pulsar el FAB abre NuevaAeronaveActivity usando el launcher
        // para detectar si se añadió una aeronave y recargar la lista
        fab.setOnClickListener {
            val intent = Intent(requireContext(), NuevaAeronaveActivity::class.java)
            nuevaAeronaveLauncher.launch(intent)
        }

        return view
    }

    // onResume se ejecuta cada vez que el fragment vuelve a ser visible,
    // incluyendo cuando se vuelve de DetalleAeronaveActivity.
    // Esto garantiza que la lista se recargue y refleje los cambios
    // (por ejemplo, una aeronave marcada como inactiva)
    override fun onResume() {
        super.onResume()
        cargarDatos()
    }

    // Carga los datos desde Supabase según el rol del usuario logueado
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                // Obtiene el email de la sesión actual de Supabase Auth
                val email = supabase.auth.currentSessionOrNull()?.user?.email

                // Consulta el usuario logueado en la tabla "usuarios" para obtener su rol
                val usuarioLogueado = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<Usuario>()

                // Muestra u oculta el FAB según si el rol tiene permiso para añadir aeronaves
                if (usuarioLogueado.rol in rolesConPermiso) {
                    fab.visibility = View.VISIBLE
                } else {
                    fab.visibility = View.GONE
                }

                // Carga todas las aeronaves o solo la del usuario según su rol
                val resultado = if (usuarioLogueado.rol in rolesGenerales) {
                    // Roles generales ven todas las aeronaves
                    supabase.postgrest["aeronaves"]
                        .select()
                        .decodeList<Aeronave>()
                } else {
                    // El resto solo ve la aeronave a la que está adscrito
                    // Si no tiene aeronave asignada (aeronaveId null) usa -1
                    // para que la consulta no devuelva resultados
                    supabase.postgrest["aeronaves"]
                        .select { filter { eq("id", usuarioLogueado.aeronaveId ?: -1) } }
                        .decodeList<Aeronave>()
                }

                aeronaves.clear()
                aeronaves.addAll(resultado)
                // Notifica al adapter que los datos han cambiado para actualizar la UI
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}