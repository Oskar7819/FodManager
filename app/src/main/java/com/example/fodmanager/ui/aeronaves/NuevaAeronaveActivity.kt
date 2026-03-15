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

// Clase de datos que representa la estructura del INSERT en la tabla aeronaves de Supabase.
// Solo contiene los campos necesarios para crear una nueva aeronave,
// el ID y created_at los genera Supabase automáticamente.
@Serializable
data class NuevaAeronave(
    val modelo: String,
    val numero_serie: String,
    val ubicacion: String?,
    // Las nuevas aeronaves siempre se crean como activas por defecto
    val activa: Boolean = true
)

// Activity que muestra el formulario para dar de alta una nueva aeronave en el sistema.
// Solo accesible para los roles: administrador, mando_gp4 y focal_point_fod.
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

        // Inicialización de los elementos visuales del layout
        etModelo = findViewById(R.id.etModelo)
        etNumeroSerie = findViewById(R.id.etNumeroSerie)
        etUbicacion = findViewById(R.id.etUbicacion)
        btnGuardar = findViewById(R.id.btnGuardarAeronave)
        progressBar = findViewById(R.id.progressBar)

        btnGuardar.setOnClickListener { guardarAeronave() }
    }

    // Valida los campos del formulario e inserta la nueva aeronave en Supabase
    private fun guardarAeronave() {
        val modelo = etModelo.text.toString().trim()
        val numeroSerie = etNumeroSerie.text.toString().trim()
        val ubicacion = etUbicacion.text.toString().trim()

        // Validaciones: modelo y número de serie son obligatorios
        if (modelo.isEmpty()) {
            etModelo.error = "El modelo es obligatorio"
            return
        }
        if (numeroSerie.isEmpty()) {
            etNumeroSerie.error = "El número de serie es obligatorio"
            return
        }

        // Deshabilita el botón y muestra el ProgressBar para evitar
        // pulsaciones múltiples mientras se procesa la petición
        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        // lifecycleScope.launch ejecuta el código en una corrutina,
        // permitiendo hacer operaciones de red sin bloquear el hilo principal de la UI
        lifecycleScope.launch {
            try {
                val nuevaAeronave = NuevaAeronave(
                    modelo = modelo,
                    numero_serie = numeroSerie,
                    // Si la ubicación está vacía se guarda como null en la base de datos
                    ubicacion = ubicacion.ifEmpty { null }
                )

                // Inserta la nueva aeronave en la tabla aeronaves de Supabase
                supabase.postgrest["aeronaves"].insert(nuevaAeronave)

                // runOnUiThread es necesario porque estamos en una corrutina
                // y los cambios de UI deben hacerse en el hilo principal
                runOnUiThread {
                    Toast.makeText(this@NuevaAeronaveActivity, "Aeronave guardada", Toast.LENGTH_SHORT).show()
                    // Devuelve RESULT_OK para que AeronaveFragment recargue la lista
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NuevaAeronaveActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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