package com.example.fodmanager.ui.inspecciones

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.models.Usuario

/**
 * Adapter que conecta la lista de [Inspeccion] con el RecyclerView del fragment de inspecciones.
 *
 * Recibe mapas auxiliares para enriquecer cada tarjeta sin consultas adicionales:
 * - aeronaves: aeronave_id → "Modelo - NumSerie"
 * - usuarios:  usuario_id → Usuario]completo
 *
 * @param inspecciones  Lista mutable de inspecciones a mostrar.
 * @param aeronaves     Mapa de aeronaves para mostrar el nombre en cada tarjeta.
 * @param usuarios      Mapa de usuarios para mostrar el inspector en cada tarjeta.
 * @param onItemClick   Lambda invocada al pulsar una tarjeta, recibe la Inspeccion seleccionada.
 */
class InspeccionesAdapter(
    private val inspecciones: MutableList<Inspeccion>,
    private val aeronaves: Map<Int, String>,
    private val usuarios: Map<Int, Usuario>,
    private val onItemClick: (Inspeccion) -> Unit
) : RecyclerView.Adapter<InspeccionesAdapter.ViewHolder>() {

    /**
     * Almacena referencias a las vistas del layout `item_inspeccion`
     * para evitar llamadas repetidas a [View.findViewById] en cada reciclaje.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvZona: TextView = view.findViewById(R.id.tvZona)
        val tvAeronave: TextView = view.findViewById(R.id.tvAeronave)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)

        // Turno operativo de la inspección.
        val tvTurnoInspeccion: TextView = view.findViewById(R.id.tvTurnoInspeccion)
        /** Línea compacta "Nombre Apellidos · Nº empleado" del inspector. */
        val tvInspectorResumen: TextView = view.findViewById(R.id.tvInspectorResumen)
        val tvConFod: TextView = view.findViewById(R.id.tvConFod)
    }

    /** Infla el layout `item_inspeccion` y lo envuelve en un [ViewHolder]. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inspeccion, parent, false)
        return ViewHolder(view)
    }

    /**
     * Rellena la tarjeta en [position] con los datos de la inspección correspondiente.
     *
     * La fecha ISO "YYYY-MM-DDTHH:mm:ss" se convierte al formato "dd/MM/yyyy  HH:mm"
     * para mejorar la legibilidad. Si el parseo falla, se muestra la cadena original.
     *
     * El inspector se muestra como "Nombre Apellidos · Nº empleado". Si no se encuentra
     * en usuarios, se usan textos por defecto.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val inspeccion = inspecciones[position]

        holder.tvZona.text = inspeccion.zona

        holder.tvAeronave.text = inspeccion.aeronaveId?.let {
            aeronaves[it] ?: "Aeronave desconocida"
        } ?: "Sin aeronave"

        // Conversión de formato ISO → "dd/MM/yyyy  HH:mm"
        val fechaFormateada = inspeccion.fecha?.let {
            try {
                val partes = it.split("T")
                val (anio, mes, dia) = partes[0].split("-")
                val hora = partes[1].substring(0, 5)
                "$dia/$mes/$anio  $hora"
            } catch (e: Exception) { it }
        } ?: "Sin fecha"

        holder.tvFecha.text = fechaFormateada

        // Muestra el turno calculado por Supabase.
        holder.tvTurnoInspeccion.text =
            "Turno: ${formatearTurnoInspeccion(inspeccion.turnoInspeccion)}"

        val usuario = usuarios[inspeccion.usuarioId]

        val nombreCompleto = buildString {
            append(usuario?.nombre ?: "Usuario")
            append(" ")
            append(usuario?.apellidos ?: "desconocido")
        }

        holder.tvInspectorResumen.text = "$nombreCompleto · ${usuario?.numeroEmpleado ?: "Sin nº empleado"}"

        // Indicador visual de resultado de la inspección
        holder.tvConFod.text = if (inspeccion.conFod) "⚠️ Con FOD" else "✅ Sin FOD"

        holder.itemView.setOnClickListener { onItemClick(inspeccion) }
    }

    /**
     * Convierte el valor técnico de Supabase en texto legible.
     */
    private fun formatearTurnoInspeccion(turno: String?): String {
        return when (turno) {
            "manana" -> "Mañana"
            "tarde" -> "Tarde"
            "noche" -> "Noche"
            "cuarto_turno" -> "Cuarto turno"
            else -> "No registrado"
        }
    }

    /** Devuelve el número total de inspecciones de la lista. */
    override fun getItemCount() = inspecciones.size
}