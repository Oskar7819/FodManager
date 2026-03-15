package com.example.fodmanager.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.models.Inspeccion
import com.example.fodmanager.data.models.IncidenciaFod
import com.example.fodmanager.data.models.Aeronave
import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Clase auxiliar para deserializar los datos del usuario logueado desde Supabase.
// Incluye solo los campos necesarios para el Dashboard.
@Serializable
data class UsuarioHome(
    val id: Int,
    val nombre: String,
    val rol: String,
    // aeronave_id puede ser null para roles generales que no están adscritos a ninguna aeronave
    val aeronave_id: Int? = null
)

// Fragment que muestra el Dashboard principal de la app en el tab "Inicio".
// Muestra un resumen de la actividad del hangar adaptado al rol del usuario:
// - rolesGenerales (administrador, head_plant, focal_point_fod) refleja  visión global de todo el hangar
// - Resto de roles (mando_gp4, quality, operario) → visión filtrada por su aeronave
class HomeFragment : Fragment() {

    private lateinit var tvBienvenida: TextView
    private lateinit var tvTotalInspecciones: TextView
    private lateinit var tvIncidenciasAbiertas: TextView
    private lateinit var tvIncidenciasEnProceso: TextView
    private lateinit var tvIncidenciasCerradas: TextView
    private lateinit var tvTotalAeronaves: TextView
    // LinearLayout dinámico donde se añaden las últimas inspecciones
    private lateinit var llUltimasInspecciones: LinearLayout

    // Roles que tienen visión general de todas las aeronaves
    private val rolesGenerales = listOf("administrador", "head_plant", "focal_point_fod")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Inicialización de los elementos visuales del layout
        tvBienvenida = view.findViewById(R.id.tvBienvenida)
        tvTotalInspecciones = view.findViewById(R.id.tvTotalInspecciones)
        tvIncidenciasAbiertas = view.findViewById(R.id.tvIncidenciasAbiertas)
        tvIncidenciasEnProceso = view.findViewById(R.id.tvIncidenciasEnProceso)
        tvIncidenciasCerradas = view.findViewById(R.id.tvIncidenciasCerradas)
        tvTotalAeronaves = view.findViewById(R.id.tvTotalAeronaves)
        llUltimasInspecciones = view.findViewById(R.id.llUltimasInspecciones)

        cargarDatos()
        return view
    }

    // Carga todos los datos del Dashboard desde Supabase según el rol del usuario
    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                // Obtiene el email de la sesión actual de Supabase Auth
                val email = supabase.auth.currentSessionOrNull()?.user?.email

                // Consulta el usuario logueado para obtener su nombre, rol y aeronave asignada
                val usuario = supabase.postgrest["usuarios"]
                    .select { filter { eq("email", email ?: "") } }
                    .decodeSingle<UsuarioHome>()

                // Muestra el mensaje de bienvenida personalizado con el nombre del usuario
                tvBienvenida.text = "Hola, ${usuario.nombre} "

                // Determina si el usuario tiene visión general o filtrada por aeronave
                val esGeneral = usuario.rol in rolesGenerales

                // Carga inspecciones: todas o filtradas por aeronave según el rol
                val inspecciones = if (esGeneral) {
                    supabase.postgrest["inspecciones"]
                        .select()
                        .decodeList<Inspeccion>()
                } else {
                    // Filtra por la aeronave asignada al usuario
                    // Si aeronave_id es null usa -1 para que no devuelva resultados
                    supabase.postgrest["inspecciones"]
                        .select { filter { eq("aeronave_id", usuario.aeronave_id ?: -1) } }
                        .decodeList<Inspeccion>()
                }

                tvTotalInspecciones.text = "Total: ${inspecciones.size} inspecciones"

                // Obtiene las últimas 5 inspecciones ordenadas por fecha descendente
                // (una por día, los últimos 5 días)
                val ultimas = inspecciones
                    .sortedByDescending { it.fecha }
                    .take(5)

                // Limpia el LinearLayout antes de añadir las nuevas inspecciones
                llUltimasInspecciones.removeAllViews()
                if (ultimas.isEmpty()) {
                    // Si no hay inspecciones recientes muestra un mensaje informativo
                    val tv = TextView(requireContext())
                    tv.text = "Sin inspecciones recientes"
                    tv.textSize = 14f
                    llUltimasInspecciones.addView(tv)
                } else {
                    // Crea dinámicamente un TextView por cada inspección reciente
                    ultimas.forEach { inspeccion ->
                        val tv = TextView(requireContext())
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
                        tv.text = "• ${inspeccion.zona} - $fechaFormateada"
                        tv.textSize = 14f
                        tv.setPadding(0, 4, 0, 4)
                        llUltimasInspecciones.addView(tv)
                    }
                }

                // Carga incidencias FOD: todas o filtradas por aeronave según el rol
                val incidencias = if (esGeneral) {
                    supabase.postgrest["incidencias_fod"]
                        .select()
                        .decodeList<IncidenciaFod>()
                } else {
                    supabase.postgrest["incidencias_fod"]
                        .select { filter { eq("aeronave_id", usuario.aeronave_id ?: -1) } }
                        .decodeList<IncidenciaFod>()
                }

                // Cuenta las incidencias por estado y las muestra con emojis de color
                tvIncidenciasAbiertas.text = "🔴 Abiertas: ${incidencias.count { it.estado == "abierta" }}"
                tvIncidenciasEnProceso.text = "🟡 En proceso: ${incidencias.count { it.estado == "en_proceso" }}"
                tvIncidenciasCerradas.text = "🟢 Cerradas: ${incidencias.count { it.estado == "cerrada" }}"

                // Carga aeronaves activas: todas o solo la del usuario según el rol
                val aeronaves = if (esGeneral) {
                    supabase.postgrest["aeronaves"]
                        .select { filter { eq("activa", true) } }
                        .decodeList<Aeronave>()
                } else {
                    // Para roles no generales filtra por su aeronave asignada
                    // y verifica además que esté activa
                    supabase.postgrest["aeronaves"]
                        .select { filter {
                            eq("id", usuario.aeronave_id ?: -1)
                            eq("activa", true)
                        } }
                        .decodeList<Aeronave>()
                }

                tvTotalAeronaves.text = "Total: ${aeronaves.size} aeronaves activas"

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}