package com.example.fodmanager.ui.inspecciones

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Inspeccion

// Adapter que conecta la lista de inspecciones con el RecyclerView.
// Recibe la lista de inspecciones, un mapa de aeronaves para mostrar su nombre,
// y una función lambda que se ejecuta al pulsar una tarjeta.
class InspeccionesAdapter(
    private val inspecciones: MutableList<Inspeccion>,
    // Mapa que relaciona el ID de la aeronave con su nombre (modelo - número de serie)
    private val aeronaves: Map<Int, String>,
    private val onItemClick: (Inspeccion) -> Unit
) : RecyclerView.Adapter<InspeccionesAdapter.ViewHolder>() {

    // ViewHolder almacena las referencias a los elementos visuales de cada tarjeta
    // para evitar búsquedas repetidas por ID, mejorando el rendimiento
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvZona: TextView = view.findViewById(R.id.tvZona)
        val tvAeronave: TextView = view.findViewById(R.id.tvAeronave)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvConFod: TextView = view.findViewById(R.id.tvConFod)
    }

    // Se llama cuando el RecyclerView necesita crear una nueva tarjeta visual.
    // Infla el layout XML item_inspeccion y lo envuelve en un ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inspeccion, parent, false)
        return ViewHolder(view)
    }

    // Se llama para rellenar cada tarjeta con los datos de la inspección correspondiente
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val inspeccion = inspecciones[position]

        holder.tvZona.text = inspeccion.zona

        // Muestra el nombre de la aeronave usando el mapa de aeronaves.
        // Si no encuentra la aeronave en el mapa muestra "Aeronave desconocida"
        holder.tvAeronave.text = inspeccion.aeronaveId?.let {
            aeronaves[it] ?: "Aeronave desconocida"
        } ?: "Sin aeronave"

        // Formatea la fecha del formato ISO (2024-01-15T08:30:00) a dd/mm/yyyy HH:mm
        val fechaFormateada = inspeccion.fecha?.let {
            try {
                val partes = it.split("T")
                val fecha = partes[0]
                val hora = partes[1].substring(0, 5)
                val (anio, mes, dia) = fecha.split("-")
                "$dia/$mes/$anio  $hora"
            } catch (e: Exception) { it }
        } ?: "Sin fecha"
        holder.tvFecha.text = fechaFormateada

        // Muestra si se encontró FOD con emoji indicativo
        holder.tvConFod.text = if (inspeccion.conFod) "⚠️ Con FOD" else "✅ Sin FOD"

        // Asigna el listener de click a toda la tarjeta,
        // llamando a la función lambda con la inspección seleccionada
        holder.itemView.setOnClickListener { onItemClick(inspeccion) }
    }

    // Devuelve el número total de inspecciones en la lista
    override fun getItemCount() = inspecciones.size
}