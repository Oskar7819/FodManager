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

/**
 * Payload mínimo para actualizar únicamente el campo activa de una aeronave.
 * Enviar solo el campo que cambia evita sobreescribir accidentalmente otros datos
 * con valores vacíos o por defecto.
 */
@Serializable
data class ActualizarAeronave(val activa: Boolean)

/**
 * Activity que muestra el detalle completo de una aeronave.
 *
 * Contenido de la pantalla:
 * - Datos identificativos: modelo, número de serie, ubicación y estado.
 * - Lista de usuarios actualmente adscritos a esa aeronave.
 * - Botón "Desactivar aeronave", visible solo para rolesConPermiso y únicamente
 *   si la aeronave está activa.
 *
 * Flujo de navegación:
 * Los datos llegan desde AeronaveFragment mediante extras del Intent.
 * Al regresar (botón atrás del sistema), se devuelve RESULT_OK
 * para que el fragment recargue la lista y refleje cualquier cambio de estado.
 *
 * Efecto de desactivar una aeronave:
 * - Se marca `activa = false` en la tabla `aeronaves`.
 * - Se pone `aeronave_id = null` a todos los usuarios adscritos.
 * - La operación es irreversible: si la aeronave regresa al hangar, se registra
 *   como una aeronave nueva para no mezclar datos históricos de ambos eventos.
 */
class DetalleAeronaveActivity : AppCompatActivity() {

    private lateinit var tvModelo: TextView
    private lateinit var tvNumeroSerie: TextView
    private lateinit var tvUbicacion: TextView
    private lateinit var tvEstado: TextView
    private lateinit var btnDesactivar: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsuarioAdapter
    private val usuarios = mutableListOf<Usuario>()

    /** Roles que pueden marcar una aeronave como inactiva. */
    private val rolesConPermiso = listOf("administrador", "focal_point_fod")

    private var aeronaveId: Int = -1
    private var aeronaveActiva: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_aeronave)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle Aeronave"

        // Vinculación de vistas del layout
        tvModelo = findViewById(R.id.tvDetalleAeronaveModelo)
        tvNumeroSerie = findViewById(R.id.tvDetalleAeronaveNumeroSerie)
        tvUbicacion = findViewById(R.id.tvDetalleAeronaveUbicacion)
        tvEstado = findViewById(R.id.tvDetalleAeronaveEstado)
        btnDesactivar = findViewById(R.id.btnDesactivarAeronave)
        recyclerView = findViewById(R.id.recyclerUsuariosAeronave)

        // Recupera los datos de la aeronave enviados desde AeronaveFragment por Intent
        aeronaveId = intent.getIntExtra("aeronave_id", -1)
        val modelo = intent.getStringExtra("aeronave_modelo") ?: ""
        val numeroSerie = intent.getStringExtra("aeronave_numero_serie") ?: ""
        val ubicacion = intent.getStringExtra("aeronave_ubicacion") ?: "Sin ubicación"
        aeronaveActiva = intent.getBooleanExtra("aeronave_activa", true)

        tvModelo.text = modelo
        tvNumeroSerie.text = "S/N: $numeroSerie"
        tvUbicacion.text = " $ubicacion"
        tvEstado.text = if (aeronaveActiva) "🟢 Activa" else "🔴 Inactiva"

        // El adapter de usuarios necesita un mapa ID→nombre para mostrar
        // a qué aeronave está adscrito cada usuario en su tarjeta
        val aeronaveNombre = "$modelo - $numeroSerie"
        val aeronavesMap = mapOf(aeronaveId to aeronaveNombre)
        adapter = UsuarioAdapter(usuarios, aeronavesMap) { }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Intercepta el gesto de "atrás" del sistema para devolver RESULT_OK,
        // forzando al fragment anterior a recargar la lista de aeronaves
        onBackPressedDispatcher.addCallback(this) {
            setResult(RESULT_OK)
            finish()
        }

        cargarUsuarios(aeronaveId)
        verificarRol()

        btnDesactivar.setOnClickListener { desactivarAeronave() }
    }

    /**
     * Consulta el rol del usuario logueado y muestra el botón de desactivar
     * solo si tiene permiso (rolesConPermiso) y la aeronave sigue activa.
     * Si la consulta falla, el botón permanece oculto por seguridad.
     */
    private fun verificarRol() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email
                val usuario = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<UsuarioRol>()

                if (usuario.rol in rolesConPermiso && aeronaveActiva) {
                    btnDesactivar.isVisible = true
                }
            } catch (e: Exception) {
                // Botón permanece oculto: ante cualquier error, la acción queda bloqueada
            }
        }
    }

    /**
     * Carga desde Supabase los usuarios adscritos a esta aeronave,
     * filtrando por aeronaveId en la tabla `usuarios`.
     */
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

    /**
     * Desactiva la aeronave y desasigna a todos sus usuarios en una secuencia de
     * dos operaciones sobre Supabase:
     * 1. Pone `activa = false` en la tabla `aeronaves`.
     * 2. Pone `aeronave_id = null` a todos los usuarios adscritos.
     *
     * La operación es irreversible por diseño: si la aeronave vuelve al hangar
     * se registra como una entrada nueva para no mezclar datos históricos.
     * Tras completarse, la UI se actualiza y se notifica al fragment anterior.
     */
    private fun desactivarAeronave() {
        lifecycleScope.launch {
            try {
                supabase.postgrest["aeronaves"]
                    .update(ActualizarAeronave(activa = false)) {
                        filter { eq("id", aeronaveId) }
                    }

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
                    setResult(RESULT_OK)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DetalleAeronaveActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Gestiona el botón de atrás.
     * Devuelve RESULT_OK para que AeronaveFragment recargue la lista.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_OK)
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}