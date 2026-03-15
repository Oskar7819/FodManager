package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Clase que representa una inspección realizada
// Se mapea directamente con la tabla "inspecciones" de Supabase
@Serializable
data class Inspeccion(
    // Identificador único de la inspección en la base de datos
    val id: Int = 0,

    // ID del usuario que realizó la inspección
    @SerialName("usuario_id") val usuarioId: Int = 0,

    // Fecha y hora en que se realizó la inspección, generada automáticamente por Supabase
    val fecha: String? = null,

    // Zona del hangar o aeronave donde se realizó la inspección
    val zona: String = "",

    // Observaciones adicionales del inspector sobre la inspección (puede ser null)
    val observaciones: String? = null,

    // Indica si durante la inspección se encontró algún objeto FOD
    // true → se encontró FOD, lo que permite registrar incidencias
    // false → inspección correcta, sin FOD´s encontrados
    @SerialName("con_fod") val conFod: Boolean = false,

    // ID de la aeronave inspeccionada (puede ser null si no está asociada a una aeronave)
    @SerialName("aeronave_id") val aeronaveId: Int? = null,

    // Ubicación específica de la aeronave dentro del hangar
    @SerialName("ubicacion_aeronave") val ubicacionAeronave: String? = null
)