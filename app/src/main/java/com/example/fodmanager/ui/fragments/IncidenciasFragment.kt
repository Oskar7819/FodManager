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
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.IncidenciaFod
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.ui.incidencias.DetalleIncidenciaActivity
import com.example.fodmanager.ui.incidencias.IncidenciasAdapter
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

/*
    Fragment que muestra la lista de incidencias FOD.

    REGLA IMPORTANTE:
    - Solo tienen visión global:
        administrador
        head_plant
        focal_point_fod

    - quality NO tiene visión global.
      Si tiene aeronave asignada, solo ve incidencias FOD de su avión.

    - Las incidencias deben mostrarse de más nueva a más antigua.
*/
class IncidenciasFragment : Fragment() {

    // RecyclerView principal
    private lateinit var recyclerView: RecyclerView

    // Adapter de la lista
    private lateinit var adapter: IncidenciasAdapter

    // Lista principal que alimenta el adapter
    private val incidencias = mutableListOf<IncidenciaFod>()

    // Mapas auxiliares para mostrar textos legibles
    private val aeronavesMap = mutableMapOf<Int, String>()
    private val usuariosMap = mutableMapOf<Int, Usuario>()

    /*
        Roles con visión global de todas las incidencias.
        OJO: quality no está aquí, porque solo debe ver su avión.
    */
    private val rolesGenerales = listOf(
        "administrador",
        "head_plant",
        "focal_point_fod"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_incidencias, container, false)

        // Enlazamos el RecyclerView del XML
        recyclerView = view.findViewById(R.id.recyclerIncidencias)

        /*
            Creamos el adapter y le pasamos:
            - lista de incidencias
            - mapa de aeronaves para mostrar "modelo - serie"
            - mapa de usuarios para mostrar quién declaró la incidencia
        */
        adapter = IncidenciasAdapter(
            incidencias,
            aeronavesMap,
            usuariosMap
        ) { incidencia ->
            /*
                Al pulsar una incidencia, abrimos su detalle.
            */
            val intent = Intent(requireContext(), DetalleIncidenciaActivity::class.java)
            intent.putExtra("incidencia_id", incidencia.id)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        cargarDatos()

        return view
    }

    /*
        Carga:
        1. usuario logueado
        2. aeronaves
        3. usuarios
        4. incidencias visibles según rol

        REGLAS:
        - quality solo ve incidencias de su aeronave asignada
        - las incidencias se ordenan de más nueva a más antigua
    */
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                // Email del usuario autenticado actualmente
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                // Usuario logueado completo
                val usuarioActual = supabase.postgrest["usuarios"]
                    .select {
                        filter { eq("email", email) }
                    }
                    .decodeSingle<Usuario>()

                /*
                    Cargamos aeronaves para poder mostrar un texto visible
                    en las tarjetas del RecyclerView.
                */
                val aeronaves = supabase.postgrest["aeronaves"]
                    .select()
                    .decodeList<Aeronave>()

                aeronavesMap.clear()
                aeronaves.forEach { aeronave ->
                    aeronavesMap[aeronave.id] = "${aeronave.modelo} - ${aeronave.numeroSerie}"
                }

                /*
                    Cargamos usuarios para poder mostrar el nombre
                    del declarante en cada incidencia.
                */
                val usuarios = supabase.postgrest["usuarios"]
                    .select()
                    .decodeList<Usuario>()

                usuariosMap.clear()
                usuarios.forEach { usuario ->
                    usuariosMap[usuario.id] = usuario
                }

                /*
                    Si el rol es global, ve todas las incidencias.
                    Si no, solo ve las incidencias de su aeronave asignada.
                */
                val resultado = if (usuarioActual.rol in rolesGenerales) {
                    supabase.postgrest["incidencias_fod"]
                        .select()
                        .decodeList<IncidenciaFod>()
                } else {
                    supabase.postgrest["incidencias_fod"]
                        .select {
                            filter { eq("aeronave_id", usuarioActual.aeronaveId ?: -1) }
                        }
                        .decodeList<IncidenciaFod>()
                }

                /*
                    ORDEN IMPORTANTE:
                    Queremos mostrar primero la incidencia más nueva.

                    Usamos createdAt en orden descendente.
                    Si alguna incidencia no tiene fecha, la dejamos al final.
                */
                val resultadoOrdenado = resultado.sortedByDescending { it.createdAt ?: "" }

                // Refrescamos la lista visual
                incidencias.clear()
                incidencias.addAll(resultadoOrdenado)
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}