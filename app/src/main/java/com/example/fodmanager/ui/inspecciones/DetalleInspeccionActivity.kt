package com.example.fodmanager.ui.inspecciones

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.ui.incidencias.NuevaIncidenciaActivity
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Clase auxiliar para deserializar únicamente el nombre del usuario desde Supabase.
// Se usa para mostrar el nombre del inspector en el detalle de la inspección.
@Serializable
data class UsuarioNombre(val nombre: String)

// Activity que muestra el detalle completo de una inspección:
// - Zona inspeccionada, fecha, si se encontró FOD, observaciones y nombre del inspector
// - Botón para registrar una nueva incidencia FOD asociada a esta inspección
class DetalleInspeccionActivity : AppCompatActivity() {

    private lateinit var tvZona: TextView
    private lateinit var tvFecha: TextView
    private lateinit var tvConFod: TextView
    private lateinit var tvObservaciones: TextView
    private lateinit var tvUsuario: TextView
    private var inspeccionId: Int = -1
    // ID de la aeronave asociada a la inspección, se pasa a NuevaIncidenciaActivity
    private var aeronaveId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_inspeccion)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle de Inspección"

        // Inicialización de los elementos visuales del layout
        tvZona = findViewById(R.id.tvDetalleZona)
        tvFecha = findViewById(R.id.tvDetalleFecha)
        tvConFod = findViewById(R.id.tvDetalleConFod)
        tvObservaciones = findViewById(R.id.tvDetalleObservaciones)
        tvUsuario = findViewById(R.id.tvDetalleUsuario)

        // Recupera el ID de la inspección enviado desde InspeccionesFragment mediante el Intent
        inspeccionId = intent.getIntExtra("inspeccion_id", -1)
        if (inspeccionId == -1) {
            Toast.makeText(this, "Error al cargar inspección", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Botón para registrar una nueva incidencia FOD asociada a esta inspección.
        // Pasa el ID de la inspección y el ID de la aeronave a NuevaIncidenciaActivity
        val btnNuevaIncidencia = findViewById<Button>(R.id.btnNuevaIncidencia)
        btnNuevaIncidencia.setOnClickListener {
            val intent = Intent(this, NuevaIncidenciaActivity::class.java)
            intent.putExtra("inspeccion_id", inspeccionId)
            // Solo añade aeronave_id al Intent si la inspección tiene aeronave asignada
            aeronaveId?.let { intent.putExtra("aeronave_id", it) }
            startActivity(intent)
        }

        cargarDetalle(inspeccionId)
    }

    // Carga desde Supabase los datos completos de la inspección y el nombre del inspector
    private fun cargarDetalle(id: Int) {
        lifecycleScope.launch {
            try {
                // Obtiene la inspección completa filtrando por su ID
                val inspeccion = supabase.postgrest["inspecciones"]
                    .select {
                        filter { eq("id", id) }
                    }
                    .decodeSingle<Inspeccion>()

                // Guarda el ID de la aeronave para pasarlo al crear una incidencia
                aeronaveId = inspeccion.aeronaveId

                tvZona.text = "Zona: ${inspeccion.zona}"

                // Formatea la fecha del formato ISO (2024-01-15T08:30:00) a dd/mm/yyyy HH:mm
                val fechaFormateada = inspeccion.fecha?.let {
                    try {
                        val partes = it.split("T")
                        val fecha = partes[0]
                        val hora = partes[1].substring(0, 5)
                        val (anio, mes, dia) = fecha.split("-")
                        "$dia/$mes/$anio  $hora"
                    } catch (e: Exception) { it }
                } ?: "Sin fecha"
                tvFecha.text = "Fecha: $fechaFormateada"

                // Muestra si se encontró FOD con emoji indicativo
                tvConFod.text = if (inspeccion.conFod) "⚠️ Con FOD" else "✅ Sin FOD"
                tvObservaciones.text = inspeccion.observaciones ?: "Sin observaciones"

                // Consulta el nombre del inspector haciendo una segunda petición a Supabase
                // usando el usuario_id de la inspección para buscar en la tabla "usuarios"
                val usuario = supabase.postgrest["usuarios"]
                    .select {
                        filter { eq("id", inspeccion.usuarioId) }
                    }
                    .decodeSingle<UsuarioNombre>()

                tvUsuario.text = usuario.nombre

            } catch (e: Exception) {
                Toast.makeText(this@DetalleInspeccionActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Gestiona el botón de atrás de la ActionBar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}