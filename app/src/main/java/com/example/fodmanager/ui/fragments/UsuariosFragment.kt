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

/**
 * Fragment del tab "Usuarios" del Bottom Navigation.
 *
 * Solo es visible para los roles: `administrador`, `mando_gp4` y `focal_point_fod`.
 * La visibilidad del tab se controla en la activity principal al cargar el rol.
 *
 * Lista de usuarios mostrada según rol:
 * - `administrador`    → todos los usuarios del sistema.
 * - `focal_point_fod`  → solo usuarios con rol `mando_gp4` y `quality`.
 * - `mando_gp4`        → solo operarios adscritos a su misma aeronave.
 * - Cualquier otro rol → lista vacía (no debería llegar a este fragment).
 *
 * Al pulsar una tarjeta se abre EditarUsuarioActivity para modificar los datos
 * del usuario seleccionado.
 */
class UsuariosFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsuarioAdapter
    private lateinit var fab: FloatingActionButton
    private val usuarios = mutableListOf<Usuario>()

    /**
     * Mapa aeronave_id → "Modelo - NumSerie".
     * Se construye en cargarDatos y se pasa al adapter para que cada tarjeta
     * muestre el nombre legible de la aeronave asignada al usuario.
     */
    private var aeronavesMap = mapOf<Int, String>()

    /**
     * Launcher para [NuevoUsuarioActivity].
     * Recarga la lista si el resultado es [Activity.RESULT_OK].
     */
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

        // El adapter se inicializa con listas vacías; se recrea en cargarDatos()
        // una vez que el mapa de aeronaves esté disponible
        adapter = UsuarioAdapter(usuarios, aeronavesMap) { usuario -> abrirEditar(usuario) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            nuevoUsuarioLauncher.launch(Intent(requireContext(), NuevoUsuarioActivity::class.java))
        }

        cargarDatos()
        return view
    }

    /**
     * Abre EditarUsuarioActivity con los datos del usuario seleccionado.
     * Se extrae a función independiente para reutilizarla tanto en el adapter
     * inicial como en el que se recrea tras recargar datos.
     */
    private fun abrirEditar(usuario: Usuario) {
        val intent = Intent(requireContext(), EditarUsuarioActivity::class.java)
        intent.putExtra("usuario_id", usuario.id)
        intent.putExtra("usuario_nombre", "${usuario.nombre} ${usuario.apellidos}")
        intent.putExtra("usuario_email", usuario.email)
        intent.putExtra("usuario_rol", usuario.rol)
        // Solo añade aeronave_id si el usuario tiene una aeronave asignada
        usuario.aeronaveId?.let { intent.putExtra("usuario_aeronave_id", it) }
        startActivity(intent)
    }

    /**
     * Carga los datos desde Supabase en este orden:
     * 1. Usuario logueado → determina el filtro de la lista.
     * 2. Todas las aeronaves → construye aeronavesMap.
     * 3. Usuarios visibles según el rol (ver KDoc de la clase).
     *
     * Al final se recrea el adapter con el aeronavesMap actualizado para que
     * las tarjetas muestren correctamente el nombre de la aeronave de cada usuario.
     * Esto es necesario porque el adapter almacena el mapa por valor en su constructor.
     */
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email

                val usuarioLogueado = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<Usuario>()

                val aeronaves = supabase.postgrest["aeronaves"]
                    .select()
                    .decodeList<Aeronave>()

                aeronavesMap = aeronaves.associate { it.id to "${it.modelo} - ${it.numeroSerie}" }

                val resultado = when (usuarioLogueado.rol) {
                    "administrador" -> {
                        supabase.postgrest["usuarios"]
                            .select()
                            .decodeList<Usuario>()
                    }
                    "focal_point_fod" -> {
                        // Puede ver y gestionar mandos y quality para asignarles aeronaves
                        supabase.postgrest["usuarios"]
                            .select { filter {
                                or {
                                    eq("rol", "mando_gp4")
                                    eq("rol", "quality")
                                }
                            }}
                            .decodeList<Usuario>()
                    }
                    "mando_gp4" -> {
                        // Solo ve los operarios de su propia aeronave
                        supabase.postgrest["usuarios"]
                            .select { filter {
                                and {
                                    eq("rol", "operario")
                                    eq("aeronave_id", usuarioLogueado.aeronaveId ?: -1)
                                }
                            }}
                            .decodeList<Usuario>()
                    }
                    else -> emptyList()
                }

                usuarios.clear()
                usuarios.addAll(resultado)

                // Se recrea el adapter para que reciba el mapa de aeronaves actualizado
                adapter = UsuarioAdapter(usuarios, aeronavesMap) { usuario -> abrirEditar(usuario) }
                recyclerView.adapter = adapter
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}