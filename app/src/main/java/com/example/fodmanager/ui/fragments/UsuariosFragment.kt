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
import com.example.fodmanager.ui.usuarios.EditarUsuarioActivity
import com.example.fodmanager.ui.usuarios.NuevoUsuarioActivity
import com.example.fodmanager.R
import com.example.fodmanager.ui.usuarios.UsuarioAdapter
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

// Fragment que muestra la lista de usuarios en el tab "Usuarios" del Bottom Navigation.
// Solo es visible para los roles: administrador, mando_gp4 y focal_point_fod.
// La lista de usuarios mostrada se filtra según el rol del usuario logueado:
// - administrador → ve todos los usuarios del sistema
// - focal_point_fod → ve solo los usuarios con rol mando_gp4 y quality
// - mando_gp4 → ve solo los operarios adscritos a su misma aeronave
class UsuariosFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsuarioAdapter
    private lateinit var fab: FloatingActionButton
    private val usuarios = mutableListOf<Usuario>()

    // Mapa que relaciona el ID de cada aeronave con su nombre (modelo - número de serie)
    // Se usa para mostrar la aeronave asignada en cada tarjeta de usuario
    private var aeronavesMap = mapOf<Int, String>()

    // Launcher para abrir NuevoUsuarioActivity y recargar la lista
    // si se creó un nuevo usuario (cuando devuelve RESULT_OK)
    private val nuevoUsuarioLauncher = registerForActivityResult(
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
        val view = inflater.inflate(R.layout.fragment_usuarios, container, false)

        recyclerView = view.findViewById(R.id.recyclerUsuarios)
        fab = view.findViewById(R.id.fabNuevoUsuario)

        // Configura el adapter inicial con listas vacías.
        // Se recrea en cargarDatos() una vez que se tienen los datos de aeronaves
        adapter = UsuarioAdapter(usuarios, aeronavesMap) { usuario ->
            abrirEditar(usuario)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // FAB para crear un nuevo usuario usando el launcher
        fab.setOnClickListener {
            val intent = Intent(requireContext(), NuevoUsuarioActivity::class.java)
            nuevoUsuarioLauncher.launch(intent)
        }

        cargarDatos()
        return view
    }

    // Abre EditarUsuarioActivity pasando los datos del usuario seleccionado mediante el Intent.
    // Se extrajo a función separada para evitar duplicar código en onCreateView y cargarDatos
    private fun abrirEditar(usuario: Usuario) {
        val intent = Intent(requireContext(), EditarUsuarioActivity::class.java)
        intent.putExtra("usuario_id", usuario.id)
        intent.putExtra("usuario_nombre", "${usuario.nombre} ${usuario.apellidos}")
        intent.putExtra("usuario_email", usuario.email)
        intent.putExtra("usuario_rol", usuario.rol)
        // Solo añade aeronave_id al Intent si el usuario tiene una aeronave asignada
        usuario.aeronaveId?.let { intent.putExtra("usuario_aeronave_id", it) }
        startActivity(intent)
    }

    // Carga los datos desde Supabase filtrando los usuarios según el rol del usuario logueado
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
                val aeronaves = supabase.postgrest["aeronaves"]
                    .select()
                    .decodeList<Aeronave>()

                aeronavesMap = aeronaves.associate { it.id to "${it.modelo} - ${it.numeroSerie}" }

                // Filtra la lista de usuarios según el rol del usuario logueado
                // usando un when (equivalente al switch de otros lenguajes)
                val resultado = when (usuarioLogueado.rol) {
                    // El administrador ve todos los usuarios sin restricciones
                    "administrador" -> {
                        supabase.postgrest["usuarios"]
                            .select()
                            .decodeList<Usuario>()
                    }
                    // focal_point_fod solo ve mandos y quality para poder asignarles aeronaves
                    "focal_point_fod" -> {
                        supabase.postgrest["usuarios"]
                            .select { filter {
                                or {
                                    eq("rol", "mando_gp4")
                                    eq("rol", "quality")
                                }
                            }}
                            .decodeList<Usuario>()
                    }
                    // mando_gp4 solo ve los operarios de su misma aeronave
                    // para poder gestionarlos
                    "mando_gp4" -> {
                        supabase.postgrest["usuarios"]
                            .select { filter {
                                and {
                                    eq("rol", "operario")
                                    // Filtra por la aeronave del mando logueado
                                    eq("aeronave_id", usuarioLogueado.aeronaveId ?: -1)
                                }
                            }}
                            .decodeList<Usuario>()
                    }
                    // Cualquier otro rol no ve usuarios (lista vacía)
                    else -> emptyList()
                }

                usuarios.clear()
                usuarios.addAll(resultado)

                // Recrea el adapter con el mapa de aeronaves actualizado
                // para que las tarjetas muestren correctamente el nombre de la aeronave
                adapter = UsuarioAdapter(usuarios, aeronavesMap) { usuario ->
                    abrirEditar(usuario)
                }
                recyclerView.adapter = adapter
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}