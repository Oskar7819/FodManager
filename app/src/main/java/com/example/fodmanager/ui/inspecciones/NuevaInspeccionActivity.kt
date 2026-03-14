package com.example.fodmanager.ui.inspecciones

import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Clase de datos que representa la estructura del INSERT en la tabla "inspecciones" de Supabase.
// Contiene todos los campos necesarios para registrar una nueva inspección.
// El ID y la fecha los genera Supabase automáticamente.
@Serializable
data class NuevaInspeccion(
    val usuario_id: Int,
    val zona: String,
    val observaciones: String,
    // Indica si se encontró algún objeto FOD durante la inspección
    val con_fod: Boolean,
    // ID de la aeronave inspeccionada (puede ser null)
    val aeronave_id: Int?,
    // Ubicación física de la aeronave dentro del hangar en el momento de la inspección
    val ubicacion_aeronave: String?
)

// Activity que muestra el formulario para registrar una nueva inspección FOD.
// El inspector selecciona la aeronave, la zona, indica si hay FOD y añade observaciones.
class NuevaInspeccionActivity : AppCompatActivity() {

    private lateinit var etZona: TextInputEditText
    private lateinit var etObservaciones: TextInputEditText
    private lateinit var etUbicacionAeronave: TextInputEditText
    private lateinit var cbConFod: CheckBox
    private lateinit var spinnerAeronave: Spinner
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar

    // Lista de aeronaves cargadas desde Supabase para mostrar en el Spinner
    private val aeronaves = mutableListOf<Aeronave>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_inspeccion)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nueva Inspección"

        // Inicialización de los elementos visuales del layout
        etZona = findViewById(R.id.etZona)
        etObservaciones = findViewById(R.id.etObservaciones)
        etUbicacionAeronave = findViewById(R.id.etUbicacionAeronave)
        cbConFod = findViewById(R.id.cbConFod)
        spinnerAeronave = findViewById(R.id.spinnerAeronave)
        btnGuardar = findViewById(R.id.btnGuardar)
        progressBar = findViewById(R.id.progressBar)

        // Carga las aeronaves disponibles antes de mostrar el formulario
        cargarAeronaves()
        btnGuardar.setOnClickListener { guardarInspeccion() }
    }

    // Carga las aeronaves desde Supabase y las muestra en el Spinner
    private fun cargarAeronaves() {
        lifecycleScope.launch {
            try {
                val resultado = supabase.postgrest["aeronaves"]
                    .select()
                    .decodeList<Aeronave>()

                aeronaves.clear()
                aeronaves.addAll(resultado)

                // Crea la lista de opciones para el Spinner con el formato "modelo - número de serie"
                val opciones = aeronaves.map { "${it.modelo} - ${it.numeroSerie}" }
                val adapter = ArrayAdapter(
                    this@NuevaInspeccionActivity,
                    android.R.layout.simple_spinner_item,
                    opciones
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAeronave.adapter = adapter

            } catch (e: Exception) {
                Toast.makeText(this@NuevaInspeccionActivity, "Error cargando aeronaves: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Valida los campos del formulario e inserta la nueva inspección en Supabase
    private fun guardarInspeccion() {
        val zona = etZona.text.toString().trim()
        val observaciones = etObservaciones.text.toString().trim()
        val ubicacionAeronave = etUbicacionAeronave.text.toString().trim()
        val conFod = cbConFod.isChecked

        // Obtiene la aeronave seleccionada en el Spinner usando su posición
        val aeronaveSeleccionada = if (aeronaves.isNotEmpty())
            aeronaves[spinnerAeronave.selectedItemPosition]
        else null

        // Validación: la zona es obligatoria
        if (zona.isEmpty()) {
            etZona.error = "La zona es obligatoria"
            return
        }

        // Deshabilita el botón y muestra el ProgressBar para evitar
        // pulsaciones múltiples mientras se procesa la petición
        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                // Obtiene el ID del usuario logueado para asociarlo a la inspección
                val session = supabase.auth.currentSessionOrNull()
                val email = session?.user?.email
                val usuarioResult = supabase.postgrest["usuarios"]
                    .select {
                        filter { eq("email", email ?: "") }
                    }
                    .decodeSingle<UsuarioId>()

                val nuevaInspeccion = NuevaInspeccion(
                    usuario_id = usuarioResult.id,
                    zona = zona,
                    observaciones = observaciones,
                    con_fod = conFod,
                    // Usa el ID de la aeronave seleccionada o null si no hay aeronaves
                    aeronave_id = aeronaveSeleccionada?.id,
                    // ifEmpty convierte cadenas vacías en null para no guardar strings vacíos
                    ubicacion_aeronave = ubicacionAeronave.ifEmpty { null }
                )

                // Inserta la nueva inspección en la tabla "inspecciones" de Supabase
                supabase.postgrest["inspecciones"].insert(nuevaInspeccion)

                runOnUiThread {
                    Toast.makeText(this@NuevaInspeccionActivity, "Inspección guardada", Toast.LENGTH_SHORT).show()
                    // Devuelve RESULT_OK para que InspeccionesFragment recargue la lista
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NuevaInspeccionActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    // Reactiva el botón para que el usuario pueda intentarlo de nuevo
                    btnGuardar.isEnabled = true
                    progressBar.isVisible = false
                }
            }
        }
    }

    // Gestiona el botón de atrás de la ActionBar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

// Clase auxiliar definida fuera de la Activity para deserializar
// únicamente el ID del usuario desde Supabase
@Serializable
data class UsuarioId(val id: Int)