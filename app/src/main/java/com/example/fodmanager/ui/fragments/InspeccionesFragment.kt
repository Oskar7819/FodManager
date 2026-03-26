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
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.ui.inspecciones.DetalleInspeccionActivity
import com.example.fodmanager.ui.inspecciones.InspeccionesAdapter
import com.example.fodmanager.ui.inspecciones.NuevaInspeccionActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

/**
 * Fragment del tab "Inspecciones" del Bottom Navigation.
 *
 * Responsabilidades:
 * - Mostrar la lista de inspecciones filtrada según el rol del usuario, ordenada
 *   de más reciente a más antigua.
 * - Mantener mapas auxiliares (aeronaves y usuarios) para enriquecer cada tarjeta.
 * - Abrir NuevaInspeccionActivity desde el FAB y recargar la lista al regresar.
 * - Abrir DetalleInspeccionActivity al pulsar una tarjeta.
 *
 * Regla de visibilidad (misma que IncidenciasFragment):
 * - rolesGenerales ven todas las inspecciones del sistema.
 * - Resto  solo ven las inspecciones de su aeronave asignada.
 */
class InspeccionesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: InspeccionesAdapter

    private val inspecciones = mutableListOf<Inspeccion>()

    /** Mapa aeronave_id → "Modelo - NumSerie" para mostrar el nombre en cada tarjeta. */
    private val aeronavesMap = mutableMapOf<Int, String>()

    /** Mapa usuario_id → [Usuario] para mostrar el nombre del inspector en cada tarjeta. */
    private val usuariosMap = mutableMapOf<Int, Usuario>()

    /** Roles con visión global de todas las inspecciones del sistema. */
    private val rolesGenerales = listOf(
        "administrador",
        "head_plant",
        "focal_point_fod"
    )

    /**
     * Launcher para [NuevaInspeccionActivity].
     * Recarga la lista si el resultado es [Activity.RESULT_OK].
     */
    private val nuevaInspeccionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            cargarDatos()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_inspecciones, container, false)

        recyclerView = view.findViewById(R.id.recyclerInspecciones)

        adapter = InspeccionesAdapter(inspecciones, aeronavesMap, usuariosMap) { inspeccion ->
            val intent = Intent(requireContext(), DetalleInspeccionActivity::class.java)
            intent.putExtra("inspeccion_id", inspeccion.id)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val fab = view.findViewById<FloatingActionButton>(R.id.fabNuevaInspeccion)
        fab.setOnClickListener {
            val intent = Intent(requireContext(), NuevaInspeccionActivity::class.java)
            nuevaInspeccionLauncher.launch(intent)
        }

        cargarDatos()
        return view
    }

    /**
     * Carga todos los datos necesarios para el listado:
     * 1. Usuario logueado → determina el filtro (global o por aeronave).
     * 2. Aeronaves → construye aeronavesMap.
     * 3. Usuarios → construye usuariosMap.
     * 4. Inspecciones visibles, ordenadas descendentemente por fecha.
     *
     * El ordenamiento se realiza en cliente porque la API no expone
     * cómodamente un `.order()` con la versión del SDK actual.
     */
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                val usuarioLogueado = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email) } }
                    .decodeSingle<Usuario>()

                val aeronaves = supabase.postgrest["aeronaves"]
                    .select()
                    .decodeList<Aeronave>()

                aeronavesMap.clear()
                aeronaves.forEach { aeronavesMap[it.id] = "${it.modelo} - ${it.numeroSerie}" }

                val usuarios = supabase.postgrest["usuarios"]
                    .select()
                    .decodeList<Usuario>()

                usuariosMap.clear()
                usuarios.forEach { usuariosMap[it.id] = it }

                val resultado = if (usuarioLogueado.rol in rolesGenerales) {
                    supabase.postgrest["inspecciones"]
                        .select()
                        .decodeList<Inspeccion>()
                } else {
                    supabase.postgrest["inspecciones"]
                        .select { filter { eq("aeronave_id", usuarioLogueado.aeronaveId ?: -1) } }
                        .decodeList<Inspeccion>()
                }

                // Ordena de más reciente a más antigua usando la fecha ISO como string
                // (funciona correctamente porque el formato "YYYY-MM-DD..." es lexicográficamente ordenable)
                val resultadoOrdenado = resultado.sortedByDescending { it.fecha ?: "" }

                inspecciones.clear()
                inspecciones.addAll(resultadoOrdenado)
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}