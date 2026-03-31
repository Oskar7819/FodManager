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
import com.example.fodmanager.data.repository.UsuarioRepository
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.ui.aeronaves.AeronaveAdapter
import com.example.fodmanager.ui.aeronaves.DetalleAeronaveActivity
import com.example.fodmanager.ui.aeronaves.NuevaAeronaveActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.launch

// Fragment encargado de mostrar la lista de aeronaves
class AeronaveFragment : Fragment() {

    // RecyclerView donde se mostrarán las aeronaves
    private lateinit var recyclerView: RecyclerView

    // Adaptador que gestiona cómo se muestran los elementos en la lista
    private lateinit var adapter: AeronaveAdapter

    // Botón flotante para crear una nueva aeronave
    private lateinit var fab: FloatingActionButton

    // Lista mutable que almacena las aeronaves cargadas
    private val aeronaves = mutableListOf<Aeronave>()

    // Roles que tienen permiso para ver el botón de añadir aeronaves
    private val rolesConPermiso = listOf("administrador", "mando_gp4", "focal_point_fod")

    // Roles que pueden ver todas las aeronaves
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    // Lanzador para abrir la actividad de nueva aeronave y recibir el resultado
    private val nuevaAeronaveLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Si la actividad devuelve resultado correcto, se recargan los datos
        if (result.resultCode == Activity.RESULT_OK) {
            cargarDatos()
        }
    }

    // Se llama para crear y devolver la vista del fragment
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Infla el layout del fragment
        val view = inflater.inflate(R.layout.fragment_aeronave, container, false)

        // Vincula el RecyclerView del layout
        recyclerView = view.findViewById(R.id.recyclerAeronaves)

        // Vincula el FloatingActionButton del layout
        fab = view.findViewById(R.id.fabNuevaAeronave)

        // Inicializa el adaptador con la lista de aeronaves y la acción al pulsar sobre una
        adapter = AeronaveAdapter(aeronaves) { aeronave ->
            val intent = Intent(requireContext(), DetalleAeronaveActivity::class.java)

            // Envía los datos de la aeronave seleccionada a la actividad de detalle
            intent.putExtra("aeronave_id", aeronave.id)
            intent.putExtra("aeronave_modelo", aeronave.modelo)
            intent.putExtra("aeronave_numero_serie", aeronave.numeroSerie)
            intent.putExtra("aeronave_ubicacion", aeronave.ubicacion)
            intent.putExtra("aeronave_activa", aeronave.activa)

            // Abre la actividad de detalle
            startActivity(intent)
        }

        // Establece un layout lineal vertical para el RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Asigna el adaptador al RecyclerView
        recyclerView.adapter = adapter

        // Acción al pulsar el botón flotante
        fab.setOnClickListener {
            val intent = Intent(requireContext(), NuevaAeronaveActivity::class.java)

            // Lanza la actividad para crear una nueva aeronave
            nuevaAeronaveLauncher.launch(intent)
        }

        // Devuelve la vista ya configurada
        return view
    }

    // Se ejecuta cada vez que el fragment vuelve a primer plano
    override fun onResume() {
        super.onResume()

        // Recarga los datos al reanudar el fragment
        cargarDatos()
    }

    // Función encargada de cargar los datos de aeronaves
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                // Obtiene el usuario actualmente logueado
                val usuarioLogueado = UsuarioRepository.getUsuarioActual()

                // Muestra u oculta el botón flotante según el rol del usuario
                fab.visibility =
                    if (usuarioLogueado.rol in rolesConPermiso) View.VISIBLE else View.GONE

                // Obtiene las aeronaves según el rol del usuario
                val resultado = if (usuarioLogueado.rol in rolesGenerales) {
                    // Si tiene un rol general, obtiene todas las aeronaves
                    supabase.postgrest["aeronaves"]
                        .select()
                        .decodeList<Aeronave>()
                } else {
                    // Si no tiene rol general, obtiene solo la aeronave asociada a su usuario
                    supabase.postgrest["aeronaves"]
                        .select {
                            filter {
                                eq("id", usuarioLogueado.aeronaveId ?: -1)
                            }
                        }
                        .decodeList<Aeronave>()
                }

                // Limpia la lista actual de aeronaves
                aeronaves.clear()

                // Añade las nuevas aeronaves obtenidas
                aeronaves.addAll(resultado)

                // Notifica al adaptador que los datos han cambiado
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                // Muestra un mensaje de error si ocurre alguna excepción
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}