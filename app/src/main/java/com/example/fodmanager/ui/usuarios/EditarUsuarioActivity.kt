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

// Clase de datos usada para actualizar únicamente el campo "aeronave_id" del usuario en Supabase.
// El rol no se puede modificar desde la app una vez creado el usuario,
// por eso solo se actualiza la aeronave asignada.
@Serializable
data class ActualizarUsuario(
    val aeronave_id: Int?
)

// Activity que permite editar la aeronave asignada a un usuario existente.
// El rol del usuario se muestra como información pero no es editable.
// Solo los roles que pueden estar adscritos a una aeronave ven el Spinner:
// mando_gp4, quality y operario.
// Los roles administrador, focal_point_fod y head_plant no se adscriben a aeronaves.
class EditarUsuarioActivity : AppCompatActivity() {

    private lateinit var tvNombre: TextView
    private lateinit var tvEmail: TextView
    // Muestra el rol actual del usuario (solo lectura, no editable)
    private lateinit var tvRol: TextView
    private lateinit var spinnerAeronave: Spinner
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar

    // Roles que pueden estar adscritos a una aeronave y por tanto ven el Spinner
    private val rolesConAeronave = listOf("mando_gp4", "quality", "operario")
    private val aeronaves = mutableListOf<Aeronave>()
    private var usuarioId: Int = -1
    private var rolUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_usuario)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editar Usuario"

        // Inicialización de los elementos visuales del layout
        tvNombre = findViewById(R.id.tvEditarNombre)
        tvEmail = findViewById(R.id.tvEditarEmail)
        tvRol = findViewById(R.id.tvEditarRol)
        spinnerAeronave = findViewById(R.id.spinnerEditarAeronave)
        btnGuardar = findViewById(R.id.btnGuardarEdicion)
        progressBar = findViewById(R.id.progressBar)

        // Recupera los datos del usuario enviados desde UsuariosFragment mediante el Intent
        usuarioId = intent.getIntExtra("usuario_id", -1)
        val nombre = intent.getStringExtra("usuario_nombre") ?: ""
        val email = intent.getStringExtra("usuario_email") ?: ""
        rolUsuario = intent.getStringExtra("usuario_rol") ?: ""
        // takeIf { it != -1 } convierte -1 (valor por defecto) en null
        val aeronaveIdActual = intent.getIntExtra("usuario_aeronave_id", -1).takeIf { it != -1 }

        // Muestra los datos del usuario en los TextViews
        tvNombre.text = nombre
        tvEmail.text = email
        tvRol.text = rolUsuario

        // Muestra u oculta el Spinner de aeronave según el rol del usuario
        if (rolUsuario in rolesConAeronave) {
            // Los roles que pueden estar adscritos a aeronave ven el Spinner
            spinnerAeronave.isVisible = true
            cargarAeronaves(aeronaveIdActual)
        } else {
            // Los roles que no se adscriben a aeronave no ven el Spinner ni su etiqueta
            spinnerAeronave.isVisible = false
            findViewById<TextView>(R.id.tvEditarAeronaveLabel)?.isVisible = false
        }

        btnGuardar.setOnClickListener { guardarCambios() }
    }

    // Carga las aeronaves activas desde Supabase y las muestra en el Spinner.
    // Solo carga aeronaves activas ya que no tiene sentido asignar
    // un usuario a una aeronave que ya se ha ido del hangar.
    private fun cargarAeronaves(aeronaveIdActual: Int?) {
        lifecycleScope.launch {
            try {
                // Filtra solo aeronaves activas
                val resultado = supabase.postgrest["aeronaves"]
                    .select { filter { eq("activa", true) } }
                    .decodeList<Aeronave>()

                aeronaves.clear()
                // Añade una opción "Sin aeronave asignada" al inicio de la lista
                // con ID -1 para identificarla fácilmente
                aeronaves.add(Aeronave(id = -1, modelo = "Sin aeronave asignada", numeroSerie = ""))
                aeronaves.addAll(resultado)

                val opciones = aeronaves.map {
                    if (it.id == -1) "Sin aeronave asignada"
                    else "${it.modelo} - ${it.numeroSerie}"
                }
                val adapterAeronave = ArrayAdapter(
                    this@EditarUsuarioActivity,
                    android.R.layout.simple_spinner_item,
                    opciones
                )
                adapterAeronave.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAeronave.adapter = adapterAeronave

                // Selecciona automáticamente la aeronave que ya tiene asignada el usuario
                val index = aeronaves.indexOfFirst { it.id == aeronaveIdActual }
                if (index >= 0) spinnerAeronave.setSelection(index)

            } catch (e: Exception) {
                Toast.makeText(this@EditarUsuarioActivity, "Error cargando aeronaves: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Actualiza el campo aeronave_id del usuario en Supabase
    private fun guardarCambios() {
        // Determina el ID de la aeronave seleccionada.
        // Si se seleccionó "Sin aeronave asignada" (id = -1) guarda null en la base de datos
        val aeronaveId = if (rolUsuario in rolesConAeronave) {
            val aeronaveSeleccionada = aeronaves[spinnerAeronave.selectedItemPosition]
            if (aeronaveSeleccionada.id == -1) null else aeronaveSeleccionada.id
        } else null

        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                // Actualiza únicamente el campo aeronave_id del usuario en Supabase
                // filtrando por el ID del usuario que se está editando
                supabase.postgrest["usuarios"]
                    .update(ActualizarUsuario(aeronave_id = aeronaveId)) {
                        filter { eq("id", usuarioId) }
                    }

                runOnUiThread {
                    Toast.makeText(this@EditarUsuarioActivity, "Usuario actualizado", Toast.LENGTH_SHORT).show()
                    // Devuelve RESULT_OK para que UsuariosFragment recargue la lista
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

    // Gestiona el botón de atrás de la ActionBar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}