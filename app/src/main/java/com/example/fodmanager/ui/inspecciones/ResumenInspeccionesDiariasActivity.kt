package com.example.fodmanager.ui.inspecciones

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Resumen diario agrupado por aeronave y día.
 *
 * Cada instancia representa la actividad de inspección de una aeronave
 * concreta durante un día concreto, consolidando todas las inspecciones
 * individuales de ese período en un único objeto de presentación.
 *
 * @property fechaDia             Fecha en formato "YYYY-MM-DD".
 * @property aeronaveId           ID interno de la aeronave (usado como clave de filtro).
 * @property aeronaveTexto        Nombre legible "Modelo - NumSerie".
 * @property zonasInspeccionadas  Zonas obligatorias que sí se inspeccionaron ese día.
 * @property zonasPendientes      Zonas obligatorias que no se inspeccionaron ese día.
 * @property zonasConFod          Zonas en las que se detectó FOD.
 * @property completa             true si todas las zonas obligatorias fueron inspeccionadas.
 * @property hayFod               true si al menos una zona registró FOD.
 */
data class ResumenInspeccionDiaria(
    val fechaDia: String,
    val aeronaveId: Int,
    val aeronaveTexto: String,
    val zonasInspeccionadas: List<String>,
    val zonasPendientes: List<String>,
    val zonasConFod: List<String>,
    val completa: Boolean,
    val hayFod: Boolean
)

/**
 * Opción del Spinner de filtro por aeronave.
 *
 * id es null para la opción "Todas las aeronaves".
 * toString devuelve texto para que el Spinner muestre el nombre directamente.
 */
data class OpcionFiltroAeronave(
    val id: Int?,
    val texto: String
) {
    override fun toString(): String = texto
}

/**
 * Activity que muestra el resumen diario de inspecciones agrupado por aeronave y día.
 *
 * Accesible desde el botón "Ver resumen diario" del HomeFragment.
 *
 * Funcionalidad:
 * - Lista de tarjetas (una por aeronave/día) con zonas inspeccionadas, pendientes y con FOD.
 * - Filtro por aeronave (Spinner) y por fecha (DatePickerDialog).
 * - Botón "Limpiar filtros" que restaura la lista completa.
 * - Ordenación: más reciente primero; dentro del mismo día, orden alfabético por aeronave.
 *
 * Visibilidad por rol:
 * - rolesGenerales  todas las aeronaves activas y todas las inspecciones.
 * - Resto  solo la aeronave asignada al usuario y sus inspecciones.
 */
class ResumenInspeccionesDiariasActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ResumenInspeccionesDiariasAdapter

    private lateinit var spinnerFiltroAeronave: Spinner
    private lateinit var btnSeleccionarFecha: Button
    private lateinit var btnAplicarFiltros: Button
    private lateinit var btnLimpiarFiltros: Button

    /** Lista actualmente visible en el RecyclerView (puede estar filtrada). */
    private val resumenes = mutableListOf<ResumenInspeccionDiaria>()

    /** Copia completa sin filtrar para restaurar al limpiar filtros. */
    private val resumenesOriginales = mutableListOf<ResumenInspeccionDiaria>()

    private val opcionesAeronave = mutableListOf<OpcionFiltroAeronave>()
    private lateinit var spinnerAdapter: ArrayAdapter<OpcionFiltroAeronave>

    /** Fecha seleccionada en el DatePickerDialog en formato "YYYY-MM-DD". Null = sin filtro. */
    private var fechaSeleccionada: String? = null

    /** Roles con visión global de todas las aeronaves y todas las inspecciones. */
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    /** Lista de zonas cuya inspección es obligatoria cada jornada. */
    private val zonasObligatorias = listOf(
        "COCKPIT + DRAWBRIDGE", "LMWS + ESCALERAS", "AVIONIC BAY",
        "CARGO HOLD FWD", "CARGO HOLD AFT", "CONE",
        "ENG#1", "ENG#2", "ENG#3", "ENG#4",
        "NLG", "MLG", "TOP FUSELAGE", "ZONA EXTERIOR"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resumen_inspecciones_diarias)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Inspecciones por día"

        recyclerView = findViewById(R.id.recyclerResumenInspeccionesDiarias)
        spinnerFiltroAeronave = findViewById(R.id.spinnerFiltroAeronave)
        btnSeleccionarFecha = findViewById(R.id.btnSeleccionarFecha)
        btnAplicarFiltros = findViewById(R.id.btnAplicarFiltros)
        btnLimpiarFiltros = findViewById(R.id.btnLimpiarFiltros)

        adapter = ResumenInspeccionesDiariasAdapter(resumenes)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opcionesAeronave)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFiltroAeronave.adapter = spinnerAdapter

        actualizarTextoBotonFecha()

        btnSeleccionarFecha.setOnClickListener { mostrarSelectorFecha() }
        btnAplicarFiltros.setOnClickListener { aplicarFiltros() }
        btnLimpiarFiltros.setOnClickListener { limpiarFiltros() }

        cargarResumenes()
    }

    /**
     * Carga desde Supabase todas las inspecciones visibles para el usuario,
     * las agrupa por aeronave y día, y construye la lista de ResumenInspeccionDiaria.
     *
     * Proceso:
     * 1. Usuario logueado  scope de datos (global o por aeronave).
     * 2. Aeronaves visibles construye el mapa ID→nombre y las opciones del Spinner.
     * 3. Inspecciones visibles  filtra las que tienen fecha y aeronave_id.
     * 4. Agrupación por clave "aeronave_id|YYYY-MM-DD".
     * 5. Por cada grupo se calculan zonas inspeccionadas, pendientes y con FOD.
     * 6. Ordenación: más reciente primero, alfabético por aeronave en empate de fecha.
     */
    private fun cargarResumenes() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                val usuarioActual = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email) } }
                    .decodeSingle<Usuario>()

                val esGeneral = usuarioActual.rol in rolesGenerales

                val aeronaves = if (esGeneral) {
                    supabase.postgrest["aeronaves"]
                        .select { filter { eq("activa", true) } }
                        .decodeList<Aeronave>()
                } else {
                    supabase.postgrest["aeronaves"]
                        .select { filter { eq("id", usuarioActual.aeronaveId ?: -1) } }
                        .decodeList<Aeronave>()
                }

                val aeronavesMap = aeronaves.associate { it.id to "${it.modelo} - ${it.numeroSerie}" }

                cargarOpcionesSpinner(aeronaves)

                val inspecciones = if (esGeneral) {
                    supabase.postgrest["inspecciones"].select().decodeList<Inspeccion>()
                } else {
                    supabase.postgrest["inspecciones"]
                        .select { filter { eq("aeronave_id", usuarioActual.aeronaveId ?: -1) } }
                        .decodeList<Inspeccion>()
                }

                // Solo se procesan inspecciones que tengan fecha y aeronave asociada
                val inspeccionesConFecha = inspecciones.filter {
                    !it.fecha.isNullOrBlank() && it.aeronaveId != null
                }

                // Agrupación por aeronave + día
                val grupos = inspeccionesConFecha.groupBy { inspeccion ->
                    "${inspeccion.aeronaveId!!}|${inspeccion.fecha!!.substring(0, 10)}"
                }

                val resultado = grupos.map { (clave, listaGrupo) ->
                    val (aeronaveId, fechaDia) = clave.split("|").let { it[0].toInt() to it[1] }
                    val zonasInspeccionadasSet = listaGrupo.map { it.zona }.toSet()

                    ResumenInspeccionDiaria(
                        fechaDia = fechaDia,
                        aeronaveId = aeronaveId,
                        aeronaveTexto = aeronavesMap[aeronaveId] ?: "Sin aeronave",
                        zonasInspeccionadas = zonasObligatorias.filter { it in zonasInspeccionadasSet },
                        zonasPendientes = zonasObligatorias.filter { it !in zonasInspeccionadasSet },
                        zonasConFod = listaGrupo.filter { it.conFod }.map { it.zona }.distinct(),
                        completa = zonasObligatorias.all { it in zonasInspeccionadasSet },
                        hayFod = listaGrupo.any { it.conFod }
                    )
                }.sortedWith(
                    compareByDescending<ResumenInspeccionDiaria> { it.fechaDia }
                        .thenBy { it.aeronaveTexto }
                )

                resumenesOriginales.clear()
                resumenesOriginales.addAll(resultado)
                resumenes.clear()
                resumenes.addAll(resultado)
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Toast.makeText(this@ResumenInspeccionesDiariasActivity, "Error cargando resumen diario: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Popula el Spinner con "Todas las aeronaves" como primera opción,
     * seguida de las aeronaves ordenadas alfabéticamente por nombre.
     */
    private fun cargarOpcionesSpinner(aeronaves: List<Aeronave>) {
        opcionesAeronave.clear()
        opcionesAeronave.add(OpcionFiltroAeronave(null, "Todas las aeronaves"))
        aeronaves.sortedBy { "${it.modelo} - ${it.numeroSerie}" }.forEach { aeronave ->
            opcionesAeronave.add(OpcionFiltroAeronave(aeronave.id, "${aeronave.modelo} - ${aeronave.numeroSerie}"))
        }
        spinnerAdapter.notifyDataSetChanged()
        spinnerFiltroAeronave.setSelection(0)
    }

    /**
     * Abre un DatePickerDialog con la fecha actual preseleccionada.
     * Al confirmar, guarda la fecha en[fechaSeleccionada y actualiza el texto del botón.
     */
    private fun mostrarSelectorFecha() {
        val calendario = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val mes = (month + 1).toString().padStart(2, '0')
                val dia = dayOfMonth.toString().padStart(2, '0')
                fechaSeleccionada = "$year-$mes-$dia"
                actualizarTextoBotonFecha()
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Actualiza el texto del botón de fecha:
     * - Sin filtro → "Todas las fechas".
     * - Con filtro → fecha en formato "dd/MM/yyyy".
     */
    private fun actualizarTextoBotonFecha() {
        btnSeleccionarFecha.text = if (fechaSeleccionada == null) {
            "Todas las fechas"
        } else {
            val partes = fechaSeleccionada!!.split("-")
            "${partes[2]}/${partes[1]}/${partes[0]}"
        }
    }

    /**
     * Filtra resumenesOriginales según la aeronave y la fecha seleccionadas
     * y actualiza el RecyclerView. Muestra un aviso si no hay resultados.
     *
     * Un filtro con valor null (aeronave = "Todas" o fecha = "Todas") no restringe
     * ese campo.
     */
    private fun aplicarFiltros() {
        val aeronaveIdSeleccionada = (spinnerFiltroAeronave.selectedItem as? OpcionFiltroAeronave)?.id

        val filtrados = resumenesOriginales.filter { resumen ->
            val coincideAeronave = aeronaveIdSeleccionada == null || resumen.aeronaveId == aeronaveIdSeleccionada
            val coincideFecha = fechaSeleccionada == null || resumen.fechaDia == fechaSeleccionada
            coincideAeronave && coincideFecha
        }

        resumenes.clear()
        resumenes.addAll(filtrados)
        adapter.notifyDataSetChanged()

        if (filtrados.isEmpty()) {
            Toast.makeText(this, "No hay resultados con esos filtros", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Restablece ambos filtros (aeronave y fecha) a sus valores por defecto
     * y restaura la lista completa desde resumenesOriginales.
     */
    private fun limpiarFiltros() {
        spinnerFiltroAeronave.setSelection(0)
        fechaSeleccionada = null
        actualizarTextoBotonFecha()

        resumenes.clear()
        resumenes.addAll(resumenesOriginales)
        adapter.notifyDataSetChanged()
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