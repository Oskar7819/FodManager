package com.example.fodmanager.ui.incidencias

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.IncidenciaFod

// Adapter que conecta la lista de incidencias FOD con el RecyclerView.
// Recibe la lista de incidencias, un mapa de aeronaves para mostrar su nombre,
// y una función lambda que se ejecuta al pulsar una tarjeta.
class IncidenciasAdapter(
    private val incidencias: MutableList<IncidenciaFod>,
    // Mapa que relaciona el ID de la aeronave con su nombre (modelo - número de serie)
    private val aeronavesMap: Map<Int, String>,
    private val onItemClick: (IncidenciaFod) -> Unit
) : RecyclerView.Adapter<IncidenciasAdapter.ViewHolder>() {

    // ViewHolder almacena las referencias a los elementos visuales de cada tarjeta
    // para evitar búsquedas repetidas por ID, mejorando el rendimiento
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAeronave: TextView = view.findViewById(R.id.tvIncidenciaAeronave)
        // tvDescripcion se reutiliza para mostrar el tipo de FOD en la tarjeta
        val tvDescripcion: TextView = view.findViewById(R.id.tvIncidenciaDescripcion)
        val tvZonaAvion: TextView = view.findViewById(R.id.tvIncidenciaZonaAvion)
        val tvNumeroEmpleado: TextView = view.findViewById(R.id.tvIncidenciaNumeroEmpleado)
        val tvFecha: TextView = view.findViewById(R.id.tvIncidenciaFecha)
        val tvEstado: TextView = view.findViewById(R.id.tvIncidenciaEstado)
    }

    // Se llama cuando el RecyclerView necesita crear una nueva tarjeta visual.
    // Infla el layout XML item_incidencia y lo envuelve en un ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incidencia, parent, false)
        return ViewHolder(view)
    }

    // Se llama para rellenar cada tarjeta con los datos de la incidencia correspondiente
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val incidencia = incidencias[position]

        // Muestra el nombre de la aeronave usando el mapa de aeronaves.
        // Si no encuentra la aeronave en el mapa muestra Aeronave desconocida
        holder.tvAeronave.text = incidencia.aeronaveId?.let {
            aeronavesMap[it] ?: "Aeronave desconocida"
        } ?: "Sin aeronave"

        // En la tarjeta se muestra el tipo de FOD con emoji en lugar de la descripción completa.
        // La descripción completa se puede ver en el detalle de la incidencia
        holder.tvDescripcion.text = when (incidencia.tipoFod) {
            "ambiental" -> "🌫️ Ambiental"
            "herramientas" -> "🔧 Herramientas"
            "restos_metalicos" -> "🔩 Restos metálicos"
            "material_consumo" -> "🧤 Material de consumo"
            "personal" -> "👤 Personal"
            "procedente_aeronave" -> "✈️ Procedente de aeronave"
            else -> "Sin clasificar"
        }

        holder.tvZonaAvion.text = "Zona: ${incidencia.zonaAvion ?: "No especificada"}"
        holder.tvNumeroEmpleado.text = "Empleado: ${incidencia.numeroEmpleado ?: "No especificado"}"

        // Formatea la fecha del formato ISO (2024-01-15T08:30:00) a dd/mm/yyyy HH:mm
        val fechaFormateada = incidencia.createdAt?.let {
            try {
                val partes = it.split("T")
                val fecha = partes[0]
                val hora = partes[1].substring(0, 5)
                val (anio, mes, dia) = fecha.split("-")
                "$dia/$mes/$anio  $hora"
            } catch (e: Exception) { it }
        } ?: "Sin fecha"
        holder.tvFecha.text = fechaFormateada

        // Muestra el estado de la incidencia con emoji de color
        holder.tvEstado.text = when (incidencia.estado) {
            "abierta" -> "🔴 Abierta"
            "en_proceso" -> "🟡 En proceso"
            "cerrada" -> "🟢 Cerrada"
            else -> incidencia.estado
        }

        // Asigna el listener de click a toda la tarjeta,
        // llamando a la función lambda con la incidencia seleccionada
        holder.itemView.setOnClickListener { onItemClick(incidencia) }
    }

    // Devuelve el número total de incidencias en la lista
    override fun getItemCount() = incidencias.size
}