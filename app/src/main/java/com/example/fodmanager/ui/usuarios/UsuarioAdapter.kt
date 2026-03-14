package com.example.fodmanager.ui.usuarios

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Usuario

// Adapter que conecta la lista de usuarios con el RecyclerView.
// Recibe la lista de usuarios, un mapa de aeronaves para mostrar el nombre de la aeronave asignada,
// y una función lambda que se ejecuta al pulsar una tarjeta.
class UsuarioAdapter(
    private val usuarios: MutableList<Usuario>,
    // Mapa que relaciona el ID de la aeronave con su nombre (modelo - número de serie)
    // Se usa para mostrar el nombre de la aeronave asignada en cada tarjeta de usuario
    private val aeronavesMap: Map<Int, String>,
    private val onItemClick: (Usuario) -> Unit
) : RecyclerView.Adapter<UsuarioAdapter.ViewHolder>() {

    // ViewHolder almacena las referencias a los elementos visuales de cada tarjeta
    // para evitar búsquedas repetidas por ID, mejorando el rendimiento
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvUsuarioNombre)
        val tvEmail: TextView = view.findViewById(R.id.tvUsuarioEmail)
        val tvRol: TextView = view.findViewById(R.id.tvUsuarioRol)
        val tvNumeroEmpleado: TextView = view.findViewById(R.id.tvUsuarioNumeroEmpleado)
        val tvAeronave: TextView = view.findViewById(R.id.tvUsuarioAeronave)
    }

    // Se llama cuando el RecyclerView necesita crear una nueva tarjeta visual.
    // Infla el layout XML item_usuario y lo envuelve en un ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usuario, parent, false)
        return ViewHolder(view)
    }

    // Se llama para rellenar cada tarjeta con los datos del usuario correspondiente
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val usuario = usuarios[position]

        // Muestra nombre completo concatenando nombre y apellidos
        holder.tvNombre.text = "${usuario.nombre} ${usuario.apellidos}"
        holder.tvEmail.text = usuario.email
        holder.tvRol.text = "Rol: ${usuario.rol}"

        // Si el número de empleado es null muestra "No especificado"
        holder.tvNumeroEmpleado.text = "Empleado: ${usuario.numeroEmpleado ?: "No especificado"}"

        // Muestra la aeronave asignada usando el mapa de aeronaves.
        // Si el usuario tiene aeronave_id pero no se encuentra en el mapa muestra "Aeronave desconocida"
        // Si el usuario no tiene aeronave asignada (aeronave_id null) muestra "Sin aeronave asignada"
        holder.tvAeronave.text = "✈️ ${usuario.aeronaveId?.let {
            aeronavesMap[it] ?: "Aeronave desconocida"
        } ?: "Sin aeronave asignada"}"

        // Asigna el listener de click a toda la tarjeta,
        // llamando a la función lambda con el usuario seleccionado
        holder.itemView.setOnClickListener { onItemClick(usuario) }
    }

    // Devuelve el número total de usuarios en la lista
    override fun getItemCount() = usuarios.size
}