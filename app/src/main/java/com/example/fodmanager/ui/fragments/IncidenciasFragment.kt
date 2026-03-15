package com.example.fodmanager.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.ui.incidencias.DetalleIncidenciaActivity
import com.example.fodmanager.ui.incidencias.IncidenciasAdapter
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.IncidenciaFod
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

// Fragment que muestra la lista de incidencias FOD en el tab "FOD" del Bottom Navigation.
// Las incidencias mostradas se filtran según el rol del usuario:
// - rolesGenerales (administrador, head_plant, focal_point_fod)  ven todas las incidencias
// - Resto de roles (mando_gp4, quality, operario) solo ven las de su aeronave asignada
class IncidenciasFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: IncidenciasAdapter
    private val incidencias = mutableListOf<IncidenciaFod>()

    // Mapa que relaciona el ID de cada aeronave con su nombre (modelo - número de serie)
    // Se usa para mostrar el nombre de la aeronave en cada tarjeta de incidencia
    private val aeronavesMap = mutableMapOf<Int, String>()

    // Roles que tienen visión general de todas las incidencias sin filtro por aeronave
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_incidencias, container, false)

        recyclerView = view.findViewById(R.id.recyclerIncidencias)

        // Configura el adapter con la lista de incidencias y el mapa de aeronaves.
        // Al pulsar una tarjeta abre DetalleIncidenciaActivity pasando el ID de la incidencia
        adapter = IncidenciasAdapter(incidencias, aeronavesMap) { incidencia ->
            val intent = Intent(requireContext(), DetalleIncidenciaActivity::class.java)
            intent.putExtra("incidencia_id", incidencia.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        cargarDatos()
        return view
    }

    // Carga las incidencias y aeronaves desde Supabase según el rol del usuario logueado
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                // Obtiene el email de la sesión actual de Supabase Auth
                val email = supabase.auth.currentSessionOrNull()?.user?.email

                // Consulta el usuario logueado para obtener su rol y aeronave asignada
                val usuarioLogueado = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<Usuario>()

                // Carga todas las aeronaves para construir el mapa ID → nombre
                // necesario para mostrar el nombre de la aeronave en cada tarjeta
                val aeronaves = supabase.postgrest["aeronaves"]
                    .select()
                    .decodeList<Aeronave>()

                aeronavesMap.clear()
                aeronaves.forEach { aeronavesMap[it.id] = "${it.modelo} - ${it.numeroSerie}" }

                // Carga todas las incidencias o solo las de la aeronave del usuario según su rol
                val resultado = if (usuarioLogueado.rol in rolesGenerales) {
                    // Roles generales ven todas las incidencias del sistema
                    supabase.postgrest["incidencias_fod"]
                        .select()
                        .decodeList<IncidenciaFod>()
                } else {
                    // El resto solo ve las incidencias de su aeronave asignada
                    // Si aeronaveId es null usa -1 para que no devuelva resultados
                    supabase.postgrest["incidencias_fod"]
                        .select { filter { eq("aeronave_id", usuarioLogueado.aeronaveId ?: -1) } }
                        .decodeList<IncidenciaFod>()
                }

                incidencias.clear()
                incidencias.addAll(resultado)
                // Notifica al adapter que los datos han cambiado para actualizar la UI
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}