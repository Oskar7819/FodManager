package com.example.fodmanager.ui.incidencias

import android.content.Intent
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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.IncidenciaFod
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Payload mínimo para actualizar el estado de una incidencia FOD.
 */
@Serializable
data class UpdateEstadoIncidenciaPayload(
    val estado: String,
    @SerialName("fecha_cierre") val fechaCierre: String? = null
)

/**
 * Proyección del usuario logueado con los campos necesarios para esta pantalla.
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
 * Mejoras aplicadas:
 * - La imagen se carga con fitCenter en vez de centerCrop.
 * - Así se evita recortar la fotografía y se ve mejor en la tablet o pantalla grande.
 * - Al pulsar sobre la imagen, se abre una nueva pantalla para verla a tamaño completo.
 * - En la pantalla completa se podrá hacer zoom con dedos mediante PhotoView.
 */
class DetalleIncidenciaActivity : AppCompatActivity() {

    // Estado actual de la incidencia
    private lateinit var tvDetalleEstado: TextView

    // Información de aeronave, zona y tipo de FOD
    private lateinit var tvDetalleAeronave: TextView
    private lateinit var tvDetalleZona: TextView
    private lateinit var tvDetalleTipoFod: TextView

    // Fechas y duración
    private lateinit var tvFechaDeteccion: TextView
    private lateinit var tvFechaCierre: TextView
    private lateinit var tvDiasAbierta: TextView

    // Datos del declarante
    private lateinit var tvDetalleDeclarante: TextView
    private lateinit var tvDetalleNumeroEmpleado: TextView

    // Descripción
    private lateinit var tvDetalleDescripcion: TextView

    // Imagen de la incidencia
    private lateinit var imgDetalleIncidencia: ImageView

    // Contenedor de botones de estado
    private lateinit var layoutBotonesEstado: LinearLayout

    // Botones de cambio de estado
    private lateinit var btnPasarEnProceso: Button
    private lateinit var btnCerrarIncidencia: Button

    // Prioridad
    private lateinit var tvDetallePrioridad: TextView

    // ID de la incidencia
    private var incidenciaId: Int = -1

    /**
     * Roles que pueden cambiar el estado de una incidencia.
     */
    private val rolesConPermiso = listOf("administrador", "mando_gp4", "quality")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Asigna el layout de la activity
        setContentView(R.layout.activity_detalle_incidencia)

        // Configura ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle Incidencia FOD"

        // Inicialización de vistas
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

        // Recupera el ID de la incidencia
        incidenciaId = intent.getIntExtra("incidencia_id", -1)

        if (incidenciaId == -1) {
            Toast.makeText(this, "Incidencia no válida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Acciones de botones
        btnPasarEnProceso.setOnClickListener { actualizarEstado("en_proceso") }
        btnCerrarIncidencia.setOnClickListener { actualizarEstado("cerrada") }

        // Carga inicial del detalle
        cargarDetalle()
    }

    /**
     * Carga la incidencia, el usuario logueado, el declarante y la aeronave.
     */
    private fun cargarDetalle() {
        lifecycleScope.launch {
            try {
                val incidencia = supabase.postgrest["incidencias_fod"]
                    .select {
                        filter {
                            eq("id", incidenciaId)
                        }
                    }
                    .decodeList<IncidenciaFod>()
                    .firstOrNull()

                if (incidencia == null) {
                    Toast.makeText(
                        this@DetalleIncidenciaActivity,
                        "No se encontró la incidencia",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@launch
                }

                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                val usuarioActual = if (email.isNotBlank()) {
                    supabase.postgrest["usuarios"]
                        .select {
                            filter {
                                eq("email", email)
                            }
                        }
                        .decodeList<UsuarioDetalleIncidencia>()
                        .firstOrNull()
                } else {
                    null
                }

                val declarante = incidencia.usuarioId?.let { userId ->
                    supabase.postgrest["usuarios"]
                        .select {
                            filter {
                                eq("id", userId)
                            }
                        }
                        .decodeList<Usuario>()
                        .firstOrNull()
                }

                val aeronave = incidencia.aeronaveId?.let { aeronaveId ->
                    supabase.postgrest["aeronaves"]
                        .select {
                            filter {
                                eq("id", aeronaveId)
                            }
                        }
                        .decodeList<Aeronave>()
                        .firstOrNull()
                }

                pintarIncidencia(incidencia, declarante, aeronave)
                configurarBotonesSegunRolYEstado(
                    rol = usuarioActual?.rol.orEmpty(),
                    estado = incidencia.estado.orEmpty()
                )

            } catch (e: Exception) {
                Toast.makeText(
                    this@DetalleIncidenciaActivity,
                    "Error cargando incidencia: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Rellena todas las vistas con los datos de la incidencia.
     */
    private fun pintarIncidencia(
        incidencia: IncidenciaFod,
        declarante: Usuario?,
        aeronave: Aeronave?
    ) {
        val estado = incidencia.estado ?: ""

        tvDetalleEstado.text = when (estado) {
            "abierta" -> "🔴 Abierta"
            "en_proceso" -> "🟡 En proceso"
            "cerrada" -> "🟢 Cerrada"
            else -> if (estado.isBlank()) "Estado no disponible" else estado
        }

        tvDetalleAeronave.text =
            "Aeronave: ${aeronave?.modelo ?: "Sin modelo"} - ${aeronave?.numeroSerie ?: "Sin serie"}"

        tvDetalleZona.text = "Zona: ${incidencia.zonaAvion ?: "No especificada"}"
        tvDetalleTipoFod.text = "Tipo FOD: ${traducirTipoFod(incidencia.tipoFod)}"
        tvDetallePrioridad.text = "Prioridad: ${traducirPrioridad(incidencia.prioridad)}"
        tvFechaDeteccion.text = "Fecha de detección: ${formatearFechaHora(incidencia.createdAt)}"
        tvFechaCierre.text =
            "Fecha de cierre: ${incidencia.fechaCierre?.let { formatearFechaHora(it) } ?: "No cerrada"}"
        tvDiasAbierta.text = calcularTextoDuracion(incidencia.createdAt, incidencia.fechaCierre)

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
            // Si existe imagen, se hace visible el ImageView
            imgDetalleIncidencia.visibility = View.VISIBLE

            // Carga de la imagen con Glide
            Glide.with(this)
                .load(incidencia.imagenUrl)
                .fitCenter()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(imgDetalleIncidencia)

            // Al pulsar la imagen, se abre la pantalla de imagen completa
            imgDetalleIncidencia.setOnClickListener {
                val intent = Intent(this, ImagenCompletaActivity::class.java)

                // Se envía la URL de la imagen a la activity de pantalla completa
                intent.putExtra("imagen_url", incidencia.imagenUrl)

                startActivity(intent)
            }
        } else {
            // Si no existe imagen, se oculta el ImageView
            imgDetalleIncidencia.visibility = View.GONE

            // Se elimina el click por seguridad
            imgDetalleIncidencia.setOnClickListener(null)
        }
    }

    /**
     * Muestra u oculta los botones según el rol y el estado actual.
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

            else -> {
                layoutBotonesEstado.visibility = View.GONE
            }
        }
    }

    /**
     * Actualiza el estado de la incidencia y recarga el detalle.
     */
    private fun actualizarEstado(nuevoEstado: String) {
        lifecycleScope.launch {
            try {
                val fechaCierre =
                    if (nuevoEstado == "cerrada") OffsetDateTime.now().toString() else null

                supabase.postgrest["incidencias_fod"]
                    .update(
                        UpdateEstadoIncidenciaPayload(
                            estado = nuevoEstado,
                            fechaCierre = fechaCierre
                        )
                    ) {
                        filter {
                            eq("id", incidenciaId)
                        }
                    }

                Toast.makeText(
                    this@DetalleIncidenciaActivity,
                    "Estado actualizado",
                    Toast.LENGTH_SHORT
                ).show()

                cargarDetalle()

            } catch (e: Exception) {
                Toast.makeText(
                    this@DetalleIncidenciaActivity,
                    "Error actualizando estado: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Calcula el tiempo que la incidencia lleva abierta o estuvo abierta.
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
     * Intenta convertir una fecha ISO a LocalDateTime.
     */
    private fun parseFecha(fechaIso: String?): LocalDateTime? {
        return try {
            if (fechaIso.isNullOrBlank()) return null
            OffsetDateTime.parse(fechaIso).toLocalDateTime()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(fechaIso)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Formatea la fecha a dd/MM/yyyy HH:mm.
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
     * Traduce el valor interno de tipo_fod a un texto legible.
     */
    private fun traducirTipoFod(tipo: String?): String {
        return when (tipo) {
            "ambiental" -> "Ambiental"
            "herramientas" -> "Herramientas"
            "restos_metalicos" -> "Restos metálicos"
            "material_consumo" -> "Material de consumo"
            "personal" -> "Personal"
            "procedente_aeronave" -> "Procedente de aeronave"
            null -> "No especificado"
            else -> tipo
        }
    }

    /**
     * Traduce la prioridad a texto visible.
     */
    private fun traducirPrioridad(prioridad: String?): String {
        return when (prioridad) {
            "baja" -> "🟢 Baja"
            "alta" -> "🔴 Alta"
            null -> "No especificada"
            else -> prioridad
        }
    }

    /**
     * Gestiona el botón atrás de la ActionBar.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}