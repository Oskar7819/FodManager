package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representa una inspección FOD realizada en el hangar.
 * Se mapea directamente con la tabla "inspecciones" de Supabase.
 */
@Serializable
data class Inspeccion(

    // Identificador único autogenerado por la base de datos.
    val id: Int = 0,

    //ID del usuario que llevó a cabo la inspección.

    @SerialName("usuario_id") val usuarioId: Int = 0,

    // Fecha y hora en que se realizó la inspección. Generada automáticamente por Supabase.


    val fecha: String? = null,

    // Fecha local de inspección en España.
    // La calcula Supabase mediante trigger.
    // Se usa para evitar duplicados por día y turno.
    @SerialName("fecha_inspeccion_dia")
    val fechaInspeccionDia: String? = null,



    // Zona de la aeronave donde se realizó la inspección

    val zona: String = "",

    // Observaciones libres del inspector sobre el resultado de la inspección.


    val observaciones: String? = null,

    // Indica si se encontró algún objeto FOD durante la inspección.

    @SerialName("con_fod") val conFod: Boolean = false,

    // ID de la aeronave sobre la que se realizó la inspección.

    @SerialName("aeronave_id") val aeronaveId: Int? = null,

    // Posición de la aeronave dentro del hangar en el momento de la inspección.


    @SerialName("ubicacion_aeronave") val ubicacionAeronave: String? = null,

    // Turno operativo de la inspección.
    // Lo calcula Supabase mediante trigger, no Android.
    // Valores posibles:
    // - manana
    // - tarde
    // - noche
    // - cuarto_turno
    @SerialName("turno_inspeccion")
val turnoInspeccion: String? = null
)