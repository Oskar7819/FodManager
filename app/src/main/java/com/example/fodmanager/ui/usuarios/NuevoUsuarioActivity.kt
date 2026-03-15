package com.example.fodmanager.ui.usuarios

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.local.SessionManager
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Clase de datos que representa la estructura del INSERT en la tabla usuarios de Supabase.
// Contiene todos los campos necesarios para crear un nuevo usuario en el sistema.
@Serializable
data class NuevoUsuario(
    val nombre: String,
    val apellidos: String,
    val numero_empleado: String?,
    val email: String,
    // La contraseña se guarda también en la tabla usuarios como referencia,
    // aunque la autenticación real la gestiona Supabase Auth
    val password: String,
    val rol: String,
    // Los nuevos usuarios se crean como activos por defecto
    val activo: Boolean = true,
    // ID de la aeronave asignada, null para roles que no se adscriben a aeronaves
    val aeronave_id: Int? = null
)

// Activity que muestra el formulario para crear un nuevo usuario.
// Los roles disponibles en el Spinner varían según el rol del usuario:
// - administrador puede crear cualquier rol
// - focal_point_fod puede crear mando_gp4 y quality
// - mando_gp4 solo puede crear operarios
// El Spinner de aeronave solo aparece para roles que se adscriben a aeronaves:
// operario, mando_gp4 y quality
class NuevoUsuarioActivity : AppCompatActivity() {

    private lateinit var etNombre: TextInputEditText
    private lateinit var etApellidos: TextInputEditText
    private lateinit var etNumeroEmpleado: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var spinnerRol: Spinner
    private lateinit var spinnerAeronave: Spinner
    private lateinit var tvAeronaveLabel: TextView
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar

    // Lista mutable de roles disponibles, se rellena según el rol del usuario
    private val roles = mutableListOf<String>()
    // Roles que pueden estar adscritos a una aeronave y por tanto ven el Spinner de aeronave
    private val rolesConAeronave = listOf("operario", "mando_gp4", "quality")
    private val aeronaves = mutableListOf<Aeronave>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nuevo_usuario)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nuevo Usuario"

        // Inicialización de los elementos visuales del layout
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

        // Listener que detecta cambios en el Spinner de rol para mostrar u ocultar
        // el Spinner de aeronave según si el rol seleccionado requiere aeronave asignada
        spinnerRol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Evita ejecutarse antes de que se carguen los roles
                if (roles.isEmpty()) return
                val rolSeleccionado = roles[position]
                if (rolSeleccionado in rolesConAeronave) {
                    // Muestra el Spinner de aeronave y carga las aeronaves disponibles
                    tvAeronaveLabel.isVisible = true
                    spinnerAeronave.isVisible = true
                    cargarAeronaves()
                } else {
                    // Oculta el Spinner de aeronave para roles que no se adscriben a aeronaves
                    tvAeronaveLabel.isVisible = false
                    spinnerAeronave.isVisible = false
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Carga los roles disponibles según el rol del usuario
        // antes de mostrar el formulario
        cargarRolesSegunUsuario()

        btnGuardar.setOnClickListener { guardarUsuario() }
    }

    // Consulta el rol del usuario y determina qué roles puede crear
    private fun cargarRolesSegunUsuario() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email
                val usuarioLogueado = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<Usuario>()

                // Define los roles disponibles según la jerarquía de permisos
                val rolesDisponibles = when (usuarioLogueado.rol) {
                    // El administrador puede crear cualquier tipo de usuario
                    "administrador" -> listOf("operario", "mando_gp4", "quality", "focal_point_fod", "head_plant", "administrador")
                    // focal_point_fod gestiona mandos y quality
                    "focal_point_fod" -> listOf("mando_gp4", "quality")
                    // mando_gp4 solo puede crear operarios para su aeronave
                    "mando_gp4" -> listOf("operario")
                    // Cualquier otro rol no puede crear usuarios
                    else -> emptyList()
                }

                roles.clear()
                roles.addAll(rolesDisponibles)

                // Actualiza el adapter del Spinner en el hilo principal
                runOnUiThread {
                    val adapterRol = ArrayAdapter(this@NuevoUsuarioActivity, android.R.layout.simple_spinner_item, roles)
                    adapterRol.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerRol.adapter = adapterRol
                }

            } catch (e: Exception) {
                Toast.makeText(this@NuevoUsuarioActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /* Carga las aeronaves activas desde Supabase y las muestra en el Spinner.
       Solo carga aeronaves activas ya que no tiene sentido asignar
       un usuario a una aeronave que ya se ha ido del hangar   */
    private fun cargarAeronaves() {
        lifecycleScope.launch {
            try {
                val resultado = supabase.postgrest["aeronaves"]
                    .select { filter { eq("activa", true) } }
                    .decodeList<Aeronave>()

                aeronaves.clear()
                // Añade una opción "Sin aeronave asignada" al inicio con ID -1
                aeronaves.add(Aeronave(id = -1, modelo = "Sin aeronave asignada", numeroSerie = ""))
                aeronaves.addAll(resultado)

                val opciones = aeronaves.map {
                    if (it.id == -1) "Sin aeronave asignada"
                    else "${it.modelo} - ${it.numeroSerie}"
                }
                val adapterAeronave = ArrayAdapter(
                    this@NuevoUsuarioActivity,
                    android.R.layout.simple_spinner_item,
                    opciones
                )
                adapterAeronave.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAeronave.adapter = adapterAeronave

            } catch (e: Exception) {
                Toast.makeText(this@NuevoUsuarioActivity, "Error cargando aeronaves: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Valida los campos, crea el usuario en Supabase Auth e inserta sus datos en la tabla usuarios
    private fun guardarUsuario() {
        val nombre = etNombre.text.toString().trim()
        val apellidos = etApellidos.text.toString().trim()
        val numeroEmpleado = etNumeroEmpleado.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val rol = roles[spinnerRol.selectedItemPosition]

        // Obtiene el ID de la aeronave seleccionada.
        // Si se seleccionó "Sin aeronave asignada" (id = -1) guarda null
        val aeronaveId = if (rol in rolesConAeronave && aeronaves.isNotEmpty()) {
            val seleccionada = aeronaves[spinnerAeronave.selectedItemPosition]
            if (seleccionada.id == -1) null else seleccionada.id
        } else null

        // Validaciones de campos obligatorios
        if (nombre.isEmpty()) { etNombre.error = "El nombre es obligatorio"; return }
        if (apellidos.isEmpty()) { etApellidos.error = "Los apellidos son obligatorios"; return }
        if (email.isEmpty()) { etEmail.error = "El email es obligatorio"; return }
        if (password.isEmpty()) { etPassword.error = "La contraseña es obligatoria"; return }

        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                // Crea el usuario en Supabase Auth para que pueda iniciar sesión en la app.
                // IMPORTANTE: signUpWith cierra automáticamente la sesión actual
                // e inicia sesión con el nuevo usuario
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Inserta los datos adicionales del usuario en la tabla "usuarios"
                val nuevoUsuario = NuevoUsuario(
                    nombre = nombre,
                    apellidos = apellidos,
                    numero_empleado = numeroEmpleado.ifEmpty { null },
                    email = email,
                    password = password,
                    rol = rol,
                    aeronave_id = aeronaveId
                )
                supabase.postgrest["usuarios"].insert(nuevoUsuario)

                // Restaura la sesión del usuario original usando las credenciales
                // guardadas en SessionManager al hacer login.
                // Necesario porque signUpWith cerró la sesión anterior automáticamente
                supabase.auth.signInWith(Email) {
                    this.email = SessionManager.emailActual
                    this.password = SessionManager.passwordActual
                }

                runOnUiThread {
                    Toast.makeText(this@NuevoUsuarioActivity, "Usuario creado correctamente", Toast.LENGTH_SHORT).show()
                    // Devuelve RESULT_OK para que UsuariosFragment recargue la lista
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NuevoUsuarioActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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