package com.example.fodmanager.ui.inspecciones

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.ui.incidencias.NuevaIncidenciaActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Proyección del inspector (usuario que realizó la inspección)
 * con los campos necesarios para mostrarlos en el detalle.
 * Es distinto del usuario logueado: puede ser cualquier operario.
 */
@Serializable
data class UsuarioDetalleInspeccion(
    val nombre: String,
    val apellidos: String,
    @SerialName("numero_empleado") val numeroEmpleado: String? = null
)

/**
 * Proyección del usuario logueado para verificar su rol
 * y decidir si puede registrar una nueva incidencia desde esta pantalla.
 */
@Serializable
data class UsuarioRolDetalleInspeccion(
    val id: Int,
    val rol: String
)

/**
 * Activity que muestra el detalle completo de una inspección.
 *
 * Información mostrada:
 * - Zona inspeccionada.
 * - Fecha y hora formateadas.
 * - Resultado (con/sin FOD).
 * - Observaciones del inspector.
 * - Nombre, apellidos y número de empleado del inspector.
 *
 * Botón "Nueva Incidencia":
 * Solo es visible si se cumplen las dos condiciones simultáneamente:
 * 1. La inspección tiene `conFod = true`.
 * 2. El rol del usuario logueado está en rolesQuePuedenRegistrarIncidencia.
 * `focal_point_fod` y `head_plant` quedan excluidos aunque la inspección tenga FOD.
 *
 * El botón abre NuevaIncidenciaActivity pasando el ID de esta inspección como extra,
 * para que la incidencia quede correctamente vinculada.
 */
class DetalleInspeccionActivity : AppCompatActivity() {

    private lateinit var tvZona: TextView
    private lateinit var tvFecha: TextView

    // Muestra el turno operativo calculado por Supabase.
    private lateinit var tvTurnoInspeccion: TextView
    private lateinit var tvConFod: TextView
    private lateinit var tvObservaciones: TextView

    private lateinit var tvUsuario: TextView
    private lateinit var tvUsuarioApellidos: TextView
    private lateinit var tvUsuarioNumeroEmpleado: TextView

    private lateinit var btnNuevaIncidencia: Button

    private var inspeccionId: Int = -1
    private var aeronaveId: Int? = null

    /** Roles que pueden registrar incidencias FOD desde esta pantalla. */
    private val rolesQuePuedenRegistrarIncidencia = listOf("operario", "mando_gp4", "quality")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_inspeccion)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle de Inspección"

        tvZona = findViewById(R.id.tvDetalleZona)
        tvFecha = findViewById(R.id.tvDetalleFecha)

        tvTurnoInspeccion = findViewById(R.id.tvDetalleTurnoInspeccion)

        tvConFod = findViewById(R.id.tvDetalleConFod)
        tvObservaciones = findViewById(R.id.tvDetalleObservaciones)
        tvUsuario = findViewById(R.id.tvDetalleUsuario)
        tvUsuarioApellidos = findViewById(R.id.tvDetalleUsuarioApellidos)
        tvUsuarioNumeroEmpleado = findViewById(R.id.tvDetalleUsuarioNumeroEmpleado)
        btnNuevaIncidencia = findViewById(R.id.btnNuevaIncidencia)

        inspeccionId = intent.getIntExtra("inspeccion_id", -1)

        if (inspeccionId == -1) {
            Toast.makeText(this, "Error al cargar inspección", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Pasa el ID de inspección y, si existe, el de aeronave a NuevaIncidenciaActivity
        btnNuevaIncidencia.setOnClickListener {
            val intent = Intent(this, NuevaIncidenciaActivity::class.java)
            intent.putExtra("inspeccion_id", inspeccionId)
            aeronaveId?.let { intent.putExtra("aeronave_id", it) }
            startActivity(intent)
        }

        cargarDetalle(inspeccionId)
    }

    /**
     * Carga desde Supabase la inspección, el inspector y el usuario logueado,
     * y construye la UI del detalle.
     *
     * Reglas de visibilidad del botón "Nueva Incidencia":
     * - `conFod = false` → nunca se muestra, independientemente del rol.
     * - `focal_point_fod` o `head_plant` → nunca se muestra, independientemente del FOD.
     * - Resto de combinaciones: visible solo si ambas condiciones se cumplen.
     *
     * La fecha ISO se formatea de "YYYY-MM-DDTHH:mm" a "dd/MM/yyyy  HH:mm" para legibilidad.
     */
    private fun cargarDetalle(id: Int) {
        lifecycleScope.launch {
            try {
                val inspeccion = supabase.postgrest["inspecciones"]
                    .select { filter { eq("id", id) } }
                    .decodeSingle<Inspeccion>()

                aeronaveId = inspeccion.aeronaveId

                tvZona.text = "Zona: ${inspeccion.zona}"

                // Conversión de formato ISO → "dd/MM/yyyy  HH:mm"
                val fechaFormateada = inspeccion.fecha?.let {
                    try {
                        val partes = it.split("T")
                        val (anio, mes, dia) = partes[0].split("-")
                        val hora = partes[1].substring(0, 5)
                        "$dia/$mes/$anio  $hora"
                    } catch (e: Exception) {
                        it
                    }
                } ?: "Sin fecha"

                tvFecha.text = "Fecha: $fechaFormateada"

                // Muestra el turno calculado por Supabase.
                tvTurnoInspeccion.text =
                    "Turno: ${formatearTurnoInspeccion(inspeccion.turnoInspeccion)}"
                tvConFod.text = if (inspeccion.conFod) "⚠️ Con FOD" else "✅ Todo OK · Sin FOD"
                tvObservaciones.text = inspeccion.observaciones ?: "Sin observaciones"

                // Carga los datos del inspector (quien realizó la inspección)
                val inspector = supabase.postgrest["usuarios"]
                    .select { filter { eq("id", inspeccion.usuarioId) } }
                    .decodeSingle<UsuarioDetalleInspeccion>()

                tvUsuario.text = "Nombre: ${inspector.nombre}"
                tvUsuarioApellidos.text = "Apellidos: ${inspector.apellidos}"
                tvUsuarioNumeroEmpleado.text =
                    "Nº empleado: ${inspector.numeroEmpleado ?: "No especificado"}"

                // Carga el usuario logueado para determinar si puede registrar incidencia
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()
                val usuarioActual = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email) } }
                    .decodeSingle<UsuarioRolDetalleInspeccion>()

                btnNuevaIncidencia.visibility =
                    if (inspeccion.conFod && usuarioActual.rol in rolesQuePuedenRegistrarIncidencia)
                        View.VISIBLE else View.GONE

            } catch (e: Exception) {
                Toast.makeText(
                    this@DetalleInspeccionActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

        /**
         * Convierte el valor técnico guardado en Supabase
         * en un texto claro para el usuario.
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


    /** Gestiona el botón de atrás de la ActionBar. */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}