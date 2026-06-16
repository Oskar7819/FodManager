package com.example.fodmanager.ui.incidencias

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.remote.EdgeFunctionsClient
import com.example.fodmanager.data.remote.NotificacionFodRequest
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import android.graphics.drawable.BitmapDrawable

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
 * Respuesta mínima devuelta por Supabase
 * al insertar una incidencia nueva.
 *
 * Solo necesitamos recuperar el ID generado
 * para poder enviarlo en la notificación push.
 */
@Serializable
data class IncidenciaCreadaResponse(
    // ID autogenerado de la incidencia
    val id: Int
)

/**
 * Pantalla para registrar una nueva incidencia FOD vinculada a una inspección previa.
 *
 * Mejora aplicada:
 * - Ya no se usa el thumbnail de la cámara.
 * - Ahora se captura la imagen completa con FileProvider.
 * - La imagen real se sube a Supabase Storage, mejorando la nitidez.
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

    // URI segura de la fotografía capturada
    private var photoUri: Uri? = null

    // Archivo temporal donde se guarda la imagen real
    private var photoFile: File? = null

    // ID de la inspección recibida por Intent
    private var inspeccionId: Int = -1

    // Usuario cargado desde Supabase
    private var usuarioLogueado: UsuarioIncidenciaActual? = null

    // Inspección origen cargada desde Supabase
    private var inspeccionOrigen: InspeccionOrigenIncidencia? = null

    /**
     * Roles autorizados para registrar incidencias FOD.
     */
    private val rolesQuePuedenRegistrarIncidencia = listOf(
        "operario",
        "mando_gp4",
        "quality"
    )

    /**
     * Valores internos del enum `tipo_fod`.
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
     * Valores visibles del tipo de FOD para el Spinner.
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
     * Valores internos de prioridad.
     */
    private val prioridades = listOf("baja", "alta")

    /**
     * Valores visibles de prioridad.
     */
    private val prioridadesMostrar = listOf(
        "🟢 Baja",
        "🔴 Alta"
    )

    /**
     * Launcher moderno para capturar la foto y guardarla directamente en el URI indicado.
     *
     * Si sale bien:
     * - la cámara escribe la foto real en photoFile
     * - se muestra la vista previa en imgPreview
     */
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null && photoFile?.exists() == true) {
            try {
                val bitmapCorregido = ImageUtils.decodeBitmapCorregido(photoFile!!.absolutePath)
                imgPreview.setImageBitmap(bitmapCorregido)
                imgPreview.isVisible = true
            } catch (e: Exception) {
                photoUri = null
                photoFile = null

                Toast.makeText(
                    this,
                    "No se pudo procesar la imagen capturada",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            photoUri = null
            photoFile = null

            Toast.makeText(
                this,
                "No se pudo obtener la imagen de la cámara",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Launcher para pedir el permiso de cámara.
     */
    private val permisoCamaraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            abrirCamara()
        } else {
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

        // Adaptador del spinner de prioridad
        val adapterPrioridad = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            prioridadesMostrar
        )
        adapterPrioridad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPrioridad.adapter = adapterPrioridad

        // Adaptador del spinner de tipo de FOD
        val adapterTipo = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            tiposFodMostrar
        )
        adapterTipo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTipoFod.adapter = adapterTipo

        // Recupera la inspección origen desde el Intent
        inspeccionId = intent.getIntExtra("inspeccion_id", -1)

        // Si la inspección no es válida, se cierra la pantalla
        if (inspeccionId == -1) {
            Toast.makeText(this, "Error: inspección no válida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Restaurar foto temporal si la activity se recrea
        val savedPath = savedInstanceState?.getString("photo_file_path")
        if (!savedPath.isNullOrBlank()) {
            val restoredFile = File(savedPath)

            if (restoredFile.exists()) {
                photoFile = restoredFile
                photoUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    restoredFile
                )

                try {
                    val bitmapCorregido = ImageUtils.decodeBitmapCorregido(restoredFile.absolutePath)
                    imgPreview.setImageBitmap(bitmapCorregido)
                    imgPreview.isVisible = true
                } catch (e: Exception) {
                    imgPreview.setImageURI(photoUri)
                    imgPreview.isVisible = true
                }
            }
        }

        // Carga inicial de datos
        cargarUsuarioLogueado()
        cargarInspeccionOrigen()

        // Eventos
        btnTomarFoto.setOnClickListener { verificarPermisoCamara() }
        btnGuardar.setOnClickListener { guardarIncidencia() }
    }

    /**
     * Guarda la ruta del archivo temporal para restaurarlo tras una recreación.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("photo_file_path", photoFile?.absolutePath)
    }

    /**
     * Carga el usuario logueado desde Supabase usando el email de la sesión.
     */
    private fun cargarUsuarioLogueado() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                if (email.isBlank()) {
                    Toast.makeText(
                        this@NuevaIncidenciaActivity,
                        "No hay sesión válida",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@launch
                }

                val usuario = supabase.postgrest["usuarios"]
                    .select {
                        filter {
                            eq("email", email)
                        }
                    }
                    .decodeList<UsuarioIncidenciaActual>()
                    .firstOrNull()

                if (usuario == null) {
                    Toast.makeText(
                        this@NuevaIncidenciaActivity,
                        "No se encontró el usuario logueado",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@launch
                }

                usuarioLogueado = usuario

                tvDeclaranteNombre.text = "Nombre: ${usuario.nombre}"
                tvDeclaranteApellidos.text = "Apellidos: ${usuario.apellidos}"
                tvDeclaranteNumeroEmpleado.text =
                    "Nº empleado: ${usuario.numeroEmpleado ?: "No especificado"}"

                if (usuario.rol !in rolesQuePuedenRegistrarIncidencia) {
                    Toast.makeText(
                        this@NuevaIncidenciaActivity,
                        "Tu rol no puede registrar incidencias FOD.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }

            } catch (e: Exception) {
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
     */
    private fun cargarInspeccionOrigen() {
        lifecycleScope.launch {
            try {
                val inspeccion = supabase.postgrest["inspecciones"]
                    .select {
                        filter {
                            eq("id", inspeccionId)
                        }
                    }
                    .decodeList<InspeccionOrigenIncidencia>()
                    .firstOrNull()

                if (inspeccion == null) {
                    Toast.makeText(
                        this@NuevaIncidenciaActivity,
                        "No se encontró la inspección",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@launch
                }

                inspeccionOrigen = inspeccion
                tvZonaAvion.text = "Zona: ${inspeccion.zona}"

                if (!inspeccion.conFod) {
                    btnGuardar.isEnabled = false
                    Toast.makeText(
                        this@NuevaIncidenciaActivity,
                        "Esta inspección está marcada sin FOD. No se puede registrar incidencia.",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@NuevaIncidenciaActivity,
                    "Error cargando inspección: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Comprueba si existe permiso de cámara.
     * Si no existe, lo solicita.
     */
    private fun verificarPermisoCamara() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            abrirCamara()
        } else {
            permisoCamaraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Abre la cámara y prepara el archivo temporal donde se guardará la foto real.
     */
    private fun abrirCamara() {
        val intentCamara = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        if (intentCamara.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "No hay aplicación de cámara disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val archivo = crearArchivoImagen()
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                archivo
            )

            photoFile = archivo
            photoUri = uri

            takePictureLauncher.launch(uri)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No se pudo preparar la cámara: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Crea un archivo temporal JPG dentro de cacheDir.
     */
    private fun crearArchivoImagen(): File {
        val nombreArchivo = "FOD_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        return File.createTempFile(nombreArchivo, ".jpg", cacheDir)
    }

    /**
     * Guarda la incidencia FOD en Supabase.
     *
     * Pasos:
     * 1. Validar usuario, inspección, descripción y foto.
     * 2. Leer el archivo real.
     * 3. Subir imagen al bucket `fod-images`.
     * 4. Obtener URL pública.
     * 5. Insertar registro en `incidencias_fod`.
     * 6. Si la prioridad es alta, solicitar a Supabase el envío de una notificación push mediante OneSignal.
     */
    private fun guardarIncidencia() {
        val usuario = usuarioLogueado ?: run {
            Toast.makeText(this, "No se ha podido cargar el usuario logueado", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val inspeccion = inspeccionOrigen ?: run {
            Toast.makeText(this, "No se ha podido cargar la inspección origen", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (usuario.rol !in rolesQuePuedenRegistrarIncidencia) {
            Toast.makeText(this, "Tu rol no puede registrar incidencias FOD.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (!inspeccion.conFod) {
            Toast.makeText(this, "La inspección origen no tiene FOD", Toast.LENGTH_SHORT).show()
            return
        }

        val descripcion = etDescripcion.text?.toString()?.trim().orEmpty()
        val tipoFod = tiposFod[spinnerTipoFod.selectedItemPosition]
        val prioridad = prioridades[spinnerPrioridad.selectedItemPosition]

        if (descripcion.isBlank()) {
            etDescripcion.error = "La descripción es obligatoria"
            return
        }

        val archivoImagen = photoFile
        if (archivoImagen == null || !archivoImagen.exists()) {
            Toast.makeText(this, "La foto es obligatoria", Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                // Nombre único del archivo en Storage
                val nombreArchivo = "fod_${UUID.randomUUID()}.jpg"

                // Leer la foto corrigiendo antes la orientación EXIF
                val bitmapCorregido = ImageUtils.decodeBitmapCorregido(archivoImagen.absolutePath)

                // Escalar imagen manteniendo proporción (máx 1280 px)
                val resizedBitmap = ImageUtils.escalarBitmap(bitmapCorregido, 1280)

                // Comprimir JPEG
                val stream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)

                val bytesImagen = stream.toByteArray()

                // Subir al bucket de Storage
                supabase.storage["fod-images"].upload(
                    nombreArchivo,
                    bytesImagen
                )

                // Obtener URL pública
                val imagenUrl = supabase.storage["fod-images"].publicUrl(nombreArchivo)

                // Insertar incidencia y recuperar el ID generado por Supabase.
                // Este ID se usará después para incluirlo en los datos de la notificación push.
                val incidenciaCreada = supabase.postgrest["incidencias_fod"].insert(
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
                ) {
                    // Indicamos a Supabase que devuelva los datos insertados.
                    // En este caso solo necesitamos el campo id.
                    select()
                }.decodeSingle<IncidenciaCreadaResponse>()

                // Si la prioridad seleccionada es "alta",
                // se llama a la Edge Function de Supabase.
                //
                // La Edge Function será la encargada de:
                // - buscar los usuarios responsables,
                // - filtrar por rol y aeronave,
                // - enviar la notificación push mediante OneSignal.
                //
                // Si la prioridad es "baja", no se envía ninguna notificación.
                if (prioridad == "alta") {
                    EdgeFunctionsClient.enviarNotificacionFod(
                        NotificacionFodRequest(
                            // ID de la incidencia recién creada
                            incidencia_id = incidenciaCreada.id,

                            // Prioridad seleccionada
                            prioridad = prioridad,

                            // Aeronave asociada a la inspección/incidencia
                            aeronave_id = inspeccion.aeronaveId,

                            // Zona donde se encontró el FOD
                            zona = inspeccion.zona,

                            // Descripción de la incidencia
                            descripcion = descripcion
                        )
                    )
                }

                Toast.makeText(
                    this@NuevaIncidenciaActivity,
                    "Incidencia guardada",
                    Toast.LENGTH_SHORT
                ).show()

                // Borra el archivo temporal local tras subirlo
                archivoImagen.delete()

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@NuevaIncidenciaActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

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
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}