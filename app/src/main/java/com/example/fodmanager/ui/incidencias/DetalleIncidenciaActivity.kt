package com.example.fodmanager.ui.incidencias

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.IncidenciaFod
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.example.fodmanager.data.models.UsuarioRol
import com.example.fodmanager.data.remote.supabase

// Clase de datos usada para actualizar únicamente el campo "estado" de la incidencia en Supabase.
// Solo contiene el campo que queremos modificar, evitando enviar datos innecesarios.
@Serializable
data class ActualizarEstado(val estado: String)

// Activity que muestra el detalle completo de una incidencia FOD:
// - Aeronave, estado, fecha, tipo de FOD, descripción, zona, empleado y foto
// - Botones para cambiar el estado (solo para administrador, mando_gp4 y quality)
// El flujo de estados es: abierta → en_proceso → cerrada (o abierta → cerrada directamente)
class DetalleIncidenciaActivity : AppCompatActivity() {

    private lateinit var tvAeronave: TextView
    private lateinit var tvEstado: TextView
    private lateinit var tvFecha: TextView
    private lateinit var tvTipoFod: TextView
    private lateinit var tvDescripcion: TextView
    private lateinit var tvZona: TextView
    private lateinit var tvEmpleado: TextView
    private lateinit var imgFoto: ImageView
    private lateinit var tvSinFoto: TextView
    // LinearLayout que contiene los botones de cambio de estado,
    // oculto por defecto y visible solo para roles con permiso
    private lateinit var llBotonesEstado: LinearLayout
    private lateinit var btnEnProceso: Button
    private lateinit var btnCerrar: Button

    // Roles que pueden cambiar el estado de una incidencia
    private val rolesConPermiso = listOf("administrador", "mando_gp4", "quality")
    private var incidenciaId: Int = -1
    private var estadoActual: String = "abierta"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_incidencia)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle Incidencia FOD"

        // Inicialización de los elementos visuales del layout
        tvAeronave = findViewById(R.id.tvDetalleIncidenciaAeronave)
        tvEstado = findViewById(R.id.tvDetalleIncidenciaEstado)
        tvFecha = findViewById(R.id.tvDetalleIncidenciaFecha)
        tvTipoFod = findViewById(R.id.tvDetalleIncidenciaTipoFod)
        tvDescripcion = findViewById(R.id.tvDetalleIncidenciaDescripcion)
        tvZona = findViewById(R.id.tvDetalleIncidenciaZona)
        tvEmpleado = findViewById(R.id.tvDetalleIncidenciaEmpleado)
        imgFoto = findViewById(R.id.imgDetalleIncidencia)
        tvSinFoto = findViewById(R.id.tvSinFoto)
        llBotonesEstado = findViewById(R.id.llBotonesEstado)
        btnEnProceso = findViewById(R.id.btnEnProceso)
        btnCerrar = findViewById(R.id.btnCerrar)

        // Recupera el ID de la incidencia enviado desde IncidenciasFragment mediante el Intent
        incidenciaId = intent.getIntExtra("incidencia_id", -1)
        if (incidenciaId == -1) {
            Toast.makeText(this, "Error al cargar incidencia", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Asigna los listeners a los botones de cambio de estado
        btnEnProceso.setOnClickListener { cambiarEstado("en_proceso") }
        btnCerrar.setOnClickListener { cambiarEstado("cerrada") }

        cargarDetalle(incidenciaId)
    }

    // Carga desde Supabase todos los datos de la incidencia y los muestra en pantalla
    private fun cargarDetalle(id: Int) {
        lifecycleScope.launch {
            try {
                // Obtiene la incidencia completa filtrando por su ID
                val incidencia = supabase.postgrest["incidencias_fod"]
                    .select { filter { eq("id", id) } }
                    .decodeSingle<IncidenciaFod>()

                estadoActual = incidencia.estado

                // Obtiene el nombre de la aeronave haciendo una consulta adicional
                // si la incidencia tiene aeronave asignada
                val aeronaveTexto = incidencia.aeronaveId?.let {
                    val aeronave = supabase.postgrest["aeronaves"]
                        .select { filter { eq("id", it) } }
                        .decodeSingle<Aeronave>()
                    "${aeronave.modelo} - ${aeronave.numeroSerie}"
                } ?: "Sin aeronave"

                tvAeronave.text = aeronaveTexto

                // Muestra el estado con emoji de color según su valor
                tvEstado.text = when (incidencia.estado) {
                    "abierta" -> "🔴 Abierta"
                    "en_proceso" -> "🟡 En proceso"
                    "cerrada" -> "🟢 Cerrada"
                    else -> incidencia.estado
                }

                // Formatea la fecha del formato ISO (2024-01-15T08:30:00) a dd/mm/yyyy HH:mm
                val fechaFormateada = incidencia.createdAt?.let {
                    try {
                        val partes = it.split("T")
                        val fecha = partes[0]
                        val hora = partes[1].substring(0, 5)
                        val (anio, mes, dia) = fecha.split("-")
                        "$dia/$mes/$anio  $hora"
                    } catch (e: Exception) { it }
                } ?: "Sin fecha"
                tvFecha.text = "Fecha: $fechaFormateada"

                // Muestra el tipo de FOD con descripción completa y emoji representativo
                tvTipoFod.text = when (incidencia.tipoFod) {
                    "ambiental" -> "🌫️ Ambiental (suciedad y polvo)"
                    "herramientas" -> "🔧 Herramientas (llaves, destornilladores...)"
                    "restos_metalicos" -> "🔩 Restos metálicos (tornillos, remaches...)"
                    "material_consumo" -> "🧤 Material de consumo (trapos, guantes...)"
                    "personal" -> "👤 Personal (bolígrafos, monedas...)"
                    "procedente_aeronave" -> "✈️ Procedente de aeronave (sellante, pintura...)"
                    else -> "Sin clasificar"
                }

                tvDescripcion.text = incidencia.descripcion
                tvZona.text = incidencia.zonaAvion ?: "No especificada"
                tvEmpleado.text = incidencia.numeroEmpleado ?: "No especificado"

                // Carga la foto usando Glide (librería de carga de imágenes)
                // Si no hay foto muestra el texto "Sin foto disponible"
                if (!incidencia.imagenUrl.isNullOrEmpty()) {
                    imgFoto.isVisible = true
                    Glide.with(this@DetalleIncidenciaActivity)
                        .load(incidencia.imagenUrl)
                        .into(imgFoto)
                } else {
                    tvSinFoto.isVisible = true
                }

                // Verifica el rol del usuario logueado para mostrar u ocultar los botones de estado
                val email = supabase.auth.currentSessionOrNull()?.user?.email
                val usuarioJson = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<UsuarioRol>()

                // Muestra los botones solo si el rol tiene permiso Y la incidencia no está cerrada
                if (usuarioJson.rol in rolesConPermiso && incidencia.estado != "cerrada") {
                    llBotonesEstado.isVisible = true
                    when (incidencia.estado) {
                        // Si está abierta muestra ambos botones: pasar a en_proceso o cerrar
                        "abierta" -> {
                            btnEnProceso.isVisible = true
                            btnCerrar.isVisible = true
                        }
                        // Si está en proceso solo muestra el botón de cerrar
                        "en_proceso" -> {
                            btnEnProceso.isVisible = false
                            btnCerrar.isVisible = true
                        }
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(this@DetalleIncidenciaActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Actualiza el estado de la incidencia en Supabase y recarga el detalle
    // para reflejar el cambio en la UI inmediatamente
    private fun cambiarEstado(nuevoEstado: String) {
        lifecycleScope.launch {
            try {
                supabase.postgrest["incidencias_fod"]
                    .update(ActualizarEstado(estado = nuevoEstado)) {
                        filter { eq("id", incidenciaId) }
                    }

                runOnUiThread {
                    Toast.makeText(this@DetalleIncidenciaActivity, "Estado actualizado", Toast.LENGTH_SHORT).show()
                    // Recarga el detalle para actualizar el estado y los botones visibles
                    cargarDetalle(incidenciaId)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DetalleIncidenciaActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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