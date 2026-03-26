package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representa una aeronave registrada en el sistema.
 * Se mapea directamente con la tabla "aeronaves" de Supabase.
 *
 * Una aeronave puede tener asociados usuarios (operario, mando_gp4, quality)
 * e inspecciones. Cuando se marca como inactiva, sus usuarios quedan desasignados.
 */
@Serializable
data class Aeronave(

    /** Identificador único autogenerado por la base de datos. */
    val id: Int = 0,

    //  Modelo comercial de la aeronave .
    val modelo: String = "",

    /**
     * Número de serie único que identifica físicamente a la aeronave.
     * Mapeado desde la columna "numero_serie" (snake_case en BD y camelCase en Kotlin).
     */
    @SerialName("numero_serie") val numeroSerie: String = "",

    /**
     * Ubicación física de la aeronave dentro del hangar.
     * Es null si la aeronave aún no ha sido ubicada.
     */
    val ubicacion: String? = null,


     // Indica si la aeronave está operativa en el hangar.

    val activa: Boolean = true
)