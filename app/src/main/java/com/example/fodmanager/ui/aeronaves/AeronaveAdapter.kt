package com.example.fodmanager.ui.aeronaves

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave

/**
 * Adapter que conecta la lista de Aeronave con el RecyclerView del fragment de aeronaves.
 *
 * Sigue el patrón estándar de RecyclerView: infla el layout XML item_aeronave,
 * almacena referencias a sus vistas en un ViewHolder y las rellena con datos
 * en onBindViewHolder. El listener de click se inyecta como lambda en el constructor
 * para mantener el adapter desacoplado de la lógica de navegación.
 *
 * @param aeronaves    Lista mutable de aeronaves que se mostrará en el RecyclerView.
 * @param onItemClick  Lambda invocada cuando el usuario pulsa sobre una tarjeta.
 *                     Recibe la [Aeronave] seleccionada como parámetro.
 */
class AeronaveAdapter(
    private val aeronaves: MutableList<Aeronave>,
    private val onItemClick: (Aeronave) -> Unit
) : RecyclerView.Adapter<AeronaveAdapter.ViewHolder>() {

    /**
     * Almacena referencias a los elementos visuales de cada tarjeta de aeronave.
     *
     * Usando ViewHolder se evita llamar a View.findViewById en cada reciclaje,
     * lo que mejora el rendimiento al hacer scroll.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvModelo: TextView = view.findViewById(R.id.tvAeronaveModelo)
        val tvNumeroSerie: TextView = view.findViewById(R.id.tvAeronaveNumeroSerie)
        val tvUbicacion: TextView = view.findViewById(R.id.tvAeronaveUbicacion)
        val tvEstado: TextView = view.findViewById(R.id.tvAeronaveEstado)
    }

    /**
     * Crea e infla una nueva tarjeta visual cuando el RecyclerView necesita
     * mostrar un elemento que aún no existe en pantalla.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_aeronave, parent, false)
        return ViewHolder(view)
    }

    /**
     * Rellena la tarjeta en la posición position con los datos de la aeronave
     * correspondiente y registra el listener de click en toda la tarjeta.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val aeronave = aeronaves[position]

        holder.tvModelo.text = aeronave.modelo
        holder.tvNumeroSerie.text = "S/N: ${aeronave.numeroSerie}"

        // Muestra "Sin ubicación" si el campo es null
        holder.tvUbicacion.text = " ${aeronave.ubicacion ?: "Sin ubicación"}"

        // Indicador visual de estado mediante emoji de color
        holder.tvEstado.text = if (aeronave.activa) "🟢 Activa" else "🔴 Inactiva"

        // Delega el click al fragment/activity que creó el adapter,
        // pasando la aeronave seleccionada para que abra su pantalla de detalle
        holder.itemView.setOnClickListener { onItemClick(aeronave) }
    }

    /**
     * Devuelve el número total de elementos de la lista.
     * El RecyclerView lo usa para saber cuántas tarjetas debe gestionar.
     */
    override fun getItemCount() = aeronaves.size
}