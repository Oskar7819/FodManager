package com.example.fodmanager.ui.incidencias

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.IncidenciaFod
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Payload mínimo para actualizar el estado de una incidencia FOD en Supabase.
 * Se incluye fechaCierre para registrarla automáticamente al cerrar la incidencia.
 */
@Serializable
data class UpdateEstadoIncidenciaPayload(
    val estado: String,
    @SerialName("fecha_cierre") val fechaCierre: String? = null
)

/**
 * Proyección del usuario logueado con los campos necesarios para esta pantalla:
 * verificar permisos (rol) e identificar al declarante.
 */
@Serializable
data class UsuarioDetalleIncidencia(
    val id: Int,
    val nombre: String,
    val apellidos: String,
    val rol: String,
    @SerialName("numero_empleado") val numeroEmpleado: String? = null
)

/**
 * Activity que muestra el detalle completo de una incidencia FOD.
 *
 * Información mostrada:
 * - Estado actual con indicador de color.
 * - Aeronave, zona y tipo de FOD.
 * - Fechas de detección y cierre, y duración total.
 * - Nombre, apellidos y número de empleado del declarante.
 * - Descripción e imagen (si existe, cargada con Glide desde Supabase Storage).
 *
 * Gestión del estado:
 * Los botones "Pasar a en proceso" y "Cerrar incidencia" solo son visibles para
 * rolesConPermiso y cuando la incidencia no está ya cerrada.
 * Al cerrar se registra automáticamente la fechaCierre con el instante actual.
 * Tras cada cambio de estado se recarga el detalle para reflejar el nuevo estado.
 *
 * Flujo de navegación:
 * El ID de la incidencia llega como extra del Intent enviado desde IncidenciasFragment
 * o DetalleInspeccionActivity.
 */
class DetalleIncidenciaActivity : AppCompatActivity() {

    private lateinit var tvDetalleEstado: TextView
    private lateinit var tvDetalleAeronave: TextView
    private lateinit var tvDetalleZona: TextView
    private lateinit var tvDetalleTipoFod: TextView

    private lateinit var tvFechaDeteccion: TextView
    private lateinit var tvFechaCierre: TextView
    private lateinit var tvDiasAbierta: TextView

    private lateinit var tvDetalleDeclarante: TextView
    private lateinit var tvDetalleNumeroEmpleado: TextView
    private lateinit var tvDetalleDescripcion: TextView

    private lateinit var imgDetalleIncidencia: ImageView

    private lateinit var layoutBotonesEstado: LinearLayout
    private lateinit var btnPasarEnProceso: Button
    private lateinit var btnCerrarIncidencia: Button

    private lateinit var tvDetallePrioridad: TextView

    private var incidenciaId: Int = -1

    /** Roles que pueden cambiar el estado de una incidencia. */
    private val rolesConPermiso = listOf("administrador", "mando_gp4", "quality")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_incidencia)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle Incidencia FOD"

        tvDetalleEstado = findViewById(R.id.tvDetalleEstado)
        tvDetalleAeronave = findViewById(R.id.tvDetalleAeronave)
        tvDetalleZona = findViewById(R.id.tvDetalleZona)
        tvDetalleTipoFod = findViewById(R.id.tvDetalleTipoFod)
        tvFechaDeteccion = findViewById(R.id.tvFechaDeteccion)
        tvFechaCierre = findViewById(R.id.tvFechaCierre)
        tvDiasAbierta = findViewById(R.id.tvDiasAbierta)
        tvDetalleDeclarante = findViewById(R.id.tvDetalleDeclarante)
        tvDetalleNumeroEmpleado = findViewById(R.id.tvDetalleNumeroEmpleado)
        tvDetalleDescripcion = findViewById(R.id.tvDetalleDescripcion)
        imgDetalleIncidencia = findViewById(R.id.imgDetalleIncidencia)
        layoutBotonesEstado = findViewById(R.id.layoutBotonesEstado)
        btnPasarEnProceso = findViewById(R.id.btnPasarEnProceso)
        btnCerrarIncidencia = findViewById(R.id.btnCerrarIncidencia)
        tvDetallePrioridad = findViewById(R.id.tvDetallePrioridad)

        incidenciaId = intent.getIntExtra("incidencia_id", -1)

        if (incidenciaId == -1) {
            Toast.makeText(this, "Incidencia no válida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnPasarEnProceso.setOnClickListener { actualizarEstado("en_proceso") }
        btnCerrarIncidencia.setOnClickListener { actualizarEstado("cerrada") }

        cargarDetalle()
    }

    /**
     * Carga desde Supabase todos los datos necesarios para mostrar el detalle:
     * la incidencia, el usuario logueado (para permisos), el declarante y la aeronave.
     * Si la incidencia no existe, cierra la activity.
     */
    private fun cargarDetalle() {
        lifecycleScope.launch {
            try {
                val incidencia = supabase.postgrest["incidencias_fod"]
                    .select { filter { eq("id", incidenciaId) } }
                    .decodeList<IncidenciaFod>()
                    .firstOrNull()

                if (incidencia == null) {
                    Toast.makeText(this@DetalleIncidenciaActivity, "No se encontró la incidencia", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                // Usuario logueado: necesario para verificar el rol y mostrar/ocultar botones
                val usuarioActual = if (email.isNotBlank()) {
                    supabase.postgrest["usuarios"]
                        .select { filter { eq("email", email) } }
                        .decodeList<UsuarioDetalleIncidencia>()
                        .firstOrNull()
                } else null

                // Declarante: quien registró la incidencia (puede ser distinto del usuario logueado)
                val declarante = incidencia.usuarioId?.let { userId ->
                    supabase.postgrest["usuarios"]
                        .select { filter { eq("id", userId) } }
                        .decodeList<Usuario>()
                        .firstOrNull()
                }

                val aeronave = incidencia.aeronaveId?.let { aeronaveId ->
                    supabase.postgrest["aeronaves"]
                        .select { filter { eq("id", aeronaveId) } }
                        .decodeList<Aeronave>()
                        .firstOrNull()
                }

                pintarIncidencia(incidencia, declarante, aeronave)
                configurarBotonesSegunRolYEstado(
                    rol = usuarioActual?.rol.orEmpty(),
                    estado = incidencia.estado.orEmpty()
                )

            } catch (e: Exception) {
                Toast.makeText(this@DetalleIncidenciaActivity, "Error cargando incidencia: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Rellena todas las vistas del layout con los datos de incidencia.
     * Si incidencia tiene URL de imagen, la carga con Glide; si no, oculta el ImageView.
     */
    private fun pintarIncidencia(
        incidencia: IncidenciaFod,
        declarante: Usuario?,
        aeronave: Aeronave?
    ) {
        val estado = incidencia.estado ?: ""

        tvDetalleEstado.text = when (estado) {
            "abierta"    -> "🔴 Abierta"
            "en_proceso" -> "🟡 En proceso"
            "cerrada"    -> "🟢 Cerrada"
            else         -> if (estado.isBlank()) "Estado no disponible" else estado
        }

        tvDetalleAeronave.text =
            "Aeronave: ${aeronave?.modelo ?: "Sin modelo"} - ${aeronave?.numeroSerie ?: "Sin serie"}"

        tvDetalleZona.text = "Zona: ${incidencia.zonaAvion ?: "No especificada"}"
        tvDetalleTipoFod.text = "Tipo FOD: ${traducirTipoFod(incidencia.tipoFod)}"
        tvDetallePrioridad.text = "Prioridad: ${traducirPrioridad(incidencia.prioridad)}"
        tvFechaDeteccion.text = "Fecha de detección: ${formatearFechaHora(incidencia.createdAt)}"
        tvFechaCierre.text = "Fecha de cierre: ${incidencia.fechaCierre?.let { formatearFechaHora(it) } ?: "No cerrada"}"
        tvDiasAbierta.text = calcularTextoDuracion(incidencia.createdAt, incidencia.fechaCierre)

        // Construye el nombre del declarante ignorando partes vacías
        val nombreDeclarante = listOfNotNull(
            declarante?.nombre?.takeIf { it.isNotBlank() },
            declarante?.apellidos?.takeIf { it.isNotBlank() }
        ).joinToString(" ")

        tvDetalleDeclarante.text =
            "Declarante: ${if (nombreDeclarante.isNotBlank()) nombreDeclarante else "Usuario desconocido"}"
        tvDetalleNumeroEmpleado.text =
            "Nº empleado: ${declarante?.numeroEmpleado ?: "No especificado"}"
        tvDetalleDescripcion.text = incidencia.descripcion ?: "Sin descripción"

        if (!incidencia.imagenUrl.isNullOrBlank()) {
            imgDetalleIncidencia.visibility = View.VISIBLE
            Glide.with(this).load(incidencia.imagenUrl).centerCrop().into(imgDetalleIncidencia)
        } else {
            imgDetalleIncidencia.visibility = View.GONE
        }
    }

    /**
     * Muestra u oculta los botones de cambio de estado según el rol del usuario
     * y el estado actual de la incidencia.
     *
     * Reglas:
     * - Si el rol no tiene permiso o la incidencia está cerrada → ocultar ambos botones.
     * - Estado "abierta" → mostrar ambos botones.
     * - Estado "en_proceso" → mostrar solo "Cerrar incidencia".
     * - Cualquier otro estado → ocultar el contenedor.
     */
    private fun configurarBotonesSegunRolYEstado(rol: String, estado: String) {
        if (rol !in rolesConPermiso || estado == "cerrada") {
            layoutBotonesEstado.visibility = View.GONE
            return
        }

        layoutBotonesEstado.visibility = View.VISIBLE

        when (estado) {
            "abierta" -> {
                btnPasarEnProceso.visibility = View.VISIBLE
                btnCerrarIncidencia.visibility = View.VISIBLE
            }
            "en_proceso" -> {
                btnPasarEnProceso.visibility = View.GONE
                btnCerrarIncidencia.visibility = View.VISIBLE
            }
            else -> layoutBotonesEstado.visibility = View.GONE
        }
    }

    /**
     * Actualiza el estado de la incidencia en Supabase y recarga el detalle.
     * Si nuevoEstado es "cerrada", se registra la fechaCierre con el instante actual.
     */
    private fun actualizarEstado(nuevoEstado: String) {
        lifecycleScope.launch {
            try {
                val fechaCierre = if (nuevoEstado == "cerrada") OffsetDateTime.now().toString() else null

                supabase.postgrest["incidencias_fod"]
                    .update(UpdateEstadoIncidenciaPayload(estado = nuevoEstado, fechaCierre = fechaCierre)) {
                        filter { eq("id", incidenciaId) }
                    }

                Toast.makeText(this@DetalleIncidenciaActivity, "Estado actualizado", Toast.LENGTH_SHORT).show()

                // Recarga el detalle para que la UI refleje el nuevo estado y los botones correctos
                cargarDetalle()

            } catch (e: Exception) {
                Toast.makeText(this@DetalleIncidenciaActivity, "Error actualizando estado: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Calcula y formatea la duración de la incidencia como texto.
     * - Si sigue abierta (sin [fechaCierre]): "Días abierta: N días".
     * - Si está cerrada: "Tiempo abierta: N días".
     */
    private fun calcularTextoDuracion(createdAt: String?, fechaCierre: String?): String {
        val inicio = parseFecha(createdAt) ?: return "Días abierta: no disponible"
        val fin = parseFecha(fechaCierre) ?: OffsetDateTime.now().toLocalDateTime()
        val dias = Duration.between(inicio, fin).toDays().coerceAtLeast(0)

        return if (fechaCierre.isNullOrBlank()) {
            if (dias == 1L) "Días abierta: 1 día" else "Días abierta: $dias días"
        } else {
            if (dias == 1L) "Tiempo abierta: 1 día" else "Tiempo abierta: $dias días"
        }
    }

    /**
     * Parsea una fecha ISO 8601 a LocalDateTime.
     * Intenta primero con offset (OffsetDateTime) y luego sin él.
     * Devuelve null si la cadena es nula, vacía o no parseable.
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
     * Traduce el código interno del tipo de FOD a texto legible en español.
     * Devuelve el propio código si no coincide con ninguno conocido.
     */
    private fun traducirTipoFod(tipo: String?): String {
        return when (tipo) {
            "ambiental"           -> "Ambiental"
            "herramientas"        -> "Herramientas"
            "restos_metalicos"    -> "Restos metálicos"
            "material_consumo"    -> "Material de consumo"
            "personal"            -> "Personal"
            "procedente_aeronave" -> "Procedente de aeronave"
            null                  -> "No especificado"
            else                  -> tipo
        }
    }

    /**
     * Traduce el código interno de prioridad a texto legible con indicador visual.
     * Devuelve "No especificada" si el campo es null.
     */
    private fun traducirPrioridad(prioridad: String?): String {
        return when (prioridad) {
            "baja"    -> "🟢 Baja"
            "alta"    -> "🔴 Alta"
            null      -> "No especificada"
            else   -> prioridad ?: ""
        }
    }

    /** Gestiona el botón de atrás de la ActionBar. */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}