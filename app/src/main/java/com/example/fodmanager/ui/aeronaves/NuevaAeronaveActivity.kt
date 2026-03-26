package com.example.fodmanager.ui.aeronaves

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Payload para el INSERT en la tabla `aeronaves`.
 *
 * Solo incluye los campos que debe proporcionar el usuario; el `id` y
 * el `created_at` los genera Supabase automáticamente en el servidor.
 * Las nuevas aeronaves siempre se crean como activas por defecto.
 */
@Serializable
data class NuevaAeronave(
    val modelo: String,
    val numero_serie: String,
    val ubicacion: String?,
    val activa: Boolean = true
)

/**
 * Activity con el formulario para registrar una nueva aeronave en el sistema.
 *
 * Campos del formulario:
 * - Modelo (obligatorio).
 * - Número de serie (obligatorio).
 * - Ubicación dentro del hangar (opcional; se guarda como null si se deja vacío).
 *
 * Acceso restringido a los roles: `administrador`, `mando_gp4` y `focal_point_fod`.
 * El control de visibilidad del FAB que abre esta activity se gestiona en AeronaveFragment.
 *
 * Al guardar con éxito devuelve RESULT_OK para que AeronaveFragment
 * recargue la lista e incluya la aeronave recién creada.
 */
class NuevaAeronaveActivity : AppCompatActivity() {

    private lateinit var etModelo: TextInputEditText
    private lateinit var etNumeroSerie: TextInputEditText
    private lateinit var etUbicacion: TextInputEditText
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_aeronave)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nueva Aeronave"

        etModelo = findViewById(R.id.etModelo)
        etNumeroSerie = findViewById(R.id.etNumeroSerie)
        etUbicacion = findViewById(R.id.etUbicacion)
        btnGuardar = findViewById(R.id.btnGuardarAeronave)
        progressBar = findViewById(R.id.progressBar)

        btnGuardar.setOnClickListener { guardarAeronave() }
    }

    /**
     * Valida el formulario e inserta la nueva aeronave en Supabase.
     *
     * Validaciones previas al envío:
     * - Modelo no puede estar vacío.
     * - Número de serie no puede estar vacío.
     *
     * Durante el proceso de red, el botón se deshabilita y aparece el ProgressBar
     * para evitar envíos duplicados. Si ocurre un error, ambos se restauran
     * para que el usuario pueda intentarlo de nuevo.
     */
    private fun guardarAeronave() {
        val modelo = etModelo.text.toString().trim()
        val numeroSerie = etNumeroSerie.text.toString().trim()
        val ubicacion = etUbicacion.text.toString().trim()

        if (modelo.isEmpty()) {
            etModelo.error = "El modelo es obligatorio"
            return
        }
        if (numeroSerie.isEmpty()) {
            etNumeroSerie.error = "El número de serie es obligatorio"
            return
        }

        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        // lifecycleScope garantiza que la corrutina se cancela automáticamente
        // si la activity es destruida, evitando fugas de memoria
        lifecycleScope.launch {
            try {
                val nuevaAeronave = NuevaAeronave(
                    modelo = modelo,
                    numero_serie = numeroSerie,
                    // Campo opcional: vacío → null en la base de datos
                    ubicacion = ubicacion.ifEmpty { null }
                )

                supabase.postgrest["aeronaves"].insert(nuevaAeronave)

                // Los cambios de UI siempre deben ejecutarse en el hilo principal
                runOnUiThread {
                    Toast.makeText(this@NuevaAeronaveActivity, "Aeronave guardada", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NuevaAeronaveActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    btnGuardar.isEnabled = true
                    progressBar.isVisible = false
                }
            }
        }
    }

    // Gestiona el botón de atrás.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}