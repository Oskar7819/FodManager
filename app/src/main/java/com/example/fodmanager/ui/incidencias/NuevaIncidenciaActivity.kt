package com.example.fodmanager.ui.incidencias

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Payload que se inserta en la tabla `incidencias_fod`.
 *
 * La zona y la aeronave se heredan de la inspección origen.
 * El estado inicial siempre será "abierta".
 */
@Serializable
data class InsertIncidenciaFodPayload(
    // ID de la inspección desde la que se crea la incidencia
    @SerialName("inspeccion_id") val inspeccionId: Int,

    // ID del usuario que declara la incidencia
    @SerialName("usuario_id") val usuarioId: Int,

    // ID de la aeronave asociada a la incidencia, puede ser nulo
    @SerialName("aeronave_id") val aeronaveId: Int?,

    // Descripción escrita por el usuario
    val descripcion: String,

    // Zona del avión donde se detectó el FOD
    @SerialName("zona_avion") val zonaAvion: String?,

    // Número de empleado del declarante, puede ser nulo
    @SerialName("numero_empleado") val numeroEmpleado: String?,

    // URL pública de la imagen subida a Storage, puede ser nula
    @SerialName("imagen_url") val imagenUrl: String?,

    // Estado inicial de la incidencia
    val estado: String = "abierta",

    // Tipo de FOD seleccionado, puede ser nulo
    @SerialName("tipo_fod") val tipoFod: String?,

    // Prioridad seleccionada, puede ser nula
    val prioridad: String?
)

/**
 * Proyección mínima del usuario logueado para registrar la incidencia.
 */
@Serializable
data class UsuarioIncidenciaActual(
    // ID interno del usuario en la tabla usuarios
    val id: Int,

    // Nombre del usuario
    val nombre: String,

    // Apellidos del usuario
    val apellidos: String,

    // Rol del usuario
    val rol: String,

    // Número de empleado, puede ser nulo
    @SerialName("numero_empleado") val numeroEmpleado: String? = null
)

/**
 * Proyección mínima de la inspección origen.
 */
@Serializable
data class InspeccionOrigenIncidencia(
    // ID de la inspección
    val id: Int,

    // Zona inspeccionada
    val zona: String,

    // ID de la aeronave asociada a la inspección, puede ser nulo
    @SerialName("aeronave_id") val aeronaveId: Int? = null,

    // Indica si en la inspección se detectó FOD
    @SerialName("con_fod") val conFod: Boolean
)

/**
 * Pantalla para registrar una nueva incidencia FOD vinculada a una inspección previa.
 *
 * Flujo:
 * 1. Cargar usuario logueado.
 * 2. Cargar inspección origen.
 * 3. Permitir tomar foto.
 * 4. Subir imagen a Supabase Storage.
 * 5. Insertar incidencia en `incidencias_fod`.
 */
class NuevaIncidenciaActivity : AppCompatActivity() {

    // Campo de texto para la descripción de la incidencia
    private lateinit var etDescripcion: TextInputEditText

    // Botón para abrir la cámara
    private lateinit var btnTomarFoto: Button

    // Vista previa de la foto capturada
    private lateinit var imgPreview: ImageView

    // Botón para guardar la incidencia
    private lateinit var btnGuardar: Button

    // Barra de progreso mostrada durante el guardado
    private lateinit var progressBar: ProgressBar

    // Spinner para elegir el tipo de FOD
    private lateinit var spinnerTipoFod: Spinner

    // Spinner para elegir la prioridad
    private lateinit var spinnerPrioridad: Spinner

    // TextView con el nombre del declarante
    private lateinit var tvDeclaranteNombre: TextView

    // TextView con los apellidos del declarante
    private lateinit var tvDeclaranteApellidos: TextView

    // TextView con el número de empleado del declarante
    private lateinit var tvDeclaranteNumeroEmpleado: TextView

    // TextView con la zona del avión
    private lateinit var tvZonaAvion: TextView

    // Bitmap capturado con la cámara
    private var fotoBitmap: Bitmap? = null

    // ID de la inspección recibida por Intent
    private var inspeccionId: Int = -1

    // Usuario cargado desde Supabase
    private var usuarioLogueado: UsuarioIncidenciaActual? = null

    // Inspección origen cargada desde Supabase
    private var inspeccionOrigen: InspeccionOrigenIncidencia? = null

    /**
     * Roles autorizados para registrar incidencias FOD.
     * `focal_point_fod` y `head_plant` quedan fuera.
     */
    private val rolesQuePuedenRegistrarIncidencia = listOf(
        "operario",
        "mando_gp4",
        "quality"
    )

    /**
     * Valores internos del enum `tipo_fod`.
     * Estos son los que realmente se guardan en la base de datos.
     */
    private val tiposFod = listOf(
        "ambiental",
        "herramientas",
        "restos_metalicos",
        "material_consumo",
        "personal",
        "procedente_aeronave"
    )

    /**
     * Valores visibles para el Spinner de tipo de FOD.
     */
    private val tiposFodMostrar = listOf(
        "🌫️ Ambiental (suciedad y polvo)",
        "🔧 Herramientas (llaves, destornilladores...)",
        "🔩 Restos metálicos (tornillos, remaches...)",
        "🧤 Material de consumo (trapos, guantes...)",
        "👤 Personal (bolígrafos, monedas...)",
        "✈️ Procedente de aeronave (sellante, pintura...)"
    )

    /**
     * Valores internos del campo prioridad.
     */
    private val prioridades = listOf("baja", "alta")

    /**
     * Valores visibles en el Spinner de prioridad.
     */
    private val prioridadesMostrar = listOf(
        "🟢 Baja",
        "🔴 Alta"
    )

    /**
     * Launcher de cámara.
     *
     * Usa la forma moderna de leer el Bitmap desde extras:
     * - En Android 13+ usa getParcelable("data", Bitmap::class.java)
     * - En versiones anteriores usa getParcelable<Bitmap>("data")
     *
     * Nota:
     * `data` aquí devuelve normalmente un thumbnail de la foto,
     * no la imagen completa en alta resolución.
     */
    private val camaraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Si la captura fue correcta, intentamos obtener el bitmap
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.let { extras ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Forma moderna para Android 13 o superior
                    extras.getParcelable("data", Bitmap::class.java)
                } else {
                    // Forma antigua para versiones anteriores
                    @Suppress("DEPRECATION")
                    extras.getParcelable<Bitmap>("data")
                }
            }

            // Si el bitmap existe, se guarda y se muestra en pantalla
            if (bitmap != null) {
                fotoBitmap = bitmap
                imgPreview.setImageBitmap(bitmap)
                imgPreview.isVisible = true
            } else {
                // Si no se pudo recuperar la imagen, se informa al usuario
                Toast.makeText(
                    this,
                    "No se pudo obtener la imagen de la cámara",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Launcher para pedir permiso de cámara.
     */
    private val permisoCamaraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        // Si el usuario concede permiso, se abre la cámara
        if (concedido) {
            abrirCamara()
        } else {
            // Si no lo concede, se muestra mensaje informativo
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Asigna el layout de la activity
        setContentView(R.layout.activity_nueva_incidencia)

        // Configuración de la ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nueva Incidencia FOD"

        // Inicialización de vistas
        etDescripcion = findViewById(R.id.etDescripcion)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        imgPreview = findViewById(R.id.imgPreview)
        btnGuardar = findViewById(R.id.btnGuardar)
        progressBar = findViewById(R.id.progressBar)
        spinnerTipoFod = findViewById(R.id.spinnerTipoFod)
        spinnerPrioridad = findViewById(R.id.spinnerPrioridad)

        tvDeclaranteNombre = findViewById(R.id.tvDeclaranteNombre)
        tvDeclaranteApellidos = findViewById(R.id.tvDeclaranteApellidos)
        tvDeclaranteNumeroEmpleado = findViewById(R.id.tvDeclaranteNumeroEmpleado)
        tvZonaAvion = findViewById(R.id.tvZonaAvion)

        // Spinner de prioridad
        val adapterPrioridad = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            prioridadesMostrar
        )
        adapterPrioridad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPrioridad.adapter = adapterPrioridad

        // Spinner de tipo FOD
        val adapterTipo = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            tiposFodMostrar
        )
        adapterTipo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTipoFod.adapter = adapterTipo

        // Recuperar inspección origen desde el Intent
        inspeccionId = intent.getIntExtra("inspeccion_id", -1)

        // Si no llega una inspección válida, se cierra la pantalla
        if (inspeccionId == -1) {
            Toast.makeText(this, "Error: inspección no válida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Carga inicial de datos
        cargarUsuarioLogueado()
        cargarInspeccionOrigen()

        // Eventos
        btnTomarFoto.setOnClickListener { verificarPermisoCamara() }
        btnGuardar.setOnClickListener { guardarIncidencia() }
    }

    /**
     * Carga el usuario logueado desde la tabla `usuarios`,
     * usando el email de la sesión actual de Supabase Auth.
     */
    private fun cargarUsuarioLogueado() {
        lifecycleScope.launch {
            try {
                // Obtiene el email del usuario autenticado
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                // Si no hay email, no hay sesión válida
                if (email.isBlank()) {
                    Toast.makeText(
                        this@NuevaIncidenciaActivity,
                        "No hay sesión válida",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@launch
                }

                // Busca el usuario en la tabla usuarios por su email
                val usuario = supabase.postgrest["usuarios"]
                    .select {
                        filter {
                            eq("email", email)
                        }
                    }
                    .decodeSingle<UsuarioIncidenciaActual>()

                usuarioLogueado = usuario

                // Pintar datos del usuario en pantalla
                tvDeclaranteNombre.text = "Nombre: ${usuario.nombre}"
                tvDeclaranteApellidos.text = "Apellidos: ${usuario.apellidos}"
                tvDeclaranteNumeroEmpleado.text =
                    "Nº empleado: ${usuario.numeroEmpleado ?: "No especificado"}"

                // Verificación de rol
                if (usuario.rol !in rolesQuePuedenRegistrarIncidencia) {
                    Toast.makeText(
                        this@NuevaIncidenciaActivity,
                        "Tu rol no puede registrar incidencias FOD.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }

            } catch (e: Exception) {
                // Muestra error si no se puede cargar el usuario
                Toast.makeText(
                    this@NuevaIncidenciaActivity,
                    "Error cargando declarante: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Carga la inspección origen desde la tabla `inspecciones`.
     * Muestra la zona y bloquea el guardado si esa inspección no tiene FOD.
     */
    private fun cargarInspeccionOrigen() {
        lifecycleScope.launch {
            try {
                // Busca la inspección origen por su id
                val inspeccion = supabase.postgrest["inspecciones"]
                    .select {
                        filter {
                            eq("id", inspeccionId)
                        }
                    }
                    .decodeSingle<InspeccionOrigenIncidencia>()

                inspeccionOrigen = inspeccion

                // Mostrar zona en la interfaz
                tvZonaAvion.text = "Zona: ${inspeccion.zona}"

                // Si la inspección no tiene FOD, no se puede crear incidencia
                if (!inspeccion.conFod) {
                    btnGuardar.isEnabled = false
                    Toast.makeText(
                        this@NuevaIncidenciaActivity,
                        "Esta inspección está marcada sin FOD. No se puede registrar incidencia.",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                // Muestra error si no se puede cargar la inspección
                Toast.makeText(
                    this@NuevaIncidenciaActivity,
                    "Error cargando inspección: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Comprueba si ya existe permiso de cámara.
     * Si no existe, lo solicita.
     */
    private fun verificarPermisoCamara() {
        // Comprueba si el permiso de cámara ya está concedido
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            abrirCamara()
        } else {
            // Si no está concedido, lo solicita al usuario
            permisoCamaraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Lanza la app de cámara del dispositivo.
     *
     * Este enfoque devuelve normalmente una miniatura en extras["data"].
     */
    private fun abrirCamara() {
        // Lanza el intent de captura de imagen
        camaraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }

    /**
     * Guarda la incidencia FOD en Supabase.
     *
     * Pasos:
     * 1. Validar usuario, inspección, descripción y foto.
     * 2. Comprimir imagen.
     * 3. Subir imagen al bucket `fod-images`.
     * 4. Obtener URL pública.
     * 5. Insertar registro en `incidencias_fod`.
     */
    private fun guardarIncidencia() {
        // Recupera el usuario cargado o muestra error
        val usuario = usuarioLogueado ?: run {
            Toast.makeText(this, "No se ha podido cargar el usuario logueado", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Recupera la inspección cargada o muestra error
        val inspeccion = inspeccionOrigen ?: run {
            Toast.makeText(this, "No se ha podido cargar la inspección origen", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Doble comprobación defensiva de rol
        if (usuario.rol !in rolesQuePuedenRegistrarIncidencia) {
            Toast.makeText(this, "Tu rol no puede registrar incidencias FOD.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // La inspección debe estar marcada con FOD
        if (!inspeccion.conFod) {
            Toast.makeText(this, "La inspección origen no tiene FOD", Toast.LENGTH_SHORT).show()
            return
        }

        // Recoger datos del formulario
        val descripcion = etDescripcion.text?.toString()?.trim().orEmpty()
        val tipoFod = tiposFod[spinnerTipoFod.selectedItemPosition]
        val prioridad = prioridades[spinnerPrioridad.selectedItemPosition]

        // Validación de descripción
        if (descripcion.isBlank()) {
            etDescripcion.error = "La descripción es obligatoria"
            return
        }

        // La foto es obligatoria
        if (fotoBitmap == null) {
            Toast.makeText(this, "La foto es obligatoria", Toast.LENGTH_SHORT).show()
            return
        }

        // Bloquear botón y mostrar progreso
        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                // 1) Comprimir imagen a JPEG con calidad 80
                val stream = ByteArrayOutputStream()
                fotoBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, stream)

                // 2) Nombre único del archivo
                val nombreArchivo = "fod_${UUID.randomUUID()}.jpg"

                // 3) Subir al bucket de Storage
                supabase.storage["fod-images"].upload(
                    nombreArchivo,
                    stream.toByteArray()
                )

                // 4) Obtener URL pública
                val imagenUrl = supabase.storage["fod-images"].publicUrl(nombreArchivo)

                // 5) Insertar incidencia en la tabla
                supabase.postgrest["incidencias_fod"].insert(
                    InsertIncidenciaFodPayload(
                        inspeccionId = inspeccion.id,
                        usuarioId = usuario.id,
                        aeronaveId = inspeccion.aeronaveId,
                        descripcion = descripcion,
                        zonaAvion = inspeccion.zona,
                        numeroEmpleado = usuario.numeroEmpleado,
                        imagenUrl = imagenUrl,
                        tipoFod = tipoFod,
                        prioridad = prioridad
                    )
                )

                // Muestra mensaje de éxito
                Toast.makeText(
                    this@NuevaIncidenciaActivity,
                    "Incidencia guardada",
                    Toast.LENGTH_SHORT
                ).show()

                // Devuelve resultado correcto y cierra la activity
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                // Muestra mensaje de error si falla el guardado
                Toast.makeText(
                    this@NuevaIncidenciaActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                // Reactiva el botón y oculta el progreso para permitir reintento
                btnGuardar.isEnabled = true
                progressBar.isVisible = false
            }
        }
    }

    /**
     * Gestiona el botón atrás de la ActionBar.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            // Cierra la pantalla al pulsar atrás en la barra superior
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}