package com.example.fodmanager.ui.incidencias

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.MenuItem
import android.widget.*
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
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.UUID

// Clase de datos que representa la estructura del INSERT en la tabla "incidencias_fod" de Supabase.
// Contiene todos los campos necesarios para registrar una nueva incidencia FOD.
@Serializable
data class NuevaIncidencia(
    val inspeccion_id: Int,
    val usuario_id: Int,
    val aeronave_id: Int?,
    val descripcion: String,
    val zona_avion: String?,
    val numero_empleado: String?,
    val imagen_url: String?,
    // Las nuevas incidencias siempre se crean con estado "abierta"
    val estado: String = "abierta",
    val tipo_fod: String?
)

// Activity que muestra el formulario para registrar una nueva incidencia FOD.
// Se accede desde DetalleInspeccionActivity cuando se detecta un FOD durante una inspección.
// Permite capturar foto con la cámara del dispositivo y subirla a Supabase Storage.
class NuevaIncidenciaActivity : AppCompatActivity() {

    private lateinit var etDescripcion: TextInputEditText
    private lateinit var etZonaAvion: TextInputEditText
    private lateinit var etNumeroEmpleado: TextInputEditText
    private lateinit var btnTomarFoto: Button
    private lateinit var imgPreview: ImageView
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerTipoFod: Spinner

    // Almacena el bitmap de la foto capturada antes de subirla a Supabase Storage
    private var fotoBitmap: Bitmap? = null
    private var inspeccionId: Int = -1
    private var aeronaveId: Int? = null

    // Lista con los valores del ENUM tipo_fod definido en Supabase
    // Se usa para obtener el valor correcto al guardar en la base de datos
    private val tiposFod = listOf(
        "ambiental",
        "herramientas",
        "restos_metalicos",
        "material_consumo",
        "personal",
        "procedente_aeronave"
    )

    // Lista con los textos descriptivos que se muestran al usuario en el Spinner
    // Cada posición corresponde al mismo índice en tiposFod
    private val tiposFodMostrar = listOf(
        "🌫️ Ambiental (suciedad y polvo)",
        "🔧 Herramientas (llaves, destornilladores...)",
        "🔩 Restos metálicos (tornillos, remaches...)",
        "🧤 Material de consumo (trapos, guantes...)",
        "👤 Personal (bolígrafos, monedas...)",
        "✈️ Procedente de aeronave (sellante, pintura...)"
    )

    // Launcher para recibir la foto capturada por la cámara.
    // La foto llega como Bitmap en los extras del resultado (data["data"])
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

    // Launcher para solicitar el permiso de cámara al usuario.
    // Si el usuario lo concede abre la cámara, si lo deniega muestra un mensaje
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

        // Inicialización de los elementos visuales del layout
        etDescripcion = findViewById(R.id.etDescripcion)
        etZonaAvion = findViewById(R.id.etZonaAvion)
        etNumeroEmpleado = findViewById(R.id.etNumeroEmpleado)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        imgPreview = findViewById(R.id.imgPreview)
        btnGuardar = findViewById(R.id.btnGuardar)
        progressBar = findViewById(R.id.progressBar)
        spinnerTipoFod = findViewById(R.id.spinnerTipoFod)

        // Configura el Spinner de tipo FOD con los textos descriptivos
        val adapterTipo = ArrayAdapter(this, android.R.layout.simple_spinner_item, tiposFodMostrar)
        adapterTipo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTipoFod.adapter = adapterTipo

        // Recupera el ID de la inspección y la aeronave enviados desde DetalleInspeccionActivity
        inspeccionId = intent.getIntExtra("inspeccion_id", -1)
        // takeIf { it != -1 } convierte -1 (valor por defecto) en null
        aeronaveId = intent.getIntExtra("aeronave_id", -1).takeIf { it != -1 }

        // Si no se recibió un ID de inspección válido, cierra la Activity
        if (inspeccionId == -1) {
            Toast.makeText(this, "Error: inspección no válida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnTomarFoto.setOnClickListener { verificarPermisoCamara() }
        btnGuardar.setOnClickListener { guardarIncidencia() }
    }

    // Verifica si la app tiene permiso de cámara antes de abrirla.
    // Si ya tiene permiso abre la cámara directamente,
    // si no lo tiene solicita el permiso al usuario
    private fun verificarPermisoCamara() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> abrirCamara()
            else -> permisoCamaraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Abre la cámara del dispositivo usando el Intent estándar de Android
    // ACTION_IMAGE_CAPTURE devuelve la foto como Bitmap en baja resolución
    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        camaraLauncher.launch(intent)
    }

    // Valida los campos, sube la foto a Supabase Storage e inserta la incidencia en la BD
    private fun guardarIncidencia() {
        val descripcion = etDescripcion.text.toString().trim()
        val zonaAvion = etZonaAvion.text.toString().trim()
        val numeroEmpleado = etNumeroEmpleado.text.toString().trim()
        // Obtiene el valor del ENUM usando el índice seleccionado en el Spinner
        val tipoFod = tiposFod[spinnerTipoFod.selectedItemPosition]

        // Validaciones: descripción y foto son obligatorias
        if (descripcion.isEmpty()) {
            etDescripcion.error = "La descripción es obligatoria"
            return
        }
        if (fotoBitmap == null) {
            Toast.makeText(this, "La foto es obligatoria", Toast.LENGTH_SHORT).show()
            return
        }

        // Deshabilita el botón y muestra el ProgressBar para evitar
        // pulsaciones múltiples mientras se procesa la petición
        btnGuardar.isEnabled = false
        progressBar.isVisible = true

        lifecycleScope.launch {
            try {
                // Obtiene el ID del usuario logueado para asociarlo a la incidencia
                val session = supabase.auth.currentSessionOrNull()
                val email = session?.user?.email
                val usuarioResult = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<UsuarioId>()

                // Convierte el Bitmap a array de bytes JPEG con calidad 80%
                // para subirlo a Supabase Storage
                val stream = ByteArrayOutputStream()
                fotoBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val bytes = stream.toByteArray()

                // Genera un nombre único para el archivo usando UUID
                // para evitar colisiones entre diferentes fotos
                val nombreArchivo = "fod_${UUID.randomUUID()}.jpg"

                // Sube la foto al bucket "fod-images" de Supabase Storage
                supabase.storage["fod-images"].upload(nombreArchivo, bytes)

                // Obtiene la URL pública de la foto para guardarla en la base de datos
                val imagenUrl = supabase.storage["fod-images"].publicUrl(nombreArchivo)

                val nuevaIncidencia = NuevaIncidencia(
                    inspeccion_id = inspeccionId,
                    usuario_id = usuarioResult.id,
                    aeronave_id = aeronaveId,
                    descripcion = descripcion,
                    // ifEmpty convierte cadenas vacías en null para no guardar strings vacíos
                    zona_avion = zonaAvion.ifEmpty { null },
                    numero_empleado = numeroEmpleado.ifEmpty { null },
                    imagen_url = imagenUrl,
                    tipo_fod = tipoFod
                )

                // Inserta la nueva incidencia en la tabla "incidencias_fod" de Supabase
                supabase.postgrest["incidencias_fod"].insert(nuevaIncidencia)

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

    // Gestiona el botón de atrás de la ActionBar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    // Clase auxiliar definida dentro de la Activity para deserializar
    // únicamente el ID del usuario desde Supabase
    @Serializable
    data class UsuarioId(val id: Int)
}