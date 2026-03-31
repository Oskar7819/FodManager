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
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.data.repository.UsuarioRepository
import com.example.fodmanager.ui.usuarios.EditarUsuarioActivity
import com.example.fodmanager.ui.usuarios.NuevoUsuarioActivity
import com.example.fodmanager.ui.usuarios.UsuarioAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.launch

// Fragment encargado de mostrar la lista de usuarios
class UsuariosFragment : Fragment() {

    // RecyclerView donde se mostrará la lista de usuarios
    private lateinit var recyclerView: RecyclerView

    // Adaptador que gestiona cómo se pintan los usuarios en la lista
    private lateinit var adapter: UsuarioAdapter

    // Botón flotante para crear un nuevo usuario
    private lateinit var fab: FloatingActionButton

    // Lista mutable que almacena los usuarios cargados
    private val usuarios = mutableListOf<Usuario>()

    // Mapa que relaciona el id de la aeronave con su texto descriptivo
    private var aeronavesMap = mapOf<Int, String>()

    // Lanzador para abrir la pantalla de nuevo usuario y recargar datos al volver
    private val nuevoUsuarioLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Si el resultado es correcto, se recargan los datos
        if (result.resultCode == Activity.RESULT_OK) {
            cargarDatos()
        }
    }

    // Crea e inicializa la vista del fragment
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Infla el layout del fragment
        val view = inflater.inflate(R.layout.fragment_usuarios, container, false)

        // Vincula el RecyclerView del layout
        recyclerView = view.findViewById(R.id.recyclerUsuarios)

        // Vincula el botón flotante del layout
        fab = view.findViewById(R.id.fabNuevoUsuario)

        // Inicializa el adaptador con la lista de usuarios y la acción al pulsar uno
        adapter = UsuarioAdapter(usuarios, aeronavesMap) { usuario ->
            abrirEditar(usuario)
        }

        // Asigna un layout lineal al RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Asigna el adaptador al RecyclerView
        recyclerView.adapter = adapter

        // Acción al pulsar el botón flotante para crear un nuevo usuario
        fab.setOnClickListener {
            val intent = Intent(requireContext(), NuevoUsuarioActivity::class.java)
            nuevoUsuarioLauncher.launch(intent)
        }

        // Carga los datos iniciales
        cargarDatos()
        return view
    }

    // Abre la pantalla de edición de un usuario enviando sus datos por intent
    private fun abrirEditar(usuario: Usuario) {
        val intent = Intent(requireContext(), EditarUsuarioActivity::class.java)

        // Envía el id del usuario
        intent.putExtra("usuario_id", usuario.id)

        // Envía el nombre completo del usuario
        intent.putExtra("usuario_nombre", "${usuario.nombre} ${usuario.apellidos}")

        // Envía el email del usuario
        intent.putExtra("usuario_email", usuario.email)

        // Envía el rol del usuario
        intent.putExtra("usuario_rol", usuario.rol)

        // Si tiene aeronave asociada, la envía también
        usuario.aeronaveId?.let { intent.putExtra("usuario_aeronave_id", it) }

        // Abre la actividad de edición
        startActivity(intent)
    }

    // Carga los usuarios y aeronaves desde la base de datos
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                // Obtiene el usuario autenticado actual
                val usuarioActual = UsuarioRepository.getUsuarioActual()

                // Busca en la tabla usuarios el registro del usuario logueado
                val usuarioLogueado = supabase.postgrest["usuarios"]
                    .select {
                        filter {
                            eq("email", usuarioActual.email)
                        }
                    }
                    .decodeSingle<Usuario>()

                // Obtiene todas las aeronaves
                val aeronaves = supabase.postgrest["aeronaves"]
                    .select()
                    .decodeList<Aeronave>()

                // Construye un mapa con el id de la aeronave y su descripción
                aeronavesMap = aeronaves.associate { aeronave ->
                    aeronave.id to "${aeronave.modelo} - ${aeronave.numeroSerie}"
                }

                // Obtiene los usuarios visibles según el rol del usuario logueado
                val resultado = when (usuarioLogueado.rol) {
                    "administrador" -> {
                        // El administrador puede ver todos los usuarios
                        supabase.postgrest["usuarios"]
                            .select()
                            .decodeList<Usuario>()
                    }

                    "focal_point_fod" -> {
                        // El focal point FOD puede ver usuarios con rol mando_gp4 o quality
                        supabase.postgrest["usuarios"]
                            .select {
                                filter {
                                    or {
                                        eq("rol", "mando_gp4")
                                        eq("rol", "quality")
                                    }
                                }
                            }
                            .decodeList<Usuario>()
                    }

                    "mando_gp4" -> {
                        // El mando_gp4 puede ver operarios de su misma aeronave
                        supabase.postgrest["usuarios"]
                            .select {
                                filter {
                                    and {
                                        eq("rol", "operario")
                                        eq("aeronave_id", usuarioLogueado.aeronaveId ?: -1)
                                    }
                                }
                            }
                            .decodeList<Usuario>()
                    }

                    // Para cualquier otro rol no se muestran usuarios
                    else -> emptyList()
                }

                // Limpia la lista actual de usuarios
                usuarios.clear()

                // Añade los usuarios obtenidos
                usuarios.addAll(resultado)

                // Recrea el adaptador con los nuevos datos
                adapter = UsuarioAdapter(usuarios, aeronavesMap) { usuario ->
                    abrirEditar(usuario)
                }

                // Asigna el nuevo adaptador al RecyclerView
                recyclerView.adapter = adapter

                // Notifica al adaptador que los datos han cambiado
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                // Muestra un mensaje si ocurre algún error
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}