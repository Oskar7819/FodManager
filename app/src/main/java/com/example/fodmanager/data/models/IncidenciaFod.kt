package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representa un hallazgo de posible Foreign Object Damage (FOD) registrado en el sistema.
 * Se mapea directamente con la tabla "incidencias_fod" de Supabase.
 *
 * Su ciclo de vida queda reflejado en el campo estado y en la tabla historial_estados.
 *
 * @property createdAt  Fecha y hora en que se detectó/registró la incidencia (generada por Supabase).
 * @property fechaCierre Fecha en que la incidencia se cerró. Null mientras siga abierta o en proceso.
 */
@Serializable
data class IncidenciaFod(

    //Identificador único autogenerado por la base de datos.
    val id: Int,

    // ID de la aeronave en la que se encontró el FOD.

    @SerialName("aeronave_id")
    val aeronaveId: Int? = null,

    // Turno en el que apareció la incidencia FOD.
// Lo asigna Supabase copiándolo desde la inspección asociada.
// Valores posibles:
// - manana
// - tarde
// - noche
// - cuarto_turno
    @SerialName("turno_inspeccion")
    val turnoInspeccion: String? = null,

    // ID de la inspección durante la cual se detectó el FOD.

    @SerialName("inspeccion_id")
    val inspeccionId: Int? = null,

    // ID del usuario que registró la incidencia.

    @SerialName("usuario_id")
    val usuarioId: Int? = null,

    /** Descripciónb del objeto o daño encontrado. */
    val descripcion: String,

    // Estado actual de la incidencia en su ciclo de vida.

    val estado: String,

    /*
     * URL de la imagen almacenada en Supabase Storage que documenta el hallazgo.
     * Null si no se adjuntó ninguna fotografía al registrar la incidencia.
     */
    @SerialName("imagen_url")
    val imagenUrl: String? = null,

    /**
     * Nivel de urgencia de la incidencia.
     * Valores posibles: "baja", "media", "alta", "critica".
     * Null si no se especificó prioridad en el momento del registro.
     */
    @SerialName("prioridad")
    val prioridad: String? = null,

    // Zona específica de la aeronave donde se localizó el FOD. Null si no se especificó.

    @SerialName("zona_avion")
    val zonaAvion: String? = null,

    // Número de empleado que detectó el FOD, si aplica.

    @SerialName("numero_empleado")
    val numeroEmpleado: String? = null,

    // Marca temporal de creación del registro, generada automáticamente por Supabase.

    @SerialName("created_at")
    val createdAt: String? = null,

    // Fecha y hora en que se cerró la incidencia.Null mientras la incidencia permanezca abierta o en proceso.


    @SerialName("fecha_cierre")
    val fechaCierre: String? = null,

    // Categoría del objeto FOD encontrado.

    @SerialName("tipo_fod")
    val tipoFod: String? = null
)