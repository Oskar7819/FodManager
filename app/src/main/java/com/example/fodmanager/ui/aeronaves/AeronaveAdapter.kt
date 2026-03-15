package com.example.fodmanager.ui.aeronaves

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave

/* Adapter que conecta la lista de aeronaves con el RecyclerView.
   Un Adapter es el componente que toma los datos de una lista y los convierte
   en tarjetas visuales que se muestran en pantalla.    */
class AeronaveAdapter(
    private val aeronaves: MutableList<Aeronave>,
    private val onItemClick: (Aeronave) -> Unit
) : RecyclerView.Adapter<AeronaveAdapter.ViewHolder>() {

    // ViewHolder almacena las referencias a los elementos visuales de cada tarjeta.
    // Evita buscar los elementos por ID en cada actualización, mejorando el rendimiento.
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvModelo: TextView = view.findViewById(R.id.tvAeronaveModelo)
        val tvNumeroSerie: TextView = view.findViewById(R.id.tvAeronaveNumeroSerie)
        val tvUbicacion: TextView = view.findViewById(R.id.tvAeronaveUbicacion)
        val tvEstado: TextView = view.findViewById(R.id.tvAeronaveEstado)
    }

    // Se llama cuando el RecyclerView necesita crear una nueva tarjeta visual.
    // Infla el layout XML item_aeronave en un objeto View y lo envuelve en un ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_aeronave, parent, false)
        return ViewHolder(view)
    }

    // Se llama para rellenar cada tarjeta con los datos de la aeronave correspondiente.
    // position indica qué elemento de la lista se está mostrando.
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val aeronave = aeronaves[position]

        // Rellena cada TextView con los datos de la aeronave
        holder.tvModelo.text = aeronave.modelo
        holder.tvNumeroSerie.text = "S/N: ${aeronave.numeroSerie}"

        // Si la ubicación es null muestra "Sin ubicación"
        holder.tvUbicacion.text = " ${aeronave.ubicacion ?: "Sin ubicación"}"

        // Muestra el estado de la aeronave con un emoji de color
        holder.tvEstado.text = if (aeronave.activa) "🟢 Activa" else "🔴 Inactiva"

        // Asigna el listener de click a toda la tarjeta,
        // llamando a la función lambda pasada al adapter con la aeronave seleccionada
        holder.itemView.setOnClickListener { onItemClick(aeronave) }
    }

    // Devuelve el número total de elementos de la lista,
    // necesario para que el RecyclerView sepa cuántas tarjetas debe crear
    override fun getItemCount() = aeronaves.size
}