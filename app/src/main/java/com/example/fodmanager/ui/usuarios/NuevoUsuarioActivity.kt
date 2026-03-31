package com.example.fodmanager.ui.usuarios

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.remote.CreateUserRequest
import com.example.fodmanager.data.remote.EdgeFunctionsClient
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.data.repository.UsuarioRepository
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.launch

// Activity encargada de crear un nuevo usuario
class NuevoUsuarioActivity : AppCompatActivity() {

    // Campo de texto para el nombre
    private lateinit var etNombre: TextInputEditText

    // Campo de texto para los apellidos
    private lateinit var etApellidos: TextInputEditText

    // Campo de texto para el número de empleado
    private lateinit var etNumeroEmpleado: TextInputEditText

    // Campo de texto para el email
    private lateinit var etEmail: TextInputEditText

    // Campo de texto para la contraseña
    private lateinit var etPassword: TextInputEditText

    // Spinner para seleccionar el rol
    private lateinit var spinnerRol: Spinner

    // Spinner para seleccionar la aeronave
    private lateinit var spinnerAeronave: Spinner

    // Etiqueta de texto para la aeronave
    private lateinit var tvAeronaveLabel: TextView

    // Botón para guardar el nuevo usuario
    private lateinit var btnGuardar: Button

    // Barra de progreso mostrada durante el guardado
    private lateinit var progressBar: ProgressBar

    // Lista mutable con los roles disponibles
    private val roles = mutableListOf<String>()

    // Roles que requieren asignación de aeronave
    private val rolesConAeronave = listOf("operario", "mando_gp4", "quality")

    // Lista mutable con las aeronaves disponibles
    private val aeronaves = mutableListOf<Aeronave>()

    // Método que se ejecuta al crear la activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Asigna el layout de la pantalla
        setContentView(R.layout.activity_nuevo_usuario)

        // Configura la barra superior
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nuevo Usuario"

        // Vincula las vistas con los elementos del layout
        etNombre = findViewById(R.id.etNombre)
        etApellidos = findViewById(R.id.etApellidos)
        etNumeroEmpleado = findViewById(R.id.etNumeroEmpleado)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        spinnerRol = findViewById(R.id.spinnerRol)
        spinnerAeronave = findViewById(R.id.spinnerNuevoUsuarioAeronave)
        tvAeronaveLabel = findViewById(R.id.tvNuevoUsuarioAeronaveLabel)
        btnGuardar = findViewById(R.id.btnGuardarUsuario)
        progressBar = findViewById(R.id.progressBar)

        // Listener que reacciona cuando cambia el rol seleccionado
        spinnerRol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (roles.isEmpty()) return

                // Si el rol requiere aeronave, se muestra el selector y se cargan las aeronaves
                if (roles[position] in rolesConAeronave) {
                    tvAeronaveLabel.isVisible = true
                    spinnerAeronave.isVisible = true
                    cargarAeronaves()
                } else {
                    // Si no requiere aeronave, se ocultan los elementos
                    tvAeronaveLabel.isVisible = false
                    spinnerAeronave.isVisible = false
                }
            }

            // Método requerido cuando no hay selección
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        // Carga los roles disponibles según el usuario logueado
        cargarRolesSegunUsuario()

        // Acción al pulsar el botón guardar
        btnGuardar.setOnClickListener {
            guardarUsuario()
        }
    }

    // Carga los roles que el usuario actual puede crear
    private fun cargarRolesSegunUsuario() {
        lifecycleScope.launch {
            try {
                // Obtiene el usuario logueado
                val usuarioLogueado = UsuarioRepository.getUsuarioActual()

                // Determina los roles disponibles según su rol
                val rolesDisponibles = when (usuarioLogueado.rol) {
                    "administrador" -> listOf(
                        "operario",
                        "mando_gp4",
                        "quality",
                        "focal_point_fod",
                        "head_plant",
                        "administrador"
                    )
                    "focal_point_fod" -> listOf("mando_gp4", "quality")
                    "mando_gp4" -> listOf("operario")
                    else -> emptyList()
                }

                // Limpia la lista actual y añade los nuevos roles
                roles.clear()
                roles.addAll(rolesDisponibles)

                // Crea el adaptador para el spinner de roles
                val adapterRol = ArrayAdapter(
                    this@NuevoUsuarioActivity,
                    android.R.layout.simple_spinner_item,
                    roles
                )
                adapterRol.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerRol.adapter = adapterRol

            } catch (e: Exception) {
                // Muestra error si falla la carga de roles
                Toast.makeText(
                    this@NuevoUsuarioActivity,
                    "Error cargando roles: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Carga las aeronaves activas desde la base de datos
    private fun cargarAeronaves() {
        lifecycleScope.launch {
            try {
                // Obtiene la lista de aeronaves activas
                val resultado = supabase.postgrest["aeronaves"]
                    .select {
                        filter { eq("activa", true) }
                    }
                    .decodeList<Aeronave>()

                // Limpia la lista actual
                aeronaves.clear()

                // Añade una opción para no asignar aeronave
                aeronaves.add(Aeronave(id = -1, modelo = "Sin aeronave asignada", numeroSerie = ""))

                // Añade el resto de aeronaves obtenidas
                aeronaves.addAll(resultado)

                // Convierte la lista en textos visibles para el spinner
                val opciones = aeronaves.map {
                    if (it.id == -1) "Sin aeronave asignada" else "${it.modelo} - ${it.numeroSerie}"
                }

                // Crea el adaptador del spinner de aeronaves
                val adapterAeronave = ArrayAdapter(
                    this@NuevoUsuarioActivity,
                    android.R.layout.simple_spinner_item,
                    opciones
                )
                adapterAeronave.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAeronave.adapter = adapterAeronave

            } catch (e: Exception) {
                // Muestra error si falla la carga de aeronaves
                Toast.makeText(
                    this@NuevoUsuarioActivity,
                    "Error cargando aeronaves: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Valida los datos y crea el nuevo usuario
    private fun guardarUsuario() {
        // Obtiene los valores introducidos en el formulario
        val nombre = etNombre.text.toString().trim()
        val apellidos = etApellidos.text.toString().trim()
        val numeroEmpleado = etNumeroEmpleado.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val rol = roles.getOrNull(spinnerRol.selectedItemPosition).orEmpty()

        // Obtiene la aeronave seleccionada si el rol la requiere
        val aeronaveId = if (rol in rolesConAeronave && aeronaves.isNotEmpty()) {
            val seleccionada = aeronaves[spinnerAeronave.selectedItemPosition]
            if (seleccionada.id == -1) null else seleccionada.id
        } else {
            null
        }

        // Validación del nombre
        if (nombre.isEmpty()) {
            etNombre.error = "El nombre es obligatorio"
            return
        }

        // Validación de los apellidos
        if (apellidos.isEmpty()) {
            etApellidos.error = "Los apellidos son obligatorios"
            return
        }

        // Validación del email
        if (email.isEmpty()) {
            etEmail.error = "El email es obligatorio"
            return
        }

        // Validación de la contraseña
        if (password.isEmpty()) {
            etPassword.error = "La contraseña es obligatoria"
            return
        }

        // Validación de longitud mínima de la contraseña
        if (password.length < 6) {
            etPassword.error = "La contraseña debe tener al menos 6 caracteres"
            return
        }

        // Validación del rol
        if (rol.isEmpty()) {
            Toast.makeText(this, "Selecciona un rol válido", Toast.LENGTH_SHORT).show()
            return
        }

        // Desactiva el botón y muestra el progreso durante el guardado
        btnGuardar.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // La creación real del usuario se hace en una Edge Function
                val result = EdgeFunctionsClient.createUser(
                    CreateUserRequest(
                        nombre = nombre,
                        apellidos = apellidos,
                        numero_empleado = numeroEmpleado.ifEmpty { null },
                        email = email,
                        password = password,
                        rol = rol,
                        aeronave_id = aeronaveId
                    )
                )

                // Gestiona el resultado de la creación
                result.fold(
                    onSuccess = {
                        Toast.makeText(
                            this@NuevoUsuarioActivity,
                            "Usuario creado correctamente",
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@NuevoUsuarioActivity,
                            "No se pudo crear el usuario: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )

            } catch (e: Exception) {
                // Muestra error si ocurre una excepción inesperada
                Toast.makeText(
                    this@NuevoUsuarioActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                // Reactiva el botón y oculta la barra de progreso
                btnGuardar.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    // Gestiona la pulsación del botón atrás de la barra superior
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}