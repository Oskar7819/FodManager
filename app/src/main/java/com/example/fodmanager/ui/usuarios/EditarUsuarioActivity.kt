package com.example.fodmanager.ui.usuarios

import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Payload mínimo para actualizar únicamente el campo `aeronave_id` de un usuario.
 *
 * El rol no se puede modificar desde la aplicación una vez creado el usuario,
 * por eso este payload solo contiene la aeronave asignada.
 * Enviar solo el campo que cambia evita sobreescribir accidentalmente otros datos.
 */
@Serializable
data class ActualizarUsuario(
    val aeronave_id: Int?
)

/**
 * Activity que permite reasignar la aeronave de un usuario existente.
 *
 * El rol se muestra como campo informativo (solo lectura): no es editable
 * desde la aplicación una vez que el usuario ha sido creado.
 *
 * Visibilidad del Spinner de aeronave:
 * - [rolesConAeronave] (mando_gp4, quality, operario) → Spinner visible con aeronaves activas.
 * - administrador, focal_point_fod, head_plant → Spinner oculto; estos roles no se adscriben
 *   a ninguna aeronave concreta.
 *
 * Solo se cargan aeronaves activas, ya que asignar un usuario a una aeronave
 * inactiva (que ya salió del hangar) no tiene sentido operativo.
 *
 * Al guardar devuelve RESULT_OK para que UsuariosFragment recargue la lista.
 */
class EditarUsuarioActivity : AppCompatActivity() {

    private lateinit var tvNombre: TextView
    private lateinit var tvEmail: TextView
    /** Muestra el rol actual del usuario (solo lectura). */
    private lateinit var tvRol: TextView
    private lateinit var spinnerAeronave: Spinner
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar

    /** Roles cuyo usuario puede estar adscrito a una aeronave (Spinner visible). */
    private val rolesConAeronave = listOf("mando_gp4", "quality", "operario")

    private val aeronaves = mutableListOf<Aeronave>()
    private var usuarioId: Int = -1
    private var rolUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_usuario)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editar Usuario"

        tvNombre = findViewById(R.id.tvEditarNombre)
        tvEmail = findViewById(R.id.tvEditarEmail)
        tvRol = findViewById(R.id.tvEditarRol)
        spinnerAeronave = findViewById(R.id.spinnerEditarAeronave)
        btnGuardar = findViewById(R.id.btnGuardarEdicion)
        progressBar = findViewById(R.id.progressBar)

        // Los datos del usuario llegan como extras del Intent enviado desde UsuariosFragment
        usuarioId = intent.getIntExtra("usuario_id", -1)
        val nombre = intent.getStringExtra("usuario_nombre") ?: ""
        val email = intent.getStringExtra("usuario_email") ?: ""
        rolUsuario = intent.getStringExtra("usuario_rol") ?: ""
        // takeIf { it != -1 } convierte el valor por defecto (-1) en null
        val aeronaveIdActual = intent.getIntExtra("usuario_aeronave_id", -1).takeIf { it != -1 }

        tvNombre.text = nombre
        tvEmail.text = email
        tvRol.text = rolUsuario

        // Muestra el Spinner solo para roles que pueden estar adscritos a una aeronave
        if (rolUsuario in rolesConAeronave) {
            spinnerAeronave.isVisible = true
            cargarAeronaves(aeronaveIdActual)
        } else {
            spinnerAeronave.isVisible = false
            findViewById<TextView>(R.id.tvEditarAeronaveLabel)?.isVisible = false
        }

        btnGuardar.setOnClickListener { guardarCambios() }
    }

    /**
     * Carga las aeronaves activas desde Supabase y las muestra en el Spinner.
     * Añade "Sin aeronave asignada" (id = -1) como primera opción para desasignar.
     * Si el usuario ya tiene una aeronave asignada, la preselecciona en el Spinner.
     *
     * Solo se cargan aeronaves activas: no tiene sentido adscribir un usuario
     * a una aeronave que ya ha salido del hangar.
     *
     * @param aeronaveIdActual ID de la aeronave actualmente asignada al usuario, o null.
     */
    private fun cargarAeronaves(aeronaveIdActual: Int?) {
        lifecycleScope.launch {
            try {
                val resultado = supabase.postgrest["aeronaves"]
                    .select { filter { eq("activa", true) } }
                    .decodeList<Aeronave>()

                aeronaves.clear()
                aeronaves.add(Aeronave(id = -1, modelo = "Sin aeronave asignada", numeroSerie = ""))
                aeronaves.addAll(resultado)

                val opciones = aeronaves.map {
                    if (it.id == -1) "Sin aeronave asignada" else "${it.modelo} - ${it.numeroSerie}"
                }
                val adapterAeronave = ArrayAdapter(
                    this@EditarUsuarioActivity,
                    android.R.layout.simple_spinner_item,
                    opciones
                )
                adapterAeronave.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAeronave.adapter = adapterAeronave

                // Preselecciona la aeronave que ya tiene asignada el usuario (si existe)
                val index = aeronaves.indexOfFirst { it.id == aeronaveIdActual }
                if (index >= 0) spinnerAeronave.setSelection(index)

            } catch (e: Exception) {
                Toast.makeText(this@EditarUsuarioActivity, "Error cargando aeronaves: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Actualiza el campo `aeronave_id` del usuario en Supabase.
     *
     * Si el rol del usuario no está en rolesConAeronave, guarda null directamente.
     * Si está en la lista pero se seleccionó "Sin aeronave asignada" (id = -1),
     * también guarda null para desasignarlo.
     *
     * Al completarse correctamente devuelve RESULT_OK y cierra la activity.
     */
    private fun guardarCambios() {
        val aeronaveId = if (rolUsuario in rolesConAeronave) {
            val seleccionada = aeronaves[spinnerAeronave.selectedItemPosition]
            if (seleccionada.id == -1) null else seleccionada.id
        } else null

        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                supabase.postgrest["usuarios"]
                    .update(ActualizarUsuario(aeronave_id = aeronaveId)) {
                        filter { eq("id", usuarioId) }
                    }

                runOnUiThread {
                    Toast.makeText(this@EditarUsuarioActivity, "Usuario actualizado", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@EditarUsuarioActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    btnGuardar.isEnabled = true
                    progressBar.isVisible = false
                }
            }
        }
    }

    /** Gestiona el botón de atrás de la ActionBar. */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}