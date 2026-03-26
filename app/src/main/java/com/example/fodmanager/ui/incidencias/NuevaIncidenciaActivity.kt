package com.example.fodmanager.ui.incidencias

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Payload para el INSERT en la tabla `incidencias_fod`.
 * La zona se hereda de la inspección origen (no la introduce el usuario).
 * El estado inicial siempre es "abierta".
 */
@Serializable
data class InsertIncidenciaFodPayload(
    @SerialName("inspeccion_id") val inspeccionId: Int,
    @SerialName("usuario_id") val usuarioId: Int,
    @SerialName("aeronave_id") val aeronaveId: Int?,
    val descripcion: String,
    @SerialName("zona_avion") val zonaAvion: String?,
    @SerialName("numero_empleado") val numeroEmpleado: String?,
    @SerialName("imagen_url") val imagenUrl: String?,
    val estado: String = "abierta",
    @SerialName("tipo_fod") val tipoFod: String?,
    val prioridad: String?
)

/**
 * Proyección del usuario logueado con los campos necesarios para registrar
 * la incidencia y verificar que su rol tiene permiso.
 */
@Serializable
data class UsuarioIncidenciaActual(
    val id: Int,
    val nombre: String,
    val apellidos: String,
    val rol: String,
    @SerialName("numero_empleado") val numeroEmpleado: String? = null
)

/**
 * Proyección de la inspección origen con los campos necesarios para
 * crear la incidencia vinculada a ella.
 */
@Serializable
data class InspeccionOrigenIncidencia(
    val id: Int,
    val zona: String,
    @SerialName("aeronave_id") val aeronaveId: Int? = null,
    @SerialName("con_fod") val conFod: Boolean
)

/**
 * Activity que muestra el formulario para registrar una nueva incidencia FOD.
 *
 * La incidencia siempre está vinculada a una InspeccionOrigenIncidencia previa.
 * De ella hereda la zona inspeccionada y la aeronave asociada, por lo que el usuario
 * no puede modificar esos campos: solo introduce descripción, tipo de FOD y foto.
 *
 * Restricciones de acceso:
 * - Solo los roles en rolesQuePuedenRegistrarIncidencia pueden llegar a esta pantalla.
 *   `focal_point_fod` y `head_plant` son rechazados al cargar el usuario.
 * - Si la inspección origen tiene `conFod = false`, el botón Guardar se deshabilita.
 *
 * Flujo de guardado:
 * 1. Comprimir la foto a JPEG (80 % de calidad) y subirla a Supabase Storage (`fod-images`).
 * 2. Obtener la URL pública de la imagen.
 * 3. Insertar el registro en la tabla `incidencias_fod`.
 * 4. Devolver RESULT_OK y cerrar la activity.
 */
class NuevaIncidenciaActivity : AppCompatActivity() {

    private lateinit var etDescripcion: TextInputEditText
    private lateinit var btnTomarFoto: Button
    private lateinit var imgPreview: ImageView
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerTipoFod: Spinner

    private lateinit var tvDeclaranteNombre: TextView
    private lateinit var tvDeclaranteApellidos: TextView
    private lateinit var tvDeclaranteNumeroEmpleado: TextView
    private lateinit var tvZonaAvion: TextView

    private lateinit var spinnerPrioridad: Spinner

    /** Bitmap capturado por la cámara; es obligatorio antes de guardar. */
    private var fotoBitmap: Bitmap? = null

    private var inspeccionId: Int = -1
    private var usuarioLogueado: UsuarioIncidenciaActual? = null
    private var inspeccionOrigen: InspeccionOrigenIncidencia? = null

    /** Roles que pueden registrar incidencias FOD. `focal_point_fod` y `head_plant` quedan excluidos. */
    private val rolesQuePuedenRegistrarIncidencia = listOf("operario", "mando_gp4", "quality")

    /** Valores internos del ENUM `tipo_fod` usados al insertar el registro. */
    private val tiposFod = listOf(
        "ambiental", "herramientas", "restos_metalicos",
        "material_consumo", "personal", "procedente_aeronave"
    )

    /** Textos legibles con emojis mostrados en el Spinner al usuario. */
    private val tiposFodMostrar = listOf(
        "🌫️ Ambiental (suciedad y polvo)",
        "🔧 Herramientas (llaves, destornilladores...)",
        "🔩 Restos metálicos (tornillos, remaches...)",
        "🧤 Material de consumo (trapos, guantes...)",
        "👤 Personal (bolígrafos, monedas...)",
        "✈️ Procedente de aeronave (sellante, pintura...)"
    )

    private val prioridades = listOf("baja", "alta")

    private val prioridadesMostrar = listOf(
        "🟢 Baja",
        "🔴 Alta"

    )

    /**
     * Launcher para la cámara. Si el resultado es exitoso, almacena el Bitmap
     * en fotoBitmap y lo muestra en el preview.
     */
    private val camaraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                fotoBitmap = bitmap
                imgPreview.setImageBitmap(bitmap)
                imgPreview.isVisible = true
            }
        }
    }

    /**
     * Launcher para solicitar el permiso de cámara.
     * Si se concede, abre la cámara directamente; si se deniega, muestra un aviso.
     */
    private val permisoCamaraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) abrirCamara()
        else Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_incidencia)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nueva Incidencia FOD"

        etDescripcion = findViewById(R.id.etDescripcion)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        imgPreview = findViewById(R.id.imgPreview)
        btnGuardar = findViewById(R.id.btnGuardar)
        progressBar = findViewById(R.id.progressBar)
        spinnerTipoFod = findViewById(R.id.spinnerTipoFod)
        tvDeclaranteNombre = findViewById(R.id.tvDeclaranteNombre)
        tvDeclaranteApellidos = findViewById(R.id.tvDeclaranteApellidos)
        tvDeclaranteNumeroEmpleado = findViewById(R.id.tvDeclaranteNumeroEmpleado)
        tvZonaAvion = findViewById(R.id.tvZonaAvion)
        spinnerPrioridad = findViewById(R.id.spinnerPrioridad)


        val adapterPrioridad = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            prioridadesMostrar
        )
        adapterPrioridad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPrioridad.adapter = adapterPrioridad

        // El Spinner muestra textos legibles pero al guardar se usa el código interno de tiposFod
        val adapterTipo = ArrayAdapter(this, android.R.layout.simple_spinner_item, tiposFodMostrar)
        adapterTipo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTipoFod.adapter = adapterTipo

        inspeccionId = intent.getIntExtra("inspeccion_id", -1)

        if (inspeccionId == -1) {
            Toast.makeText(this, "Error: inspección no válida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        cargarUsuarioLogueado()
        cargarInspeccionOrigen()

        btnTomarFoto.setOnClickListener { verificarPermisoCamara() }
        btnGuardar.setOnClickListener { guardarIncidencia() }
    }

    /**
     * Carga el usuario logueado desde Supabase, rellena sus datos en la UI
     * y verifica que su rol esté en rolesQuePuedenRegistrarIncidencia.
     * Si no tiene permiso, cierra la activity con un mensaje informativo.
     */
    private fun cargarUsuarioLogueado() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                val usuario = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email) } }
                    .decodeSingle<UsuarioIncidenciaActual>()

                usuarioLogueado = usuario

                tvDeclaranteNombre.text = "Nombre: ${usuario.nombre}"
                tvDeclaranteApellidos.text = "Apellidos: ${usuario.apellidos}"
                tvDeclaranteNumeroEmpleado.text = "Nº empleado: ${usuario.numeroEmpleado ?: "No especificado"}"

                if (usuario.rol !in rolesQuePuedenRegistrarIncidencia) {
                    Toast.makeText(this@NuevaIncidenciaActivity, "Tu rol no puede registrar incidencias FOD.", Toast.LENGTH_LONG).show()
                    finish()
                }

            } catch (e: Exception) {
                Toast.makeText(this@NuevaIncidenciaActivity, "Error cargando declarante: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Carga la inspección origen y muestra su zona en la UI.
     * Si la inspección tiene `conFod = false`, deshabilita el botón Guardar:
     * no tiene sentido registrar un FOD en una inspección sin FOD declarado.
     */
    private fun cargarInspeccionOrigen() {
        lifecycleScope.launch {
            try {
                val inspeccion = supabase.postgrest["inspecciones"]
                    .select { filter { eq("id", inspeccionId) } }
                    .decodeSingle<InspeccionOrigenIncidencia>()

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
                Toast.makeText(this@NuevaIncidenciaActivity, "Error cargando inspección: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Comprueba si el permiso de cámara ya está concedido.
     * Si lo está, abre la cámara directamente; si no, lo solicita con [permisoCamaraLauncher].
     */
    private fun verificarPermisoCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        } else {
            permisoCamaraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /** Lanza la cámara del dispositivo en modo captura de imagen. */
    private fun abrirCamara() {
        camaraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }

    /**
     * Valida los datos del formulario y guarda la incidencia en Supabase.
     *
     * Validaciones previas:
     * - Usuario y aeronave cargados correctamente.
     * - Rol con permiso (doble verificación defensiva).
     * - Inspección con `conFod = true`.
     * - Descripción no vacía.
     * - Foto obligatoria.
     *
     * Proceso de guardado:
     * 1. Comprimir [fotoBitmap] a JPEG (80 %) y subir al bucket `fod-images`.
     * 2. Obtener la URL pública de la imagen subida.
     * 3. Insertar [InsertIncidenciaFodPayload] en `incidencias_fod`.
     */
    private fun guardarIncidencia() {
        val usuario = usuarioLogueado ?: run {
            Toast.makeText(this, "No se ha podido cargar el usuario logeado", Toast.LENGTH_SHORT).show()
            return
        }
        val inspeccion = inspeccionOrigen ?: run {
            Toast.makeText(this, "No se ha podido cargar la inspección origen", Toast.LENGTH_SHORT).show()
            return
        }

        if (usuario.rol !in rolesQuePuedenRegistrarIncidencia) {
            Toast.makeText(this, "Tu rol no puede registrar incidencias FOD.", Toast.LENGTH_SHORT).show()
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
        if (fotoBitmap == null) {
            Toast.makeText(this, "La foto es obligatoria", Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                // Comprimir y subir la imagen al bucket "fod-images"
                val stream = ByteArrayOutputStream()
                fotoBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val nombreArchivo = "fod_${UUID.randomUUID()}.jpg"
                supabase.storage["fod-images"].upload(nombreArchivo, stream.toByteArray())
                val imagenUrl = supabase.storage["fod-images"].publicUrl(nombreArchivo)

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

                runOnUiThread {
                    Toast.makeText(this@NuevaIncidenciaActivity, "Incidencia guardada", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NuevaIncidenciaActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    btnGuardar.isEnabled = true
                    progressBar.isVisible = false
                }
            }
        }
    }

    /** Gestiona el botón de atrás de la ActionBar. */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}