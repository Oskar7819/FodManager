package com.example.fodmanager.ui.usuarios

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Usuario

/**
 * Adapter que conecta la lista de Usuario con el RecyclerView del fragment de usuarios.
 *
 * Recibe aeronavesMap para mostrar el nombre legible de la aeronave asignada
 * a cada usuario sin necesidad de consultas adicionales por elemento.
 *
 * @param usuarios      Lista mutable de usuarios a mostrar.
 * @param aeronavesMap  Mapa aeronave_id → "Modelo - NumSerie".
 * @param onItemClick   Lambda invocada al pulsar una tarjeta, recibe el [Usuario] seleccionado.
 */
class UsuarioAdapter(
    private val usuarios: MutableList<Usuario>,
    private val aeronavesMap: Map<Int, String>,
    private val onItemClick: (Usuario) -> Unit
) : RecyclerView.Adapter<UsuarioAdapter.ViewHolder>() {

    /**
     * Almacena referencias a las vistas del layout `item_usuario`
     * para evitar llamadas repetidas a [View.findViewById] en cada reciclaje.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvUsuarioNombre)
        val tvEmail: TextView = view.findViewById(R.id.tvUsuarioEmail)
        val tvRol: TextView = view.findViewById(R.id.tvUsuarioRol)
        val tvNumeroEmpleado: TextView = view.findViewById(R.id.tvUsuarioNumeroEmpleado)
        val tvAeronave: TextView = view.findViewById(R.id.tvUsuarioAeronave)
    }

    /** Infla el layout `item_usuario` y lo envuelve en un [ViewHolder]. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usuario, parent, false)
        return ViewHolder(view)
    }

    /**
     * Rellena la tarjeta en [position] con los datos del usuario correspondiente.
     *
     * Para la aeronave asignada se aplica esta lógica:
     * - Usuario.aeronaveId con entrada en [aeronavesMap] → nombre de la aeronave.
     * - Usuario.aeronaveId sin entrada en el mapa → "Aeronave desconocida" (dato inconsistente).
     * - Usuario.aeronaveId null → "Sin aeronave asignada" (roles que no se adscriben).
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val usuario = usuarios[position]

        holder.tvNombre.text = "${usuario.nombre} ${usuario.apellidos}"
        holder.tvEmail.text = usuario.email
        holder.tvRol.text = "Rol: ${usuario.rol}"
        holder.tvNumeroEmpleado.text = "Empleado: ${usuario.numeroEmpleado ?: "No especificado"}"

        holder.tvAeronave.text = "✈️ ${
            usuario.aeronaveId?.let { aeronavesMap[it] ?: "Aeronave desconocida" }
                ?: "Sin aeronave asignada"
        }"

        holder.itemView.setOnClickListener { onItemClick(usuario) }
    }

    // Devuelve el número total de usuarios de la lista.
    override fun getItemCount() = usuarios.size
}