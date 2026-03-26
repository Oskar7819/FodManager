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

/**
 * Payload para el INSERT en la tabla usuarios.
 *
 * Contiene todos los campos necesarios para crear un usuario completo.
 * La contraseña se guarda también en la tabla como referencia interna,
 * aunque la autenticación real la gestiona exclusivamente Supabase Auth.
 * Los nuevos usuarios se crean siempre como activos (`activo = true`).
 */
@Serializable
data class NuevoUsuario(
    val nombre: String,
    val apellidos: String,
    val numero_empleado: String?,
    val email: String,
    val password: String,
    val rol: String,
    val activo: Boolean = true,
    /** Null para roles que no se adscriben a ninguna aeronave concreta. */
    val aeronave_id: Int? = null
)

/**
 * Activity con el formulario para crear un nuevo usuario en el sistema.
 *
 * El proceso de creación tiene dos pasos:
 * 1. Registrar el usuario en Supabase Auth (`signUpWith`) para que pueda autenticarse.
 * 2. Insertar los datos adicionales en la tabla `usuarios`.
 *
 * IMPORTANTE — problema de sesión con `signUpWith`:
 * Supabase Auth cierra automáticamente la sesión del usuario actual al registrar
 * uno nuevo. Para evitar que el creador pierda su sesión, las credenciales se
 * guardan en [SessionManager] al hacer login y se restauran aquí tras el `signUpWith`.
 *
 * Roles disponibles en el Spinner según el rol del creador:
 * - administrador    puede crear cualquier rol.
 * - focal_point_fod puede crear `mando_gp4` y `quality`.
 * - mando_gp4      solo puede crear `operario`.
 * - Otros roles       →no deberían llegar a esta pantalla (lista vacía).
 *
 * Spinner de aeronave:
 * Solo aparece si el rol seleccionado pertenece a rolesConAeronave.
 * Se oculta dinámicamente al cambiar el Spinner de rol.
 */
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

    /** Lista de roles disponibles para el Spinner; se rellena según el rol del creador. */
    private val roles = mutableListOf<String>()

    /** Roles cuyo usuario puede estar adscrito a una aeronave (Spinner de aeronave visible). */
    private val rolesConAeronave = listOf("operario", "mando_gp4", "quality")

    private val aeronaves = mutableListOf<Aeronave>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nuevo_usuario)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nuevo Usuario"

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

        // Al cambiar el rol seleccionado, muestra u oculta el Spinner de aeronave dinámicamente
        spinnerRol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // La lista de roles puede no estar cargada aún; se evita ejecutar antes de tiempo
                if (roles.isEmpty()) return

                if (roles[position] in rolesConAeronave) {
                    tvAeronaveLabel.isVisible = true
                    spinnerAeronave.isVisible = true
                    cargarAeronaves()
                } else {
                    tvAeronaveLabel.isVisible = false
                    spinnerAeronave.isVisible = false
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        cargarRolesSegunUsuario()

        btnGuardar.setOnClickListener { guardarUsuario() }
    }

    /**
     * Consulta el rol del usuario logueado en Supabase y rellena roles con
     * los roles que tiene permiso para crear, según la jerarquía de permisos.
     * Una vez cargados, actualiza el adapter del Spinner de rol en el hilo principal.
     */
    private fun cargarRolesSegunUsuario() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email
                val usuarioLogueado = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<Usuario>()

                val rolesDisponibles = when (usuarioLogueado.rol) {
                    "administrador"   -> listOf("operario", "mando_gp4", "quality", "focal_point_fod", "head_plant", "administrador")
                    "focal_point_fod" -> listOf("mando_gp4", "quality")
                    "mando_gp4"       -> listOf("operario")
                    else              -> emptyList()
                }

                roles.clear()
                roles.addAll(rolesDisponibles)

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

    /**
     * Carga las aeronaves activas desde Supabase y las muestra en el Spinner de aeronave.
     * Añade Sin aeronave asignada (id = -1) como primera opción para no forzar la asignación.
     *
     * Solo se cargan aeronaves activas: adscribir un usuario a una aeronave inactiva
     * (que ya salió del hangar) no tiene sentido operativo.
     */
    private fun cargarAeronaves() {
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

    /**
     * Valida el formulario y crea el nuevo usuario en dos pasos:
     * 1. signUpWith(Email) →registra las credenciales en Supabase Auth.
     * 2. INSERT en usuarios  guarda el perfil completo del usuario.
     * 3. signInWith(Email) restaura la sesión del creador usando SessionManager.
     *
     * Validaciones previas al envío:
     * - Nombre, apellidos, email y contraseña son obligatorios.
     * - Número de empleado es opcional; si se deja vacío se guarda como null.
     *
     * Si la aeronave seleccionada es Sin aeronave asignada (id = -1),
     * se guarda null en aeronave_id.
     */
    private fun guardarUsuario() {
        val nombre = etNombre.text.toString().trim()
        val apellidos = etApellidos.text.toString().trim()
        val numeroEmpleado = etNumeroEmpleado.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val rol = roles[spinnerRol.selectedItemPosition]

        val aeronaveId = if (rol in rolesConAeronave && aeronaves.isNotEmpty()) {
            val seleccionada = aeronaves[spinnerAeronave.selectedItemPosition]
            if (seleccionada.id == -1) null else seleccionada.id
        } else null

        if (nombre.isEmpty())    { etNombre.error = "El nombre es obligatorio"; return }
        if (apellidos.isEmpty()) { etApellidos.error = "Los apellidos son obligatorios"; return }
        if (email.isEmpty())     { etEmail.error = "El email es obligatorio"; return }
        if (password.isEmpty())  { etPassword.error = "La contraseña es obligatoria"; return }

        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                // Paso 1: registrar en Supabase Auth.
                // ADVERTENCIA: esto cierra la sesión actual automáticamente.
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Paso 2: insertar el perfil completo en la tabla usuarios
                supabase.postgrest["usuarios"].insert(
                    NuevoUsuario(
                        nombre = nombre,
                        apellidos = apellidos,
                        numero_empleado = numeroEmpleado.ifEmpty { null },
                        email = email,
                        password = password,
                        rol = rol,
                        aeronave_id = aeronaveId
                    )
                )

                // Paso 3: restaurar la sesión del creador (cerrada por signUpWith en el paso 1)
                supabase.auth.signInWith(Email) {
                    this.email = SessionManager.emailActual
                    this.password = SessionManager.passwordActual
                }

                runOnUiThread {
                    Toast.makeText(this@NuevoUsuarioActivity, "Usuario creado correctamente", Toast.LENGTH_SHORT).show()
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

    /** Gestiona el botón de atrás de la ActionBar. */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}