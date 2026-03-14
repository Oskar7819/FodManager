package com.example.fodmanager.ui.aeronaves

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.ui.usuarios.UsuarioAdapter
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.models.UsuarioRol
import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Clase de datos usada para actualizar el campo "activa" de la aeronave en Supabase.
// Solo contiene el campo que queremos modificar, evitando enviar datos innecesarios.
@Serializable
data class ActualizarAeronave(val activa: Boolean)

// Activity que muestra el detalle de una aeronave:
// - Información general (modelo, número de serie, ubicación, estado)
// - Lista de usuarios adscritos a esa aeronave
// - Botón para marcar la aeronave como inactiva (solo para administrador y focal_point_fod)
class DetalleAeronaveActivity : AppCompatActivity() {

    private lateinit var tvModelo: TextView
    private lateinit var tvNumeroSerie: TextView
    private lateinit var tvUbicacion: TextView
    private lateinit var tvEstado: TextView
    private lateinit var btnDesactivar: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsuarioAdapter
    private val usuarios = mutableListOf<Usuario>()

    // Roles que pueden marcar una aeronave como inactiva
    private val rolesConPermiso = listOf("administrador", "focal_point_fod")
    private var aeronaveId: Int = -1
    private var aeronaveActiva: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_aeronave)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle Aeronave"

        // Inicialización de los elementos visuales del layout
        tvModelo = findViewById(R.id.tvDetalleAeronaveModelo)
        tvNumeroSerie = findViewById(R.id.tvDetalleAeronaveNumeroSerie)
        tvUbicacion = findViewById(R.id.tvDetalleAeronaveUbicacion)
        tvEstado = findViewById(R.id.tvDetalleAeronaveEstado)
        btnDesactivar = findViewById(R.id.btnDesactivarAeronave)
        recyclerView = findViewById(R.id.recyclerUsuariosAeronave)

        // Recupera los datos de la aeronave enviados desde AeronaveFragment
        // mediante el Intent (mecanismo de comunicación entre pantallas en Android)
        aeronaveId = intent.getIntExtra("aeronave_id", -1)
        val modelo = intent.getStringExtra("aeronave_modelo") ?: ""
        val numeroSerie = intent.getStringExtra("aeronave_numero_serie") ?: ""
        val ubicacion = intent.getStringExtra("aeronave_ubicacion") ?: "Sin ubicación"
        aeronaveActiva = intent.getBooleanExtra("aeronave_activa", true)

        // Muestra los datos de la aeronave en los TextViews
        tvModelo.text = modelo
        tvNumeroSerie.text = "S/N: $numeroSerie"
        tvUbicacion.text = "📍 $ubicacion"
        tvEstado.text = if (aeronaveActiva) "🟢 Activa" else "🔴 Inactiva"

        // Crea el mapa de aeronaves para el adapter de usuarios
        // asociando el ID de la aeronave con su nombre para mostrarlo en las tarjetas
        val aeronaveNombre = "$modelo - $numeroSerie"
        val aeronavesMap = mapOf(aeronaveId to aeronaveNombre)
        adapter = UsuarioAdapter(usuarios, aeronavesMap) { }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Al pulsar el botón atrás del sistema, devuelve RESULT_OK al fragment anterior
        // para que recargue la lista de aeronaves y refleje los cambios
        onBackPressedDispatcher.addCallback(this) {
            setResult(RESULT_OK)
            finish()
        }

        cargarUsuarios(aeronaveId)
        verificarRol()

        btnDesactivar.setOnClickListener { desactivarAeronave() }
    }

    // Verifica el rol del usuario logueado para mostrar u ocultar el botón de desactivar.
    // Solo administrador y focal_point_fod pueden marcar una aeronave como inactiva.
    private fun verificarRol() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email
                val usuario = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<UsuarioRol>()

                // Muestra el botón solo si el rol tiene permiso Y la aeronave está activa
                if (usuario.rol in rolesConPermiso && aeronaveActiva) {
                    btnDesactivar.isVisible = true
                }
            } catch (e: Exception) {
                // Si falla la consulta, el botón permanece oculto por seguridad
            }
        }
    }

    // Carga desde Supabase los usuarios adscritos a esta aeronave
    // filtrando por aeronave_id en la tabla "usuarios"
    private fun cargarUsuarios(aeronaveId: Int) {
        lifecycleScope.launch {
            try {
                val resultado = supabase.postgrest["usuarios"]
                    .select { filter { eq("aeronave_id", aeronaveId) } }
                    .decodeList<Usuario>()

                usuarios.clear()
                usuarios.addAll(resultado)
                adapter.notifyDataSetChanged()

                if (usuarios.isEmpty()) {
                    Toast.makeText(this@DetalleAeronaveActivity, "Sin usuarios adscritos", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@DetalleAeronaveActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Marca la aeronave como inactiva en Supabase y desasigna a todos sus usuarios.
    // Esto ocurre cuando la aeronave abandona el hangar.
    // Una vez inactiva no se puede reactivar; si vuelve se registra como nueva aeronave.
    private fun desactivarAeronave() {
        lifecycleScope.launch {
            try {
                // Actualiza el campo "activa" a false en la tabla "aeronaves"
                supabase.postgrest["aeronaves"]
                    .update(ActualizarAeronave(activa = false)) {
                        filter { eq("id", aeronaveId) }
                    }

                // Desasigna todos los usuarios adscritos a esta aeronave
                // poniendo su aeronave_id a null en la tabla "usuarios"
                supabase.postgrest["usuarios"]
                    .update(mapOf("aeronave_id" to null)) {
                        filter { eq("aeronave_id", aeronaveId) }
                    }

                runOnUiThread {
                    Toast.makeText(this@DetalleAeronaveActivity, "Aeronave marcada como inactiva", Toast.LENGTH_SHORT).show()
                    tvEstado.text = "🔴 Inactiva"
                    btnDesactivar.isVisible = false
                    usuarios.clear()
                    adapter.notifyDataSetChanged()
                    // Notifica al fragment anterior que hubo cambios para que recargue la lista
                    setResult(RESULT_OK)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DetalleAeronaveActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Gestiona el botón de atrás de la ActionBar (flecha arriba izquierda)
    // devolviendo RESULT_OK para que el fragment recargue la lista de aeronaves
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_OK)
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}