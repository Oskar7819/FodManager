package com.example.fodmanager.ui.incidencias

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.IncidenciaFod
import com.example.fodmanager.data.models.Usuario
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Adapter que conecta la lista de IncidenciaFod con el RecyclerView del fragment de incidencias.
 *
 * Recibe mapas auxiliares para enriquecer cada tarjeta sin realizar consultas extra:
 * - aeronaves: aeronave_id → "Modelo - NumSerie"
 * - usuarios:  usuario_id → Usuario completo
 *
 * @param incidencias   Lista mutable de incidencias a mostrar.
 * @param aeronaves     Mapa de aeronaves para mostrar el nombre en cada tarjeta.
 * @param usuarios      Mapa de usuarios para mostrar el declarante en cada tarjeta.
 * @param onItemClick   Lambda invocada al pulsar una tarjeta, recibe la IncidenciaFod seleccionada.
 */
class IncidenciasAdapter(
    private val incidencias: MutableList<IncidenciaFod>,
    private val aeronaves: Map<Int, String>,
    private val usuarios: Map<Int, Usuario>,
    private val onItemClick: (IncidenciaFod) -> Unit
) : RecyclerView.Adapter<IncidenciasAdapter.ViewHolder>() {

    /**
     * Almacena referencias a las vistas del layout `item_incidencia`
     * para evitar llamadas repetidas a [View.findViewById] en cada reciclaje.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAeronave: TextView = view.findViewById(R.id.tvAeronave)
        val tvEstado: TextView = view.findViewById(R.id.tvEstado)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvDeclaranteResumen: TextView = view.findViewById(R.id.tvDeclaranteResumen)
        val tvTipoFod: TextView = view.findViewById(R.id.tvTipoFod)
        val tvZona: TextView = view.findViewById(R.id.tvZona)
        val tvDescripcion: TextView = view.findViewById(R.id.tvDescripcion)

        val tvPrioridad: TextView = view.findViewById(R.id.tvPrioridad)
    }

    /** Infla el layout `item_incidencia` y lo envuelve en un [ViewHolder]. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incidencia, parent, false)
        return ViewHolder(view)
    }

    /**
     * Rellena la tarjeta en position con los datos de la incidencia correspondiente.
     *
     * Para el declarante: prioriza el número de empleado guardado en la propia incidencia;
     * si es null, usa el del perfil del usuario; si tampoco existe, muestra un placeholder.
     *
     * La línea de fecha combina la fecha de detección y la duración calculada
     * para ofrecer contexto temporal de un vistazo.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val incidencia = incidencias[position]

        holder.tvAeronave.text = incidencia.aeronaveId?.let {
            aeronaves[it] ?: "Aeronave desconocida"
        } ?: "Sin aeronave"

        holder.tvEstado.text = when (incidencia.estado) {
            "abierta"    -> "🔴 Abierta"
            "en_proceso" -> "🟡 En proceso"
            "cerrada"    -> "🟢 Cerrada"
            else         -> incidencia.estado
        }

        val usuario = incidencia.usuarioId?.let { usuarios[it] }

        val nombreCompleto = buildString {
            append(usuario?.nombre ?: "Usuario")
            append(" ")
            append(usuario?.apellidos ?: "desconocido")
        }

        // Prioridad: número en la incidencia → número en el perfil → placeholder
        val numeroEmpleado = incidencia.numeroEmpleado
            ?: usuario?.numeroEmpleado
            ?: "Sin nº empleado"

        holder.tvDeclaranteResumen.text = "$nombreCompleto · $numeroEmpleado"

        // Tipo FOD con emoji identificativo para facilitar el escaneo visual
        holder.tvTipoFod.text = when (incidencia.tipoFod) {
            "ambiental"           -> "🌫️ Ambiental"
            "herramientas"        -> "🔧 Herramientas"
            "restos_metalicos"    -> "🔩 Restos metálicos"
            "material_consumo"    -> "🧤 Material de consumo"
            "personal"            -> "👤 Personal"
            "procedente_aeronave" -> "✈️ Procedente de aeronave"
            else                  -> "Sin clasificar"
        }

        holder.tvPrioridad.text = when (incidencia.prioridad) {
            "baja"    -> "🟢 Baja"
            "alta"    -> "🔴 Alta"
            null      -> "⚪ Sin prioridad"
             else      -> incidencia.prioridad ?: ""
        }

        holder.tvZona.text = "Zona: ${incidencia.zonaAvion ?: "No especificada"}"

        // Línea de fecha: fecha de detección + duración de la incidencia
        holder.tvFecha.text = "Detectada: ${formatearFechaHora(incidencia.createdAt)} · ${calcularDuracion(incidencia.createdAt, incidencia.fechaCierre)}"

        holder.tvDescripcion.text = incidencia.descripcion

        holder.itemView.setOnClickListener { onItemClick(incidencia) }
    }

    /** Devuelve el número total de incidencias de la lista. */
    override fun getItemCount() = incidencias.size

    /**
     * Parsea una fecha ISO 8601 a [LocalDateTime].
     * Intenta primero con offset ([OffsetDateTime]) y luego sin él.
     * Devuelve null si la cadena es nula, vacía o con formato no reconocido.
     */
    private fun parseFecha(fechaIso: String?): LocalDateTime? {
        return try {
            if (fechaIso.isNullOrBlank()) return null
            OffsetDateTime.parse(fechaIso).toLocalDateTime()
        } catch (e: Exception) {
            try { LocalDateTime.parse(fechaIso) } catch (_: Exception) { null }
        }
    }

    /**
     * Formatea una fecha ISO 8601 al patrón "dd/MM/yyyy HH:mm".
     * Devuelve "No disponible" si la cadena es nula o no parseable.
     */
    private fun formatearFechaHora(fechaIso: String?): String {
        return try {
            parseFecha(fechaIso)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                ?: "No disponible"
        } catch (e: Exception) {
            fechaIso ?: "No disponible"
        }
    }

    /**
     * Calcula cuántos días lleva o estuvo abierta la incidencia.
     * - Si fechaCierre es null → la incidencia sigue abierta; se mide hasta ahora.
     * - Si fechaCierre tiene valor → incidencia cerrada; se mide hasta ese instante.
     *
     * Devuelve un texto compacto como "abierta 3 días" para incluir en la tarjeta.
     */
    private fun calcularDuracion(createdAt: String?, fechaCierre: String?): String {
        val inicio = parseFecha(createdAt) ?: return "duración no disponible"
        val fin = parseFecha(fechaCierre) ?: OffsetDateTime.now().toLocalDateTime()
        val dias = Duration.between(inicio, fin).toDays().coerceAtLeast(0)

        return if (dias == 1L) "abierta 1 día" else "abierta $dias días"
    }
}