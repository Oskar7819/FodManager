package com.example.fodmanager.ui.inspecciones

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R

/**
 * Adapter que conecta la lista de ResumenInspeccionDiaria con el RecyclerView de
 * ResumenInspeccionesDiariasActivity.
 *
 * Cada tarjeta muestra:
 * - Fecha y nombre de la aeronave.
 * - Estado de completitud (zonas inspeccionadas vs. obligatorias).
 * - Estado FOD (si se detectó alguno).
 * - Sección de zonas inspeccionadas (siempre visible).
 * - Sección de zonas pendientes (solo si hay pendientes).
 * - Sección de zonas con FOD (solo si hay FOD).
 *
 * Color de fondo semafórico:
 * - 🔴 Rojo suave   se detectó FOD.
 * - 🟠 Naranja suave  inspección incompleta (sin FOD).
 * - 🟢 Verde suave  →inspección completa y sin FOD.
 *
 * @param resumenes Lista de resúmenes diarios a mostrar (no mutable: el adapter no la modifica).
 */
class ResumenInspeccionesDiariasAdapter(
    private val resumenes: List<ResumenInspeccionDiaria>
) : RecyclerView.Adapter<ResumenInspeccionesDiariasAdapter.ViewHolder>() {

    /**
     * Almacena referencias a las vistas del layout item_resumen_inspeccion_dia
     * para evitar llamadas repetidas a View.findViewById en cada reciclaje.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: androidx.cardview.widget.CardView = view.findViewById(R.id.cardRoot)
        val tvFecha: TextView = view.findViewById(R.id.tvFechaResumen)

        val tvTurno: TextView = view.findViewById(R.id.tvTurnoResumen)
        val tvAeronave: TextView = view.findViewById(R.id.tvAeronaveResumen)
        val tvEstado: TextView = view.findViewById(R.id.tvEstadoResumen)
        val tvFod: TextView = view.findViewById(R.id.tvFodResumen)

        val tvTituloInspeccionadas: TextView = view.findViewById(R.id.tvTituloInspeccionadas)
        val llInspeccionadas: LinearLayout = view.findViewById(R.id.llInspeccionadas)

        val tvTituloPendientes: TextView = view.findViewById(R.id.tvTituloPendientes)
        val llPendientes: LinearLayout = view.findViewById(R.id.llPendientes)

        val tvTituloFod: TextView = view.findViewById(R.id.tvTituloFod)
        val llFod: LinearLayout = view.findViewById(R.id.llFod)
    }

    // Infla el layout item_resumen_inspeccion_dia y lo envuelve en un [ViewHolder].
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_resumen_inspeccion_dia, parent, false)
        return ViewHolder(view)
    }

    /**
     * Rellena la tarjeta con los datos del ResumenInspeccionDiaria en position.
     *
     * La sección de zonas inspeccionadas siempre es visible.
     * Las secciones de zonas pendientes y con FOD se muestran u ocultan dinámicamente
     * según si tienen contenido, para no mostrar secciones vacías.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = resumenes[position]
        val context = holder.itemView.context

        holder.tvFecha.text = "Fecha: ${formatearSoloFecha(item.fechaDia)}"
        holder.tvTurno.text = "🕒 Turno: ${formatearTurnoInspeccion(item.turnoInspeccion)}"
        holder.tvAeronave.text = item.aeronaveTexto

        // Estado de completitud: cuántas de las 14 zonas obligatorias se inspeccionaron
        holder.tvEstado.text = if (item.completa) {
            "✅ Inspección de turno completa (${item.zonasInspeccionadas.size}/14 zonas)"
        } else {
            "⚠️ Inspección de turno incompleta (${item.zonasInspeccionadas.size}/14 zonas)"
        }

        holder.tvFod.text = if (item.hayFod) {
            "🚨 Se detectó FOD en ${item.zonasConFod.size} zona(s)"
        } else {
            "🟢 Sin FOD en esta inspección de turno"
        }

        // Color semafórico del fondo de la tarjeta
        holder.cardRoot.setCardBackgroundColor(when {
            item.hayFod   -> Color.parseColor("#FFEBEE") // rojo suave
            !item.completa -> Color.parseColor("#FFF3E0") // naranja suave
            else           -> Color.parseColor("#E8F5E9") // verde suave
        })

        // Sección Zonas inspeccionadas: siempre visible
        holder.llInspeccionadas.removeAllViews()
        holder.tvTituloInspeccionadas.visibility = View.VISIBLE
        holder.llInspeccionadas.visibility = View.VISIBLE
        item.zonasInspeccionadas.forEach { zona ->
            holder.llInspeccionadas.addView(
                crearLinea(context, "☑ $zona", ContextCompat.getColor(context, android.R.color.black))
            )
        }

        // Sección Zonas pendientes: visible solo si hay zonas sin inspeccionar
        if (item.zonasPendientes.isNotEmpty()) {
            holder.tvTituloPendientes.visibility = View.VISIBLE
            holder.llPendientes.visibility = View.VISIBLE
            holder.llPendientes.removeAllViews()
            item.zonasPendientes.forEach { zona ->
                holder.llPendientes.addView(
                    crearLinea(context, "☐ $zona", Color.parseColor("#F57C00"))
                )
            }
        } else {
            holder.tvTituloPendientes.visibility = View.GONE
            holder.llPendientes.visibility = View.GONE
        }

        // Sección "Zonas con FOD": visible solo si se detectó FOD en alguna zona
        if (item.zonasConFod.isNotEmpty()) {
            holder.tvTituloFod.visibility = View.VISIBLE
            holder.llFod.visibility = View.VISIBLE
            holder.llFod.removeAllViews()
            item.zonasConFod.forEach { zona ->
                holder.llFod.addView(
                    crearLinea(context, "🚨 $zona", ContextCompat.getColor(context, android.R.color.holo_red_dark))
                )
            }
        } else {
            holder.tvTituloFod.visibility = View.GONE
            holder.llFod.visibility = View.GONE
        }
    }

    /** Devuelve el número total de resúmenes de la lista. */
    override fun getItemCount(): Int = resumenes.size

    /**
     * Crea un TextView reutilizable para mostrar una línea de zona en cualquier sección.
     *
     * @param context Contexto necesario para crear la vista.
     * @param texto   Texto a mostrar (incluye prefijo con emoji o símbolo de checklist).
     * @param color   Color del texto, varía según la sección (negro, naranja o rojo).
     */
    private fun crearLinea(context: android.content.Context, texto: String, color: Int): TextView {
        return TextView(context).apply {
            this.text = texto
            textSize = 13f
            setTextColor(color)
            setPadding(12, 2, 0, 2)
            setLineSpacing(0f, 1.1f)
        }
    }

    /**
     * Convierte una fecha del formato "YYYY-MM-DD" al formato "dd/MM/yyyy".
     * Si el parseo falla, devuelve la cadena original sin modificar.
     */
    private fun formatearSoloFecha(fechaIso: String): String {
        return try {
            val (anio, mes, dia) = fechaIso.split("-")
            "$dia/$mes/$anio"
        } catch (e: Exception) { fechaIso }
    }

    /**
     * Convierte el valor técnico de Supabase en texto claro para el usuario.
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
}