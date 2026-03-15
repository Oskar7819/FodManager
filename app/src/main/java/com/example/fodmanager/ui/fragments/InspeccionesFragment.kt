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
import com.example.fodmanager.ui.inspecciones.DetalleInspeccionActivity
import com.example.fodmanager.ui.inspecciones.InspeccionesAdapter
import com.example.fodmanager.ui.inspecciones.NuevaInspeccionActivity
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

/*  Fragment que muestra la lista de inspecciones en el tab Inspecciones del Bottom Navigation.
    Las inspecciones mostradas se filtran según el rol del usuario:
    - rolesGenerales (administrador, head_plant, focal_point_fod) ven todas las inspecciones
    - Resto de roles (mando_gp4, quality, operario) → solo ven las de su aeronave asignada  */
class InspeccionesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: InspeccionesAdapter
    private val inspecciones = mutableListOf<Inspeccion>()

    // Mapa que relaciona el ID de cada aeronave con su nombre (modelo - número de serie)
    // Se usa para mostrar el nombre de la aeronave en cada tarjeta de inspección
    private val aeronavesMap = mutableMapOf<Int, String>()

    // Roles que tienen visión general de todas las inspecciones sin filtro por aeronave
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    // Launcher para abrir NuevaInspeccionActivity y recargar la lista
    // si se creó una nueva inspección (cuando devuelve RESULT_OK)
    private val nuevaInspeccionLauncher = registerForActivityResult(
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
        val view = inflater.inflate(R.layout.fragment_inspecciones, container, false)

        recyclerView = view.findViewById(R.id.recyclerInspecciones)

        // Configura el adapter con la lista de inspecciones y el mapa de aeronaves.
        // Al pulsar una tarjeta abre DetalleInspeccionActivity pasando el ID de la inspección
        adapter = InspeccionesAdapter(inspecciones, aeronavesMap) { inspeccion ->
            val intent = Intent(requireContext(), DetalleInspeccionActivity::class.java)
            intent.putExtra("inspeccion_id", inspeccion.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // FAB (Floating Action Button) para crear una nueva inspección
        // Usa el launcher para detectar si se creó una y recargar la lista
        val fab = view.findViewById<FloatingActionButton>(R.id.fabNuevaInspeccion)
        fab.setOnClickListener {
            val intent = Intent(requireContext(), NuevaInspeccionActivity::class.java)
            nuevaInspeccionLauncher.launch(intent)
        }

        cargarDatos()
        return view
    }

    // Carga las inspecciones y aeronaves desde Supabase según el rol del usuario logueado
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

                // Carga todas las inspecciones o solo las de la aeronave del usuario según su rol
                val resultado = if (usuarioLogueado.rol in rolesGenerales) {
                    // Roles generales ven todas las inspecciones del sistema
                    supabase.postgrest["inspecciones"]
                        .select()
                        .decodeList<Inspeccion>()
                } else {
                    // El resto solo ve las inspecciones de su aeronave asignada
                    // Si aeronaveId es null usa -1 para que no devuelva resultados
                    supabase.postgrest["inspecciones"]
                        .select { filter { eq("aeronave_id", usuarioLogueado.aeronaveId ?: -1) } }
                        .decodeList<Inspeccion>()
                }

                inspecciones.clear()
                inspecciones.addAll(resultado)
                // Notifica al adapter que los datos han cambiado para actualizar la UI
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}