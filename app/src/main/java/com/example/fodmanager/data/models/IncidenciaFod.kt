package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Clase que representa una incidencia FOD (Foreign Object Damage)
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

    // Descripción del FOD
    val descripcion: String = "",

    // URL de la foto del FOD almacenada en Supabase Storage, si hay foto será null
    @SerialName("imagen_url") val imagenUrl: String? = null,

    // Estado actual de la incidencia según el ENUM definido en Supabase:
    // "abierta" si está pendiente de resolver
    // "en_proceso" si está pendiente de resolver
    // "cerrada"  si esta cerrada ya
    val estado: String = "abierta",

    // Nivel de prioridad de la incidencia (puede ser null)
    val prioridad: String? = null,

    // Zona específica del avión donde se encontró el FOD
    @SerialName("zona_avion") val zonaAvion: String? = null,

    // Número de empleado que encontró el FOD
    @SerialName("numero_empleado") val numeroEmpleado: String? = null,

    // Fecha y hora de creación de la incidencia, generada automáticamente por Supabase
    @SerialName("created_at") val createdAt: String? = null,

    // Clasificación del tipo de FOD según el ENUM tipo_fod definido en Supabase:
    // "ambiental" → suciedad y polvo
    // "herramientas" → llaves, destornilladores, etc.
    // "restos_metalicos" → tornillos, remaches, tuercas, etc.
    // "material_consumo" → trapos, guantes, bridas, etc.
    // "personal" → bolígrafos, monedas, tarjetas, etc.
    // "procedente_aeronave" → sellante, pintura desprendida, etc.
    @SerialName("tipo_fod") val tipoFod: String? = null
)