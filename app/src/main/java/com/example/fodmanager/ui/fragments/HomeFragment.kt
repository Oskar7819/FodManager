package com.example.fodmanager.ui.fragments

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.models.IncidenciaFod
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import com.example.fodmanager.data.repository.UsuarioRepository
import com.example.fodmanager.ui.inspecciones.ResumenInspeccionesDiariasActivity
import com.google.android.material.card.MaterialCardView
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


// Clase auxiliar que resume el estado global de una inspección por día y aeronave
data class InspeccionGlobalResumen(
    // Fecha del día de la inspección
    val fechaDia: String,

    // Turno operativo de la inspección: manana, tarde, noche o cuarto_turno
    val turnoInspeccion: String,

    // Texto descriptivo de la aeronave
    val aeronaveTexto: String,

    // Lista de zonas que sí fueron inspeccionadas
    val zonasInspeccionadas: List<String>,

    // Lista de zonas obligatorias que faltan por inspeccionar
    val zonasFaltantes: List<String>,

    // Lista de zonas donde se detectó FOD
    val zonasConFod: List<String>,

    // Indica si en esa inspección hubo FOD
    val hayFod: Boolean
)

// Fragment principal del dashboard o panel de inicio
class HomeFragment : Fragment() {

    // Texto de bienvenida al usuario
    private lateinit var tvBienvenida: TextView

    // Texto con el resumen según el rol del usuario
    private lateinit var tvResumenRol: TextView

    // Botón para abrir el resumen diario de inspecciones
    private lateinit var btnVerResumenDiario: Button

    // KPI de inspecciones realizadas hoy
    private lateinit var tvKpiInspeccionesHoy: TextView

    // KPI de incidencias abiertas
    private lateinit var tvKpiAbiertas: TextView

    // KPI de incidencias en proceso
    private lateinit var tvKpiEnProceso: TextView

    // KPI de aeronaves activas visibles
    private lateinit var tvKpiAeronavesActivas: TextView

    // Textos de la tarjeta "Inspecciones por turno de hoy"
    private lateinit var tvDashboardTurnoManana: TextView
    private lateinit var tvDashboardTurnoTarde: TextView
    private lateinit var tvDashboardTurnoNoche: TextView
    private lateinit var tvDashboardTurnoCuarto: TextView
    private lateinit var tvDashboardTurnoTotal: TextView

    // Contenedor para mostrar el estado de las aeronaves
    private lateinit var llEstadoAeronaves: LinearLayout

    // Contenedor para mostrar las últimas incidencias
    private lateinit var llUltimasIncidencias: LinearLayout

    // Contenedor para mostrar las últimas inspecciones globales
    private lateinit var llUltimasInspeccionesGlobales: LinearLayout

    // Roles que tienen acceso a una vista global
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    // Lista de zonas obligatorias que forman parte de una inspección global completa
    private val zonasObligatorias = listOf(
        "COCKPIT + DRAWBRIDGE", "LMWS + ESCALERAS", "AVIONIC BAY",
        "CARGO HOLD FWD", "CARGO HOLD AFT", "CONE",
        "ENG#1", "ENG#2", "ENG#3", "ENG#4",
        "NLG", "MLG", "TOP FUSELAGE", "ZONA EXTERIOR"
    )

    // Colores usados para resaltar distintos estados del dashboard
    private val colorNaranjaPendiente = Color.parseColor("#F57C00")
    private val colorFondoVerdeSuave = Color.parseColor("#E8F5E9")
    private val colorFondoNaranjaSuave = Color.parseColor("#FFF3E0")
    private val colorFondoRojoSuave = Color.parseColor("#FFEBEE")

    // Colores de fondo para tarjetas de estado de aeronaves
    private val colorFondoEstadoVerde = Color.parseColor("#E8F5E9")
    private val colorFondoEstadoAmarillo = Color.parseColor("#FFF8E1")
    private val colorFondoEstadoRojo = Color.parseColor("#FFEBEE")
    private val colorFondoEstadoNeutro = Color.parseColor("#F5F5F5")

    // Crea e inicializa la vista del fragment
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Infla el layout principal del fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Vinculación de vistas del layout
        tvBienvenida = view.findViewById(R.id.tvBienvenida)
        tvResumenRol = view.findViewById(R.id.tvResumenRol)
        btnVerResumenDiario = view.findViewById(R.id.btnVerResumenDiario)
        tvKpiInspeccionesHoy = view.findViewById(R.id.tvKpiInspeccionesHoy)
        tvKpiAbiertas = view.findViewById(R.id.tvKpiAbiertas)
        tvKpiEnProceso = view.findViewById(R.id.tvKpiEnProceso)
        tvKpiAeronavesActivas = view.findViewById(R.id.tvKpiAeronavesActivas)

        // Vinculación de la tarjeta de inspecciones por turno
        tvDashboardTurnoManana = view.findViewById(R.id.tvDashboardTurnoManana)
        tvDashboardTurnoTarde = view.findViewById(R.id.tvDashboardTurnoTarde)
        tvDashboardTurnoNoche = view.findViewById(R.id.tvDashboardTurnoNoche)
        tvDashboardTurnoCuarto = view.findViewById(R.id.tvDashboardTurnoCuarto)
        tvDashboardTurnoTotal = view.findViewById(R.id.tvDashboardTurnoTotal)
        llEstadoAeronaves = view.findViewById(R.id.llEstadoAeronaves)
        llUltimasIncidencias = view.findViewById(R.id.llUltimasIncidencias)
        llUltimasInspeccionesGlobales = view.findViewById(R.id.llUltimasInspeccionesGlobales)

        // Acción del botón para abrir la actividad de resumen diario
        btnVerResumenDiario.setOnClickListener {
            startActivity(Intent(requireContext(), ResumenInspeccionesDiariasActivity::class.java))
        }

        // Carga el contenido del dashboard
        cargarDashboard()
        return view
    }

    // Función principal que obtiene y pinta todos los datos del dashboard
    private fun cargarDashboard() {
        lifecycleScope.launch {
            try {
                // Obtiene el usuario autenticado actual
                val usuarioActual = UsuarioRepository.getUsuarioActual()

                // Muestra el saludo personalizado
                tvBienvenida.text = "Hola, ${usuarioActual.nombre} 👋"

                // Comprueba si el usuario tiene acceso global
                val esGeneral = usuarioActual.rol in rolesGenerales

                // Obtiene las aeronaves activas
                val aeronavesActivas = supabase.postgrest["aeronaves"]
                    .select {
                        filter {
                            eq("activa", true)
                        }
                    }
                    .decodeList<Aeronave>()

                // Mapa de aeronaveId a texto descriptivo de aeronave
                val aeronavesMap = aeronavesActivas.associate {
                    it.id to "${it.modelo} - ${it.numeroSerie}"
                }

                // Obtiene todos los usuarios
                val usuarios = supabase.postgrest["usuarios"]
                    .select()
                    .decodeList<Usuario>()

                // Mapa de usuarioId a objeto Usuario
                val usuariosMap = usuarios.associateBy { it.id }

                // Obtiene inspecciones según el rol del usuario
                val inspecciones = if (esGeneral) {
                    supabase.postgrest["inspecciones"]
                        .select()
                        .decodeList<Inspeccion>()
                } else {
                    supabase.postgrest["inspecciones"]
                        .select {
                            filter {
                                eq("aeronave_id", usuarioActual.aeronaveId ?: -1)
                            }
                        }
                        .decodeList<Inspeccion>()
                }

                // Obtiene incidencias según el rol del usuario
                val incidencias = if (esGeneral) {
                    supabase.postgrest["incidencias_fod"]
                        .select()
                        .decodeList<IncidenciaFod>()
                } else {
                    supabase.postgrest["incidencias_fod"]
                        .select {
                            filter {
                                eq("aeronave_id", usuarioActual.aeronaveId ?: -1)
                            }
                        }
                        .decodeList<IncidenciaFod>()
                }

                // Determina qué aeronaves puede ver el usuario
                val aeronavesVisibles = if (esGeneral) {
                    aeronavesActivas
                } else {
                    aeronavesActivas.filter { it.id == usuarioActual.aeronaveId }
                }

                // Pinta cada sección del dashboard
                pintarResumenRol(usuarioActual, aeronavesVisibles.size, incidencias)
                pintarKpis(inspecciones, incidencias, aeronavesVisibles.size)

                // Nueva tarjeta: resumen de inspecciones por turno de hoy.
                // Usa las inspecciones visibles según el rol del usuario.
                pintarInspeccionesPorTurno(inspecciones)
                pintarEstadoAeronaves(aeronavesVisibles, incidencias, inspecciones)
                pintarUltimasIncidencias(incidencias, aeronavesMap, usuariosMap)
                pintarUltimasInspeccionesGlobales(inspecciones, aeronavesMap)

            } catch (e: Exception) {
                // Muestra un mensaje si ocurre un error al cargar el panel
                Toast.makeText(
                    requireContext(),
                    "Error cargando dashboard: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Muestra un resumen textual adaptado al rol del usuario
    private fun pintarResumenRol(
        usuario: Usuario,
        aeronavesVisibles: Int,
        incidencias: List<IncidenciaFod>
    ) {
        // Cuenta incidencias abiertas
        val abiertas = incidencias.count { it.estado == "abierta" }

        // Cuenta incidencias en proceso
        val enProceso = incidencias.count { it.estado == "en_proceso" }

        // Construye el mensaje según el rol
        tvResumenRol.text = when (usuario.rol) {
            "administrador" ->
                "Vista global de administración. Estás viendo $aeronavesVisibles aeronaves activas, con $abiertas incidencias abiertas y $enProceso en proceso."
            "head_plant" ->
                "Vista global de planta. Este panel resume la situación operativa completa del hangar."
            "focal_point_fod" ->
                "Vista global FOD. Este panel te ayuda a seguir incidencias activas, aeronaves afectadas y estado de inspecciones."
            "quality" ->
                "Vista de calidad. Estás viendo el estado operativo de tu aeronave asignada, con foco en incidencias activas e inspecciones completas."
            "mando_gp4" ->
                "Vista de mando. Estás viendo el seguimiento operativo de tu aeronave asignada."
            else ->
                "Vista operativa. Este panel resume el estado actual de tu aeronave y sus movimientos recientes."
        }

        // Aplica un color gris oscuro al texto resumen
        tvResumenRol.setTextColor(
            ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        )
    }

    // Pinta los indicadores KPI del panel
    private fun pintarKpis(
        inspecciones: List<Inspeccion>,
        incidencias: List<IncidenciaFod>,
        aeronavesVisibles: Int
    ) {
        // Obtiene la fecha actual en formato texto
        val hoy = java.time.LocalDate.now().toString()

        // Muestra el número de inspecciones hechas hoy
        tvKpiInspeccionesHoy.text = inspecciones.count { it.fecha?.startsWith(hoy) == true }.toString()

        // Muestra el número de incidencias abiertas
        tvKpiAbiertas.text = incidencias.count { it.estado == "abierta" }.toString()

        // Muestra el número de incidencias en proceso
        tvKpiEnProceso.text = incidencias.count { it.estado == "en_proceso" }.toString()

        // Muestra el número de aeronaves activas visibles
        tvKpiAeronavesActivas.text = aeronavesVisibles.toString()
    }


    /**
     * Pinta en el dashboard cuántas inspecciones se han registrado hoy
     * por cada turno operativo.
     *
     * El turno no se calcula aquí.
     * El turno ya viene calculado desde Supabase en inspeccion.turnoInspeccion.
     *
     * Reglas:
     * - manana:        día ordinario de 07:00 a 14:59
     * - tarde:         día ordinario de 15:00 a 22:59
     * - noche:         día ordinario de 23:00 a 06:59
     * - cuarto_turno:  fechas cargadas en calendario_cuarto_turno
     */
    /**
     * Pinta en el dashboard cuántas inspecciones se han registrado hoy
     * por cada turno operativo.
     *
     * El turno y la fecha local de inspección los calcula Supabase.
     */
    private fun pintarInspeccionesPorTurno(inspecciones: List<Inspeccion>) {
        val hoy = java.time.LocalDate.now().toString()

        /*
         * Usamos fechaInspeccionDia porque es la fecha local calculada por Supabase.
         * Si alguna inspección antigua no tiene ese campo, usamos fecha como respaldo.
         */
        val inspeccionesHoy = inspecciones.filter { inspeccion ->
            inspeccion.fechaInspeccionDia == hoy ||
                    (inspeccion.fechaInspeccionDia == null && inspeccion.fecha?.startsWith(hoy) == true)
        }

        val manana = inspeccionesHoy.count { it.turnoInspeccion == "manana" }
        val tarde = inspeccionesHoy.count { it.turnoInspeccion == "tarde" }
        val noche = inspeccionesHoy.count { it.turnoInspeccion == "noche" }
        val cuartoTurno = inspeccionesHoy.count { it.turnoInspeccion == "cuarto_turno" }

        val total = inspeccionesHoy.size

        tvDashboardTurnoManana.text = "Mañana: $manana"
        tvDashboardTurnoTarde.text = "Tarde: $tarde"
        tvDashboardTurnoNoche.text = "Noche: $noche"
        tvDashboardTurnoCuarto.text = "Cuarto turno: $cuartoTurno"
        tvDashboardTurnoTotal.text = "Total hoy: $total"
    }


    // Pinta el estado actual de cada aeronave visible
    private fun pintarEstadoAeronaves(
        aeronavesVisibles: List<Aeronave>,
        incidencias: List<IncidenciaFod>,
        inspecciones: List<Inspeccion>
    ) {
        // Limpia el contenedor antes de volver a rellenarlo
        llEstadoAeronaves.removeAllViews()

        // Si no hay aeronaves visibles, muestra mensaje informativo
        if (aeronavesVisibles.isEmpty()) {
            llEstadoAeronaves.addView(crearLineaDashboard("Sin aeronaves visibles para este usuario."))
            return
        }

        // Fecha de hoy
        val hoy = java.time.LocalDate.now().toString()

        // Recorre cada aeronave para pintar su resumen
        aeronavesVisibles.forEach { aeronave ->
            // Filtra incidencias de la aeronave
            val incidenciasAeronave = incidencias.filter { it.aeronaveId == aeronave.id }

            // Cuenta incidencias abiertas
            val abiertas = incidenciasAeronave.count { it.estado == "abierta" }

            // Cuenta incidencias en proceso
            val enProceso = incidenciasAeronave.count { it.estado == "en_proceso" }

            // Filtra inspecciones de hoy para esa aeronave
            val inspeccionesHoyAeronave = inspecciones.filter {
                it.aeronaveId == aeronave.id && it.fecha?.startsWith(hoy) == true
            }

            // Número de inspecciones de hoy
            val inspeccionesHoy = inspeccionesHoyAeronave.size

            // Número de inspecciones de hoy con FOD
            val inspeccionesHoyConFod = inspeccionesHoyAeronave.count { it.conFod }

            // Texto descriptivo del estado de incidencias
            val estadoDescriptivo = when {
                abiertas > 0 -> if (abiertas == 1) {
                    "🔴 Estado actual: tiene 1 incidencia FOD abierta"
                } else {
                    "🔴 Estado actual: tiene $abiertas incidencias FOD abiertas"
                }

                enProceso > 0 -> if (enProceso == 1) {
                    "🟡 Estado actual: tiene 1 incidencia FOD en proceso"
                } else {
                    "🟡 Estado actual: tiene $enProceso incidencias FOD en proceso"
                }

                else -> "🟢 Estado actual: sin incidencias activas"
            }

            // Texto con la actividad de inspecciones de hoy
            val actividadHoy = when (inspeccionesHoy) {
                0 -> "📋 Inspecciones realizadas hoy: ninguna"
                1 -> "📋 Inspecciones realizadas hoy: 1"
                else -> "📋 Inspecciones realizadas hoy: $inspeccionesHoy"
            }

            // Texto con el resultado FOD de hoy
            val resultadoHoy = when {
                inspeccionesHoy == 0 -> "ℹ️ Hoy todavía no hay inspecciones registradas"
                inspeccionesHoyConFod == 0 -> "✅ Todas las inspecciones de hoy están sin FOD"
                inspeccionesHoyConFod == 1 -> "🚨 Hoy se ha detectado FOD en 1 inspección"
                else -> "🚨 Hoy se ha detectado FOD en $inspeccionesHoyConFod inspecciones"
            }

            // Infla la tarjeta visual de estado de aeronave
            val tarjeta = layoutInflater.inflate(
                R.layout.item_estado_aeronave_dashboard,
                llEstadoAeronaves,
                false
            )

            // Obtiene la card principal de la tarjeta
            val cardEstadoAeronave =
                tarjeta.findViewById<MaterialCardView>(R.id.cardEstadoAeronave)

            // Rellena los textos de la tarjeta
            tarjeta.findViewById<TextView>(R.id.tvAeronaveEstado).text =
                "${aeronave.modelo} - ${aeronave.numeroSerie}"
            tarjeta.findViewById<TextView>(R.id.tvEstadoActualAeronave).text = estadoDescriptivo
            tarjeta.findViewById<TextView>(R.id.tvActividadHoyAeronave).text = actividadHoy
            tarjeta.findViewById<TextView>(R.id.tvResultadoHoyAeronave).text = resultadoHoy

            // Cambia el color de fondo según la situación de la aeronave
            cardEstadoAeronave.setCardBackgroundColor(
                when {
                    abiertas > 0 || inspeccionesHoyConFod > 0 -> colorFondoEstadoRojo
                    enProceso > 0 -> colorFondoEstadoAmarillo
                    inspeccionesHoy == 0 -> colorFondoEstadoNeutro
                    else -> colorFondoEstadoVerde
                }
            )

            // Añade la tarjeta al contenedor
            llEstadoAeronaves.addView(tarjeta)
        }
    }

    // Pinta las últimas incidencias registradas
    private fun pintarUltimasIncidencias(
        incidencias: List<IncidenciaFod>,
        aeronavesMap: Map<Int, String>,
        usuariosMap: Map<Int, Usuario>
    ) {
        // Limpia el contenedor
        llUltimasIncidencias.removeAllViews()

        // Ordena por fecha descendente y toma las 5 más recientes
        val ultimas = incidencias.sortedByDescending { it.createdAt ?: "" }.take(5)

        // Si no hay incidencias, muestra mensaje informativo
        if (ultimas.isEmpty()) {
            llUltimasIncidencias.addView(crearLineaDashboard("No hay incidencias recientes."))
            return
        }

        // Recorre cada incidencia para pintar las tarjetas
        ultimas.forEach { incidencia ->
            // Infla la tarjeta visual
            val tarjeta = layoutInflater.inflate(
                R.layout.item_incidencia_dashboard,
                llUltimasIncidencias,
                false
            )

            // Obtiene la card principal
            val cardIncidencia =
                tarjeta.findViewById<MaterialCardView>(R.id.cardIncidenciaDashboard)

            // Obtiene el texto descriptivo de la aeronave
            val aeronaveTexto = incidencia.aeronaveId?.let { aeronavesMap[it] } ?: "Sin aeronave"

            // Obtiene el usuario asociado a la incidencia
            val usuario = incidencia.usuarioId?.let { usuariosMap[it] }

            // Construye el texto del declarante
            val declarante = if (usuario != null) {
                "${usuario.nombre} ${usuario.apellidos} · ${incidencia.numeroEmpleado ?: usuario.numeroEmpleado ?: "Sin nº empleado"}"
            } else {
                "Usuario desconocido · ${incidencia.numeroEmpleado ?: "Sin nº empleado"}"
            }

            // Traduce el estado a un texto más visual
            val estado = when (incidencia.estado) {
                "abierta" -> "🔴 Abierta"
                "en_proceso" -> "🟡 En proceso"
                "cerrada" -> "🟢 Cerrada"
                else -> incidencia.estado ?: "Estado no disponible"
            }

            // Rellena los datos de la tarjeta
            tarjeta.findViewById<TextView>(R.id.tvAeronaveIncidencia).text = aeronaveTexto
            tarjeta.findViewById<TextView>(R.id.tvFechaIncidencia).text =
                "Detectada: ${formatearFechaHora(incidencia.createdAt)}"
            tarjeta.findViewById<TextView>(R.id.tvEstadoIncidencia).text = estado
            tarjeta.findViewById<TextView>(R.id.tvDuracionIncidencia).text =
                calcularDuracionDashboard(
                    incidencia.estado,
                    incidencia.createdAt,
                    incidencia.fechaCierre
                )
            tarjeta.findViewById<TextView>(R.id.tvDeclaranteIncidencia).text = declarante
            tarjeta.findViewById<TextView>(R.id.tvZonaIncidencia).text =
                "Zona: ${incidencia.zonaAvion ?: "No especificada"}"

            // Color de fondo según el estado de la incidencia
            cardIncidencia.setCardBackgroundColor(
                when (incidencia.estado) {
                    "abierta" -> colorFondoRojoSuave
                    "en_proceso" -> colorFondoNaranjaSuave
                    "cerrada" -> colorFondoVerdeSuave
                    else -> Color.WHITE
                }
            )

            // Añade la tarjeta al contenedor
            llUltimasIncidencias.addView(tarjeta)
        }
    }



    // Pinta los resúmenes globales de inspecciones recientes.
// Ahora una inspección global se agrupa por:
// - aeronave
// - día
// - turno
//
// Esto permite que una misma aeronave tenga una inspección global de mañana,
// otra de tarde, otra de noche y otra de cuarto turno.
    private fun pintarUltimasInspeccionesGlobales(
        inspecciones: List<Inspeccion>,
        aeronavesMap: Map<Int, String>
    ) {
        // Limpia el contenedor
        llUltimasInspeccionesGlobales.removeAllViews()

        // Filtra solo inspecciones que tienen fecha válida.
        // Usamos fechaInspeccionDia si existe, porque la calcula Supabase en horario local.
        // Si alguna inspección antigua no tiene ese campo, usamos fecha como respaldo.
        val inspeccionesConFecha = inspecciones.filter { inspeccion ->
            !inspeccion.fechaInspeccionDia.isNullOrBlank() || !inspeccion.fecha.isNullOrBlank()
        }

        // Agrupa por aeronave, día y turno.
        // Antes se agrupaba solo por aeronave y día.
        // Ahora añadimos turno_inspeccion para no mezclar mañana, tarde, noche y cuarto turno.
        val grupos = inspeccionesConFecha.groupBy { inspeccion ->
            val dia = inspeccion.fechaInspeccionDia
                ?: inspeccion.fecha!!.substring(0, 10)

            val aeronave = inspeccion.aeronaveId ?: -1

            val turno = inspeccion.turnoInspeccion ?: "sin_turno"

            "$aeronave|$dia|$turno"
        }

        // Genera un resumen por cada grupo
        val resumenes = grupos.map { (clave, listaGrupo) ->
            val partes = clave.split("|")

            val aeronaveId = partes[0].toInt()
            val fechaDia = partes[1]
            val turno = partes[2]

            val zonasInspeccionadasSet = listaGrupo.map { it.zona }.toSet()

            InspeccionGlobalResumen(
                fechaDia = fechaDia,
                turnoInspeccion = turno,
                aeronaveTexto = aeronavesMap[aeronaveId] ?: "Sin aeronave",
                zonasInspeccionadas = zonasObligatorias.filter { it in zonasInspeccionadasSet },
                zonasFaltantes = zonasObligatorias.filter { it !in zonasInspeccionadasSet },
                zonasConFod = listaGrupo.filter { it.conFod }.map { it.zona }.distinct(),
                hayFod = listaGrupo.any { it.conFod }
            )
        }.sortedWith(
            compareByDescending<InspeccionGlobalResumen> { it.fechaDia }
                .thenBy { ordenTurnoInspeccion(it.turnoInspeccion) }
        ).take(5)

        // Si no hay resúmenes, muestra mensaje informativo
        if (resumenes.isEmpty()) {
            llUltimasInspeccionesGlobales.addView(
                crearLineaDashboard("No hay inspecciones globales recientes.")
            )
            return
        }

        // Recorre cada resumen para mostrarlo en una tarjeta
        resumenes.forEach { resumen ->
            // Infla la tarjeta visual
            val tarjeta = layoutInflater.inflate(
                R.layout.item_inspeccion_global_dashboard,
                llUltimasInspeccionesGlobales,
                false
            )

            // Obtiene la card principal
            val cardInspeccionGlobal =
                tarjeta.findViewById<MaterialCardView>(R.id.cardInspeccionGlobal)

            // Rellena datos básicos de la tarjeta
            tarjeta.findViewById<TextView>(R.id.tvAeronaveGlobal).text = resumen.aeronaveTexto

            // Muestra la fecha con icono y texto claro para que destaque en la tarjeta.
            tarjeta.findViewById<TextView>(R.id.tvFechaGlobal).text =
                "📅 Fecha: ${formatearSoloFecha(resumen.fechaDia)}"

            // Nuevo campo: muestra el turno de esa inspección global
            // Muestra el turno de forma más visible.
            tarjeta.findViewById<TextView>(R.id.tvTurnoGlobal).text =
                "🕒 Turno: ${formatearTurnoInspeccion(resumen.turnoInspeccion)}"

            // Configura el texto de completitud de la inspección
            val tvCompletitud = tarjeta.findViewById<TextView>(R.id.tvEstadoCompletitudGlobal)

            if (resumen.zonasFaltantes.isEmpty()) {
                tvCompletitud.text =
                    "✅ Completa (${resumen.zonasInspeccionadas.size}/${zonasObligatorias.size} zonas)"
                tvCompletitud.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.black)
                )
            } else {
                tvCompletitud.text =
                    "⚠️ Incompleta (${resumen.zonasInspeccionadas.size}/${zonasObligatorias.size} zonas)"
                tvCompletitud.setTextColor(colorNaranjaPendiente)
            }

            // Configura el texto del estado FOD
            val tvFod = tarjeta.findViewById<TextView>(R.id.tvEstadoFodGlobal)

            if (resumen.hayFod) {
                tvFod.text = "🚨 Se detectó FOD en ${resumen.zonasConFod.size} zona(s)"
                tvFod.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                )
            } else {
                tvFod.text = "🟢 Sin FOD declarado en esta inspección global por turno"
                tvFod.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.black)
                )
            }

            // Cambia el color de fondo según el resultado
            cardInspeccionGlobal.setCardBackgroundColor(
                when {
                    resumen.hayFod -> colorFondoRojoSuave
                    resumen.zonasFaltantes.isNotEmpty() -> colorFondoNaranjaSuave
                    else -> colorFondoVerdeSuave
                }
            )

            // Muestra la sección de zonas inspeccionadas
            mostrarSeccionZonas(
                tarjeta,
                R.id.tvTituloZonasInspeccionadasGlobal,
                R.id.llZonasInspeccionadasGlobal,
                resumen.zonasInspeccionadas
            ) { crearLineaZonaInspeccionada(it) }

            // Muestra la sección de zonas con FOD
            mostrarSeccionZonas(
                tarjeta,
                R.id.tvTituloZonasFodGlobal,
                R.id.llZonasFodGlobal,
                resumen.zonasConFod,
                configTitulo = { tv ->
                    tv.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    )
                }
            ) { crearLineaZonaConFodRoja(it) }

            // Muestra la sección de zonas pendientes
            mostrarSeccionZonas(
                tarjeta,
                R.id.tvTituloPendientesGlobal,
                R.id.llPendientesGlobal,
                resumen.zonasFaltantes,
                configTitulo = { tv ->
                    tv.setTextColor(colorNaranjaPendiente)
                }
            ) { crearLineaChecklistPendienteNaranja(it) }

            // Añade la tarjeta al contenedor
            llUltimasInspeccionesGlobales.addView(tarjeta)
        }
    }

    /**
     * Convierte el valor técnico de Supabase en texto claro para el usuario.
     */
    private fun formatearTurnoInspeccion(turno: String?): String {
        return when (turno) {
            "manana" -> "Mañana"
            "tarde" -> "Tarde"
            "noche" -> "Noche"
            "cuarto_turno" -> "Cuarto turno"
            else -> "No registrado"
        }
    }

    /**
     * Da un orden lógico a los turnos para el dashboard.
     */
    private fun ordenTurnoInspeccion(turno: String?): Int {
        return when (turno) {
            "manana" -> 1
            "tarde" -> 2
            "noche" -> 3
            "cuarto_turno" -> 4
            else -> 99
        }
    }


    // Muestra u oculta una sección de zonas dentro de una tarjeta
    private fun mostrarSeccionZonas(
        tarjeta: View,
        idTitulo: Int,
        idContenedor: Int,
        zonas: List<String>,
        configTitulo: ((TextView) -> Unit)? = null,
        crearFila: (String) -> TextView
    ) {
        // Obtiene el título de la sección
        val tvTitulo = tarjeta.findViewById<TextView>(idTitulo)

        // Obtiene el contenedor de la sección
        val llContenedor = tarjeta.findViewById<LinearLayout>(idContenedor)

        // Si hay elementos, muestra la sección y la rellena
        if (zonas.isNotEmpty()) {
            tvTitulo.visibility = View.VISIBLE
            llContenedor.visibility = View.VISIBLE
            llContenedor.removeAllViews()
            configTitulo?.invoke(tvTitulo)
            zonas.forEach { llContenedor.addView(crearFila(it)) }
        } else {
            // Si no hay elementos, oculta la sección
            tvTitulo.visibility = View.GONE
            llContenedor.visibility = View.GONE
        }
    }

    // Crea una línea simple de texto para el dashboard
    private fun crearLineaDashboard(texto: String): TextView {
        return TextView(requireContext()).apply {
            text = texto
            textSize = 14f
            setLineSpacing(0f, 1.15f)
            setPadding(0, 8, 0, 12)
        }
    }

    // Crea una línea para una zona inspeccionada
    private fun crearLineaZonaInspeccionada(zona: String): TextView {
        return TextView(requireContext()).apply {
            text = "☑ $zona"
            textSize = 13f
            setPadding(12, 2, 0, 2)
            setLineSpacing(0f, 1.1f)
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        }
    }

    // Crea una línea para una zona pendiente en color naranja
    private fun crearLineaChecklistPendienteNaranja(zona: String): TextView {
        return TextView(requireContext()).apply {
            text = "☐ $zona"
            textSize = 13f
            setPadding(12, 2, 0, 2)
            setLineSpacing(0f, 1.1f)
            setTextColor(colorNaranjaPendiente)
        }
    }

    // Crea una línea para una zona con FOD en color rojo
    private fun crearLineaZonaConFodRoja(zona: String): TextView {
        return TextView(requireContext()).apply {
            text = "🚨 $zona"
            textSize = 13f
            setPadding(12, 2, 0, 2)
            setLineSpacing(0f, 1.1f)
            setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
            )
        }
    }

    // Intenta convertir una fecha en formato ISO a LocalDateTime
    private fun parseFecha(fechaIso: String?): LocalDateTime? {
        return try {
            if (fechaIso.isNullOrBlank()) return null
            OffsetDateTime.parse(fechaIso).toLocalDateTime()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(fechaIso)
            } catch (_: Exception) {
                null
            }
        }
    }

    // Calcula la duración de una incidencia para mostrarla en el dashboard
    private fun calcularDuracionDashboard(
        estado: String?,
        createdAt: String?,
        fechaCierre: String?
    ): String {
        // Obtiene la fecha de inicio
        val inicio = parseFecha(createdAt) ?: return "Duración: no disponible"

        // Obtiene la fecha final, usando la fecha de cierre o el momento actual
        val fin = if (estado == "cerrada" && !fechaCierre.isNullOrBlank()) {
            parseFecha(fechaCierre)
        } else {
            OffsetDateTime.now().toLocalDateTime()
        } ?: return "Duración: no disponible"

        // Calcula la duración en días
        val dias = Duration.between(inicio, fin).toDays().coerceAtLeast(0)

        // Devuelve un texto distinto según el estado
        return when (estado) {
            "cerrada" -> if (dias == 1L) {
                "✅ Estuvo abierta 1 día"
            } else {
                "✅ Estuvo abierta $dias días"
            }

            "abierta", "en_proceso" -> if (dias == 1L) {
                "⏳ Abierta desde hace 1 día"
            } else {
                "⏳ Abierta desde hace $dias días"
            }

            else -> if (dias == 1L) {
                "Duración: 1 día"
            } else {
                "Duración: $dias días"
            }
        }
    }

    // Formatea una fecha ISO a formato dd/MM/yyyy HH:mm
    private fun formatearFechaHora(fechaIso: String?): String {
        return try {
            parseFecha(fechaIso)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                ?: "No disponible"
        } catch (e: Exception) {
            fechaIso ?: "No disponible"
        }
    }

    // Formatea una fecha yyyy-MM-dd a dd/MM/yyyy
    private fun formatearSoloFecha(fechaIso: String?): String {
        return fechaIso?.let {
            try {
                val (anio, mes, dia) = it.split("-")
                "$dia/$mes/$anio"
            } catch (e: Exception) {
                it
            }
        } ?: "Sin fecha"
    }
}