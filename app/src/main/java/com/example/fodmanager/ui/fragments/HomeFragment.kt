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
import com.example.fodmanager.ui.inspecciones.ResumenInspeccionesDiariasActivity
import com.google.android.material.card.MaterialCardView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Modelo de datos para el resumen diario agrupado de inspecciones por aeronave y día.
 *
 * Se construye en HomeFragment.pintarUltimasInspeccionesGlobales agrupando todas
 * las inspecciones de una misma aeronave en el mismo día en un único objeto,
 * de forma que la tarjeta del dashboard muestre el estado global de la jornada.
 *
 * @property fechaDia             Fecha en formato "YYYY-MM-DD".
 * @property aeronaveTexto        Nombre legible de la aeronave ("Modelo - NumSerie").
 * @property zonasInspeccionadas  Zonas obligatorias que sí fueron inspeccionadas ese día.
 * @property zonasFaltantes       Zonas obligatorias que no se inspeccionaron ese día.
 * @property zonasConFod          Zonas en las que se detectó FOD ese día.
 * @property hayFod               true si al menos una zona registró FOD ese día.
 */
data class InspeccionGlobalResumen(
    val fechaDia: String,
    val aeronaveTexto: String,
    val zonasInspeccionadas: List<String>,
    val zonasFaltantes: List<String>,
    val zonasConFod: List<String>,
    val hayFod: Boolean
)

/**
 * Fragment de la pantalla principal ("Home") del Bottom Navigation.
 *
 * Actúa como dashboard operativo del hangar. Muestra:
 * - Saludo personalizado al usuario logueado.
 * - Texto de resumen adaptado al rol (pintarResumenRol).
 * - KPIs numéricos: inspecciones de hoy, incidencias abiertas/en proceso, aeronaves activas (pintarKpis).
 * - Estado por aeronave con código de color (pintarEstadoAeronaves).
 * - Últimas 5 incidencias FOD (pintarUltimasIncidencias).
 * - Últimas 5 inspecciones globales agrupadas por aeronave/día (pintarUltimasInspeccionesGlobales).
 * - Botón de acceso a ResumenInspeccionesDiariasActivity.
 *
 * La información visible se filtra por rol (misma lógica que el resto de fragments):
 * - rolesGenerales, datos de todo el hangar.
 * - Resto, datos de la aeronave asignada únicamente.
 */
class HomeFragment : Fragment() {

    private lateinit var tvBienvenida: TextView
    private lateinit var tvResumenRol: TextView
    private lateinit var btnVerResumenDiario: Button

    // KPIs numéricos del dashboard
    private lateinit var tvKpiInspeccionesHoy: TextView
    private lateinit var tvKpiAbiertas: TextView
    private lateinit var tvKpiEnProceso: TextView
    private lateinit var tvKpiAeronavesActivas: TextView

    // Contenedores de las secciones dinámicas del dashboard
    private lateinit var llEstadoAeronaves: LinearLayout
    private lateinit var llUltimasIncidencias: LinearLayout
    private lateinit var llUltimasInspeccionesGlobales: LinearLayout

    // Roles con visión global de todo el hangar.
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    /**
     * Lista de zonas que deben inspeccionarse obligatoriamente cada jornada.
     * Se compara con las zonas realmente inspeccionadas para calcular
     * completitud e identificar zonas pendientes.
     */
    private val zonasObligatorias = listOf(
        "COCKPIT + DRAWBRIDGE", "LMWS + ESCALERAS", "AVIONIC BAY",
        "CARGO HOLD FWD", "CARGO HOLD AFT", "CONE",
        "ENG#1", "ENG#2", "ENG#3", "ENG#4",
        "NLG", "MLG", "TOP FUSELAGE", "ZONA EXTERIOR"
    )

    // Colores reutilizados en varias secciones del dashboard
    private val colorNaranjaPendiente = Color.parseColor("#F57C00")
    private val colorFondoVerdeSuave = Color.parseColor("#E8F5E9")
    private val colorFondoNaranjaSuave = Color.parseColor("#FFF3E0")
    private val colorFondoRojoSuave = Color.parseColor("#FFEBEE")

    // Colores de fondo para las tarjetas de estado por aeronave
    private val colorFondoEstadoVerde = Color.parseColor("#E8F5E9")    // Sin incidencias activas
    private val colorFondoEstadoAmarillo = Color.parseColor("#FFF8E1") // Incidencias en proceso
    private val colorFondoEstadoRojo = Color.parseColor("#FFEBEE")     // Incidencias abiertas o FOD hoy
    private val colorFondoEstadoNeutro = Color.parseColor("#F5F5F5")   // Sin actividad hoy

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        tvBienvenida = view.findViewById(R.id.tvBienvenida)
        tvResumenRol = view.findViewById(R.id.tvResumenRol)
        btnVerResumenDiario = view.findViewById(R.id.btnVerResumenDiario)
        tvKpiInspeccionesHoy = view.findViewById(R.id.tvKpiInspeccionesHoy)
        tvKpiAbiertas = view.findViewById(R.id.tvKpiAbiertas)
        tvKpiEnProceso = view.findViewById(R.id.tvKpiEnProceso)
        tvKpiAeronavesActivas = view.findViewById(R.id.tvKpiAeronavesActivas)
        llEstadoAeronaves = view.findViewById(R.id.llEstadoAeronaves)
        llUltimasIncidencias = view.findViewById(R.id.llUltimasIncidencias)
        llUltimasInspeccionesGlobales = view.findViewById(R.id.llUltimasInspeccionesGlobales)

        btnVerResumenDiario.setOnClickListener {
            startActivity(Intent(requireContext(), ResumenInspeccionesDiariasActivity::class.java))
        }

        cargarDashboard()
        return view
    }

    /**
     * Punto de entrada principal del dashboard.
     * Realiza todas las consultas a Supabase y delega el pintado
     * a cada función especializada (`pintar*`).
     *
     * Orden de consultas:
     * 1. Usuario logueado → determina el scope de datos.
     * 2. Aeronaves activas → base para el estado por aeronave y los mapas.
     * 3. Todos los usuarios → mapa para mostrar nombres de declarantes.
     * 4. Inspecciones e incidencias (filtradas por rol).
     */
    private fun cargarDashboard() {
        lifecycleScope.launch {
            try {
                val email = supabase.auth.currentSessionOrNull()?.user?.email.orEmpty()

                val usuarioActual = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email) } }
                    .decodeSingle<Usuario>()

                tvBienvenida.text = "Hola, ${usuarioActual.nombre} 👋"

                val esGeneral = usuarioActual.rol in rolesGenerales

                val aeronavesActivas = supabase.postgrest["aeronaves"]
                    .select { filter { eq("activa", true) } }
                    .decodeList<Aeronave>()

                val aeronavesMap = aeronavesActivas.associate { it.id to "${it.modelo} - ${it.numeroSerie}" }

                val usuarios = supabase.postgrest["usuarios"].select().decodeList<Usuario>()
                val usuariosMap = usuarios.associateBy { it.id }

                // Carga inspecciones e incidencias según scope del rol
                val inspecciones = if (esGeneral) {
                    supabase.postgrest["inspecciones"].select().decodeList<Inspeccion>()
                } else {
                    supabase.postgrest["inspecciones"]
                        .select { filter { eq("aeronave_id", usuarioActual.aeronaveId ?: -1) } }
                        .decodeList<Inspeccion>()
                }

                val incidencias = if (esGeneral) {
                    supabase.postgrest["incidencias_fod"].select().decodeList<IncidenciaFod>()
                } else {
                    supabase.postgrest["incidencias_fod"]
                        .select { filter { eq("aeronave_id", usuarioActual.aeronaveId ?: -1) } }
                        .decodeList<IncidenciaFod>()
                }

                // Aeronaves visibles: todas (roles generales) o solo la asignada
                val aeronavesVisibles = if (esGeneral) aeronavesActivas
                else aeronavesActivas.filter { it.id == usuarioActual.aeronaveId }

                pintarResumenRol(usuarioActual, aeronavesVisibles.size, incidencias)
                pintarKpis(inspecciones, incidencias, aeronavesVisibles.size)
                pintarEstadoAeronaves(aeronavesVisibles, incidencias, inspecciones)
                pintarUltimasIncidencias(incidencias, aeronavesMap, usuariosMap)
                pintarUltimasInspeccionesGlobales(inspecciones, aeronavesMap)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error cargando dashboard: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Muestra un texto descriptivo del panel adaptado al rol del usuario.
     * Incluye métricas relevantes (aeronaves visibles, incidencias abiertas/en proceso)
     * donde aplica.
     */
    private fun pintarResumenRol(
        usuario: Usuario,
        aeronavesVisibles: Int,
        incidencias: List<IncidenciaFod>
    ) {
        val abiertas = incidencias.count { it.estado == "abierta" }
        val enProceso = incidencias.count { it.estado == "en_proceso" }

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

        tvResumenRol.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
    }

    /**
     * Actualiza los cuatro indicadores numéricos del dashboard:
     * - Inspecciones de hoy: inspecciones cuya fecha empieza por la fecha actual.
     * - Incidencias abiertas.
     * - Incidencias en proceso.
     * - Aeronaves visibles (activas dentro del scope del rol).
     */
    private fun pintarKpis(
        inspecciones: List<Inspeccion>,
        incidencias: List<IncidenciaFod>,
        aeronavesVisibles: Int
    ) {
        val hoy = java.time.LocalDate.now().toString()
        tvKpiInspeccionesHoy.text = inspecciones.count { it.fecha?.startsWith(hoy) == true }.toString()
        tvKpiAbiertas.text = incidencias.count { it.estado == "abierta" }.toString()
        tvKpiEnProceso.text = incidencias.count { it.estado == "en_proceso" }.toString()
        tvKpiAeronavesActivas.text = aeronavesVisibles.toString()
    }

    /**
     * Genera dinámicamente una tarjeta por cada aeronave visible mostrando:
     * - Nombre del modelo y número de serie.
     * - Estado actual basado en incidencias activas (🔴 abiertas / 🟡 en proceso / 🟢 OK).
     * - Actividad de inspecciones del día y si alguna registró FOD.
     *
     * El color de fondo de la tarjeta sigue la misma lógica semafórica:
     * rojo → incidencias abiertas o FOD hoy; amarillo → en proceso; verde → limpio;
     * neutro → sin actividad hoy.
     */
    private fun pintarEstadoAeronaves(
        aeronavesVisibles: List<Aeronave>,
        incidencias: List<IncidenciaFod>,
        inspecciones: List<Inspeccion>
    ) {
        llEstadoAeronaves.removeAllViews()

        if (aeronavesVisibles.isEmpty()) {
            llEstadoAeronaves.addView(crearLineaDashboard("Sin aeronaves visibles para este usuario."))
            return
        }

        val hoy = java.time.LocalDate.now().toString()

        aeronavesVisibles.forEach { aeronave ->
            val incidenciasAeronave = incidencias.filter { it.aeronaveId == aeronave.id }
            val abiertas = incidenciasAeronave.count { it.estado == "abierta" }
            val enProceso = incidenciasAeronave.count { it.estado == "en_proceso" }

            val inspeccionesHoyAeronave = inspecciones.filter {
                it.aeronaveId == aeronave.id && it.fecha?.startsWith(hoy) == true
            }
            val inspeccionesHoy = inspeccionesHoyAeronave.size
            val inspeccionesHoyConFod = inspeccionesHoyAeronave.count { it.conFod }

            // Texto semafórico del estado actual de incidencias
            val estadoDescriptivo = when {
                abiertas > 0 -> if (abiertas == 1) "🔴 Estado actual: tiene 1 incidencia FOD abierta"
                else "🔴 Estado actual: tiene $abiertas incidencias FOD abiertas"
                enProceso > 0 -> if (enProceso == 1) "🟡 Estado actual: tiene 1 incidencia FOD en proceso"
                else "🟡 Estado actual: tiene $enProceso incidencias FOD en proceso"
                else -> "🟢 Estado actual: sin incidencias activas"
            }

            val actividadHoy = when (inspeccionesHoy) {
                0 -> "📋 Inspecciones realizadas hoy: ninguna"
                1 -> "📋 Inspecciones realizadas hoy: 1"
                else -> "📋 Inspecciones realizadas hoy: $inspeccionesHoy"
            }

            val resultadoHoy = when {
                inspeccionesHoy == 0 -> "ℹ️ Hoy todavía no hay inspecciones registradas"
                inspeccionesHoyConFod == 0 -> "✅ Todas las inspecciones de hoy están sin FOD"
                inspeccionesHoyConFod == 1 -> "🚨 Hoy se ha detectado FOD en 1 inspección"
                else -> "🚨 Hoy se ha detectado FOD en $inspeccionesHoyConFod inspecciones"
            }

            val tarjeta = layoutInflater.inflate(R.layout.item_estado_aeronave_dashboard, llEstadoAeronaves, false)

            val cardEstadoAeronave = tarjeta.findViewById<MaterialCardView>(R.id.cardEstadoAeronave)
            tarjeta.findViewById<TextView>(R.id.tvAeronaveEstado).text = "${aeronave.modelo} - ${aeronave.numeroSerie}"
            tarjeta.findViewById<TextView>(R.id.tvEstadoActualAeronave).text = estadoDescriptivo
            tarjeta.findViewById<TextView>(R.id.tvActividadHoyAeronave).text = actividadHoy
            tarjeta.findViewById<TextView>(R.id.tvResultadoHoyAeronave).text = resultadoHoy

            cardEstadoAeronave.setCardBackgroundColor(when {
                abiertas > 0 || inspeccionesHoyConFod > 0 -> colorFondoEstadoRojo
                enProceso > 0 -> colorFondoEstadoAmarillo
                inspeccionesHoy == 0 -> colorFondoEstadoNeutro
                else -> colorFondoEstadoVerde
            })

            llEstadoAeronaves.addView(tarjeta)
        }
    }

    /**
     * Muestra las últimas 5 incidencias FOD ordenadas de más reciente a más antigua.
     * Por cada incidencia muestra: aeronave, fecha de detección, duración,
     * declarante (nombre + número de empleado), estado y zona.
     */
    private fun pintarUltimasIncidencias(
        incidencias: List<IncidenciaFod>,
        aeronavesMap: Map<Int, String>,
        usuariosMap: Map<Int, Usuario>
    ) {
        llUltimasIncidencias.removeAllViews()

        val ultimas = incidencias.sortedByDescending { it.createdAt ?: "" }.take(5)

        if (ultimas.isEmpty()) {
            llUltimasIncidencias.addView(crearLineaDashboard("No hay incidencias recientes."))
            return
        }

        ultimas.forEach { incidencia ->
            val aeronaveTexto = incidencia.aeronaveId?.let { aeronavesMap[it] } ?: "Sin aeronave"
            val usuario = incidencia.usuarioId?.let { usuariosMap[it] }

            // Prioriza el número de empleado guardado en la incidencia; si es null,
            // usa el del perfil del usuario; si tampoco existe, muestra un placeholder
            val declarante = if (usuario != null) {
                "${usuario.nombre} ${usuario.apellidos} · ${incidencia.numeroEmpleado ?: usuario.numeroEmpleado ?: "Sin nº empleado"}"
            } else {
                "Usuario desconocido · ${incidencia.numeroEmpleado ?: "Sin nº empleado"}"
            }

            val estado = when (incidencia.estado) {
                "abierta"    -> "🔴 Abierta"
                "en_proceso" -> "🟡 En proceso"
                "cerrada"    -> "🟢 Cerrada"
                else         -> incidencia.estado ?: "Estado no disponible"
            }

            val texto = buildString {
                append("$aeronaveTexto\n")
                append("Detectada: ${formatearFechaHora(incidencia.createdAt)}\n")
                append("${calcularDuracionDashboard(incidencia.estado, incidencia.createdAt, incidencia.fechaCierre)}\n")
                append("$declarante\n")
                append("$estado · Zona: ${incidencia.zonaAvion ?: "No especificada"}")
            }

            llUltimasIncidencias.addView(crearLineaDashboard(texto))
        }
    }

    /**
     * Agrupa todas las inspecciones por aeronave y día, calcula el estado de
     * completitud y FOD de cada jornada, y muestra las 5 más recientes.
     *
     * Lógica de agrupación:
     * - Clave: "aeronave_id|YYYY-MM-DD".
     * - Por cada grupo se determinan las zonas inspeccionadas, las faltantes
     *   (comparando con zonasObligatorias) y las zonas con FOD.
     *
     * Color de tarjeta: rojo si hubo FOD, naranja si faltan zonas, verde si completa y limpia.
     */
    private fun pintarUltimasInspeccionesGlobales(
        inspecciones: List<Inspeccion>,
        aeronavesMap: Map<Int, String>
    ) {
        llUltimasInspeccionesGlobales.removeAllViews()

        val inspeccionesConFecha = inspecciones.filter { !it.fecha.isNullOrBlank() }

        // Agrupa por aeronave + día para obtener el estado global de cada jornada
        val grupos = inspeccionesConFecha.groupBy { inspeccion ->
            val dia = inspeccion.fecha!!.substring(0, 10)
            val aeronave = inspeccion.aeronaveId ?: -1
            "$aeronave|$dia"
        }

        val resumenes = grupos.map { (clave, listaGrupo) ->
            val (aeronaveId, fechaDia) = clave.split("|").let { it[0].toInt() to it[1] }
            val zonasInspeccionadasSet = listaGrupo.map { it.zona }.toSet()

            InspeccionGlobalResumen(
                fechaDia = fechaDia,
                aeronaveTexto = aeronavesMap[aeronaveId] ?: "Sin aeronave",
                zonasInspeccionadas = zonasObligatorias.filter { it in zonasInspeccionadasSet },
                zonasFaltantes = zonasObligatorias.filter { it !in zonasInspeccionadasSet },
                zonasConFod = listaGrupo.filter { it.conFod }.map { it.zona }.distinct(),
                hayFod = listaGrupo.any { it.conFod }
            )
        }.sortedByDescending { it.fechaDia }.take(5)

        if (resumenes.isEmpty()) {
            llUltimasInspeccionesGlobales.addView(crearLineaDashboard("No hay inspecciones globales recientes."))
            return
        }

        resumenes.forEach { resumen ->
            val tarjeta = layoutInflater.inflate(
                R.layout.item_inspeccion_global_dashboard, llUltimasInspeccionesGlobales, false
            )

            val cardInspeccionGlobal = tarjeta.findViewById<MaterialCardView>(R.id.cardInspeccionGlobal)
            tarjeta.findViewById<TextView>(R.id.tvAeronaveGlobal).text = resumen.aeronaveTexto
            tarjeta.findViewById<TextView>(R.id.tvFechaGlobal).text = formatearSoloFecha(resumen.fechaDia)

            val tvCompletitud = tarjeta.findViewById<TextView>(R.id.tvEstadoCompletitudGlobal)
            if (resumen.zonasFaltantes.isEmpty()) {
                tvCompletitud.text = "✅ Completa (${resumen.zonasInspeccionadas.size}/${zonasObligatorias.size} zonas)"
                tvCompletitud.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            } else {
                tvCompletitud.text = "⚠️ Incompleta (${resumen.zonasInspeccionadas.size}/${zonasObligatorias.size} zonas)"
                tvCompletitud.setTextColor(colorNaranjaPendiente)
            }

            val tvFod = tarjeta.findViewById<TextView>(R.id.tvEstadoFodGlobal)
            if (resumen.hayFod) {
                tvFod.text = "🚨 Se detectó FOD en ${resumen.zonasConFod.size} zona(s)"
                tvFod.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            } else {
                tvFod.text = "🟢 Sin FOD declarado en esa inspección global"
                tvFod.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            }

            cardInspeccionGlobal.setCardBackgroundColor(when {
                resumen.hayFod -> colorFondoRojoSuave
                resumen.zonasFaltantes.isNotEmpty() -> colorFondoNaranjaSuave
                else -> colorFondoVerdeSuave
            })

            // Secciones opcionales: solo visibles si tienen contenido
            mostrarSeccionZonas(tarjeta, R.id.tvTituloZonasInspeccionadasGlobal, R.id.llZonasInspeccionadasGlobal,
                resumen.zonasInspeccionadas) { crearLineaZonaInspeccionada(it) }

            mostrarSeccionZonas(tarjeta, R.id.tvTituloZonasFodGlobal, R.id.llZonasFodGlobal,
                resumen.zonasConFod, configTitulo = { tv ->
                    tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                }) { crearLineaZonaConFodRoja(it) }

            mostrarSeccionZonas(tarjeta, R.id.tvTituloPendientesGlobal, R.id.llPendientesGlobal,
                resumen.zonasFaltantes, configTitulo = { tv ->
                    tv.setTextColor(colorNaranjaPendiente)
                }) { crearLineaChecklistPendienteNaranja(it) }

            llUltimasInspeccionesGlobales.addView(tarjeta)
        }
    }

    /**
     * Muestra u oculta una sección de zonas dentro de una tarjeta de inspección global.
     * Si [zonas] está vacío, oculta tanto el título como el contenedor.
     *
     * @param tarjeta        Vista raíz de la tarjeta inflada.
     * @param idTitulo       ID del TextView del título de la sección.
     * @param idContenedor   ID del LinearLayout que contendrá las filas de zonas.
     * @param zonas          Lista de zonas a mostrar.
     * @param configTitulo   Configuración adicional del TextView del título (color, etc.).
     * @param crearFila      Función que crea el TextView visual para cada zona.
     */
    private fun mostrarSeccionZonas(
        tarjeta: View,
        idTitulo: Int,
        idContenedor: Int,
        zonas: List<String>,
        configTitulo: ((TextView) -> Unit)? = null,
        crearFila: (String) -> TextView
    ) {
        val tvTitulo = tarjeta.findViewById<TextView>(idTitulo)
        val llContenedor = tarjeta.findViewById<LinearLayout>(idContenedor)

        if (zonas.isNotEmpty()) {
            tvTitulo.visibility = View.VISIBLE
            llContenedor.visibility = View.VISIBLE
            llContenedor.removeAllViews()
            configTitulo?.invoke(tvTitulo)
            zonas.forEach { llContenedor.addView(crearFila(it)) }
        } else {
            tvTitulo.visibility = View.GONE
            llContenedor.visibility = View.GONE
        }
    }

    /** Crea un TextView estándar para las líneas de texto del dashboard. */
    private fun crearLineaDashboard(texto: String): TextView {
        return TextView(requireContext()).apply {
            text = texto
            textSize = 14f
            setLineSpacing(0f, 1.15f)
            setPadding(0, 8, 0, 12)
        }
    }

    /** Crea una fila de zona inspeccionada (☑ texto, color negro). */
    private fun crearLineaZonaInspeccionada(zona: String): TextView {
        return TextView(requireContext()).apply {
            text = "☑ $zona"
            textSize = 13f
            setPadding(12, 2, 0, 2)
            setLineSpacing(0f, 1.1f)
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        }
    }

    /** Crea una fila de zona pendiente de inspección (☐ texto, color naranja). */
    private fun crearLineaChecklistPendienteNaranja(zona: String): TextView {
        return TextView(requireContext()).apply {
            text = "☐ $zona"
            textSize = 13f
            setPadding(12, 2, 0, 2)
            setLineSpacing(0f, 1.1f)
            setTextColor(colorNaranjaPendiente)
        }
    }

    /** Crea una fila de zona con FOD detectado (🚨 texto, color rojo). */
    private fun crearLineaZonaConFodRoja(zona: String): TextView {
        return TextView(requireContext()).apply {
            text = "🚨 $zona"
            textSize = 13f
            setPadding(12, 2, 0, 2)
            setLineSpacing(0f, 1.1f)
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        }
    }

    /**
     * Parsea una fecha ISO 8601 a [LocalDateTime].
     * Intenta primero con offset ([OffsetDateTime]) y luego sin él ([LocalDateTime]).
     * Devuelve null si la cadena es nula, vacía o tiene un formato no reconocido.
     */
    private fun parseFecha(fechaIso: String?): LocalDateTime? {
        return try {
            if (fechaIso.isNullOrBlank()) return null
            OffsetDateTime.parse(fechaIso).toLocalDateTime()
        } catch (e: Exception) {
            try { LocalDateTime.parse(fechaIso) } catch (_: Exception) { null }
        }
    }

    /**
     * Calcula y formatea la duración de una incidencia como texto legible.
     * - Si está cerrada: "✅ Estuvo abierta N día(s)".
     * - Si está abierta/en proceso: "⏳ Abierta desde hace N día(s)".
     * - Si no se puede calcular: "Duración: no disponible".
     *
     * La duración se calcula entre [createdAt] y [fechaCierre] (si cerrada)
     * o entre [createdAt] y ahora (si sigue abierta).
     */
    private fun calcularDuracionDashboard(
        estado: String?,
        createdAt: String?,
        fechaCierre: String?
    ): String {
        val inicio = parseFecha(createdAt) ?: return "Duración: no disponible"
        val fin = if (estado == "cerrada" && !fechaCierre.isNullOrBlank()) {
            parseFecha(fechaCierre)
        } else {
            OffsetDateTime.now().toLocalDateTime()
        } ?: return "Duración: no disponible"

        val dias = Duration.between(inicio, fin).toDays().coerceAtLeast(0)

        return when (estado) {
            "cerrada" -> if (dias == 1L) "✅ Estuvo abierta 1 día" else "✅ Estuvo abierta $dias días"
            "abierta", "en_proceso" -> if (dias == 1L) "⏳ Abierta desde hace 1 día" else "⏳ Abierta desde hace $dias días"
            else -> if (dias == 1L) "Duración: 1 día" else "Duración: $dias días"
        }
    }

    /**
     * Formatea una fecha ISO 8601 al patrón "dd/MM/yyyy HH:mm".
     * Devuelve "No disponible" si la cadena es nula o tiene formato no reconocido.
     */
    private fun formatearFechaHora(fechaIso: String?): String {
        return try {
            parseFecha(fechaIso)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) ?: "No disponible"
        } catch (e: Exception) {
            fechaIso ?: "No disponible"
        }
    }

    /**
     * Convierte una fecha en formato "YYYY-MM-DD" al patrón "dd/MM/yyyy".
     * Devuelve "Sin fecha" si la cadena es nula o no se puede parsear.
     */
    private fun formatearSoloFecha(fechaIso: String?): String {
        return fechaIso?.let {
            try {
                val (anio, mes, dia) = it.split("-")
                "$dia/$mes/$anio"
            } catch (e: Exception) { it }
        } ?: "Sin fecha"
    }
}