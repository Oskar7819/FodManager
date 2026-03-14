package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Clase que representa una incidencia FOD (Foreign Object Debris/Damage)
// Se mapea directamente con la tabla "incidencias_fod" de Supabase
@Serializable
data class IncidenciaFod(
    // Identificador único de la incidencia en la base de datos
    val id: Int = 0,

    // ID de la aeronave donde se encontró el FOD (puede ser null)
    @SerialName("aeronave_id") val aeronaveId: Int? = null,

    // ID de la inspección durante la cual se detectó el FOD (puede ser null)
    @SerialName("inspeccion_id") val inspeccionId: Int? = null,

    // ID del usuario que registró la incidencia (puede ser null)
    @SerialName("usuario_id") val usuarioId: Int? = null,

    // Descripción detallada del objeto encontrado o del daño detectado
    val descripcion: String = "",

    // URL de la foto del FOD almacenada en Supabase Storage (puede ser null si no hay foto)
    @SerialName("imagen_url") val imagenUrl: String? = null,

    // Estado actual de la incidencia según el ENUM definido en Supabase:
    // "abierta" → recién registrada, pendiente de resolver
    // "en_proceso" → se está trabajando en su resolución
    // "cerrada" → resuelta y cerrada
    val estado: String = "abierta",

    // Nivel de prioridad de la incidencia (puede ser null)
    val prioridad: String? = null,

    // Zona específica del avión donde se encontró el FOD (ej: tren de aterrizaje, cabina)
    @SerialName("zona_avion") val zonaAvion: String? = null,

    // Número de empleado del operario que encontró el FOD
    @SerialName("numero_empleado") val numeroEmpleado: String? = null,

    // Fecha y hora de creación de la incidencia, generada automáticamente por Supabase
    @SerialName("created_at") val createdAt: String? = null,

    // Clasificación del tipo de FOD según el ENUM tipo_fod definido en Supabase:
    // "ambiental" → suciedad y polvo
    // "herramientas" → llaves, destornilladores, etc.
    // "restos_metalicos" → tornillos, remaches, tuercas, etc.
    // "material_consumo" → trapos, guantes, bridas, etc.
    // "personal" → bolígrafos, monedas, tarjetas, etc.
    // "procedente_aeronave" → sellante, pintura desprendida, juntas deterioradas, etc.
    @SerialName("tipo_fod") val tipoFod: String? = null
)