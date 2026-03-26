package com.example.fodmanager.ui.inspecciones

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload para el INSERT en la tabla inspecciones.
 * `fecha` la genera Supabase automáticamente al insertar el registro.
 */
@Serializable
data class InsertInspeccionPayload(
    @SerialName("usuario_id") val usuarioId: Int,
    val zona: String,
    val observaciones: String,
    @SerialName("con_fod") val conFod: Boolean,
    @SerialName("aeronave_id") val aeronaveId: Int?,
    @SerialName("ubicacion_aeronave") val ubicacionAeronave: String?
)

/**
 * Proyección del usuario logueado con los campos necesarios para
 * identificar al inspector y obtener la aeronave asignada.
 */
@Serializable
data class UsuarioInspeccionActual(
    val id: Int,
    val nombre: String,
    val apellidos: String,
    @SerialName("numero_empleado") val numeroEmpleado: String? = null,
    @SerialName("aeronave_id") val aeronaveId: Int? = null
)

/**
 * Proyección mínima de la aeronave asignada al inspector.
 * Solo se necesitan los datos de presentación y la ubicación.
 */
@Serializable
data class AeronaveAsignadaInspeccion(
    val id: Int,
    val modelo: String,
    @SerialName("numero_serie") val numeroSerie: String? = null,
    val ubicacion: String? = null
)

/**
 * Activity con el formulario para registrar una nueva inspección FOD.
 *
 * El inspector ve automáticamente sus datos (nombre, apellidos, nº empleado)
 * y los de su aeronave asignada (modelo, nº serie, ubicación en hangar).
 * Solo puede seleccionar la zona y añadir observaciones; el resto de campos
 * se rellenan automáticamente.
 *
 * Regla de zona única diaria:
 * Antes de guardar se comprueba si esa misma zona ya fue inspeccionada hoy
 * para la misma aeronave. Si existe una inspección previa, se muestra el
 * aviso "Zona inspeccionada ya" y no se guarda el duplicado.
 *
 * Si el usuario no tiene aeronave asignada, el botón Guardar se deshabilita
 * y se muestra un mensaje informativo.
 */
class NuevaInspeccionActivity : AppCompatActivity() {

    private lateinit var spinnerZona: Spinner
    private lateinit var etObservaciones: TextInputEditText
    private lateinit var cbConFod: CheckBox
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var tvInspectorNombre: TextView
    private lateinit var tvInspectorApellidos: TextView
    private lateinit var tvInspectorNumeroEmpleado: TextView
    private lateinit var tvAeronaveAsignada: TextView
    private lateinit var tvPosicionAeronave: TextView

    private var usuarioLogueado: UsuarioInspeccionActual? = null
    private var aeronaveAsignada: AeronaveAsignadaInspeccion? = null

    /** Lista de zonas de inspección obligatorias. Debe coincidir con [HomeFragment.zonasObligatorias]. */
    private val zonas = listOf(
        "COCKPIT + DRAWBRIDGE", "LMWS + ESCALERAS", "AVIONIC BAY",
        "CARGO HOLD FWD", "CARGO HOLD AFT", "CONE",
        "ENG#1", "ENG#2", "ENG#3", "ENG#4",
        "NLG", "MLG", "TOP FUSELAGE", "ZONA EXTERIOR"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_inspeccion)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nueva Inspección"

        spinnerZona = findViewById(R.id.spinnerZona)
        etObservaciones = findViewById(R.id.etObservaciones)
        cbConFod = findViewById(R.id.cbConFod)
        btnGuardar = findViewById(R.id.btnGuardar)
        progressBar = findViewById(R.id.progressBar)
        tvInspectorNombre = findViewById(R.id.tvInspectorNombre)
        tvInspectorApellidos = findViewById(R.id.tvInspectorApellidos)
        tvInspectorNumeroEmpleado = findViewById(R.id.tvInspectorNumeroEmpleado)
        tvAeronaveAsignada = findViewById(R.id.tvAeronaveAsignada)
        tvPosicionAeronave = findViewById(R.id.tvPosicionAeronave)

        configurarSpinnerZonas()
        cargarUsuarioYAeronave()

        btnGuardar.setOnClickListener { guardarInspeccion() }
    }

    /** Configura el Spinner de zonas con el listado de zonas. */
    private fun configurarSpinnerZonas() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, zonas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerZona.adapter = adapter
    }

    /**
     * Carga el usuario logueado y, si tiene aeronave asignada, carga también
     * los datos de esa aeronave para mostrarlos en la UI.
     *
     * Si el usuario no tiene aeronave asignada (`aeronaveId == null`),
     * deshabilita el botón Guardar: no se puede inspeccionar sin aeronave.
     */
    private fun cargarUsuarioYAeronave() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                val usuario = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email) } }
                    .decodeSingle<UsuarioInspeccionActual>()

                usuarioLogueado = usuario

                tvInspectorNombre.text = "Nombre: ${usuario.nombre}"
                tvInspectorApellidos.text = "Apellidos: ${usuario.apellidos}"
                tvInspectorNumeroEmpleado.text = "Nº empleado: ${usuario.numeroEmpleado ?: "No especificado"}"

                if (usuario.aeronaveId == null) {
                    tvAeronaveAsignada.text = "Aeronave: Sin adscripción"
                    tvPosicionAeronave.text = "Posición: Sin adscripción"
                    btnGuardar.isEnabled = false
                    Toast.makeText(this@NuevaInspeccionActivity, "No tienes una aeronave adscrita.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val aeronave = supabase.postgrest["aeronaves"]
                    .select { filter { eq("id", usuario.aeronaveId) } }
                    .decodeSingle<AeronaveAsignadaInspeccion>()

                aeronaveAsignada = aeronave
                tvAeronaveAsignada.text = "Aeronave: ${aeronave.modelo} - ${aeronave.numeroSerie ?: "Sin nº serie"}"
                tvPosicionAeronave.text = "Posición: ${aeronave.ubicacion ?: "No especificada en sistema"}"

            } catch (e: Exception) {
                Toast.makeText(this@NuevaInspeccionActivity, "Error cargando usuario/aeronave: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Valida los datos e inserta la inspección en Supabase.
     *
     * Validación de zona única diaria:
     * Antes del INSERT consulta si ya existe una inspección de la misma zona
     * para la misma aeronave en el día actual (rango 00:00:00–23:59:59).
     * Si existe, muestra "Zona inspeccionada ya" y cancela el guardado.
     *
     * Si la inserción tiene éxito devuelve RESULT_OK para que
     * InspeccionesFragment recargue la lista.
     */
    private fun guardarInspeccion() {
        val usuario = usuarioLogueado ?: run {
            Toast.makeText(this, "No se ha podido cargar el inspector", Toast.LENGTH_SHORT).show()
            return
        }
        val aeronave = aeronaveAsignada ?: run {
            Toast.makeText(this, "No tienes aeronave asignada", Toast.LENGTH_SHORT).show()
            return
        }

        val zona = spinnerZona.selectedItem?.toString().orEmpty()
        val observaciones = etObservaciones.text?.toString()?.trim().orEmpty()
        val conFod = cbConFod.isChecked

        if (zona.isBlank()) {
            Toast.makeText(this, "Selecciona una zona", Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                val hoy = java.time.LocalDate.now().toString()

                // Comprobación de zona ya inspeccionada hoy para evitar duplicados
                val inspeccionesExistentes = supabase.postgrest["inspecciones"]
                    .select {
                        filter {
                            eq("aeronave_id", aeronave.id)
                            eq("zona", zona)
                            gte("fecha", "${hoy}T00:00:00")
                            lte("fecha", "${hoy}T23:59:59")
                        }
                    }
                    .decodeList<Inspeccion>()

                if (inspeccionesExistentes.isNotEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@NuevaInspeccionActivity, "Zona inspeccionada ya", Toast.LENGTH_SHORT).show()
                        btnGuardar.isEnabled = true
                        progressBar.isVisible = false
                    }
                    return@launch
                }

                supabase.postgrest["inspecciones"].insert(
                    InsertInspeccionPayload(
                        usuarioId = usuario.id,
                        zona = zona,
                        observaciones = observaciones,
                        conFod = conFod,
                        aeronaveId = aeronave.id,
                        ubicacionAeronave = aeronave.ubicacion
                    )
                )

                runOnUiThread {
                    Toast.makeText(this@NuevaInspeccionActivity, "Inspección guardada", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NuevaInspeccionActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    btnGuardar.isEnabled = true
                    progressBar.isVisible = false
                }
            }
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