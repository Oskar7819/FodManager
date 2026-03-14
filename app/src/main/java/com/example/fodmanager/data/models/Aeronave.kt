package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Anotación que permite serializar/deserializar esta clase a/desde JSON
// necesario para comunicarse con Supabase (PostgreSQL)
@Serializable
// data class es una clase especial de Kotlin optimizada para almacenar datos
// Se mapea directamente con la tabla "aeronaves" de Supabase
data class Aeronave(
    // Identificador único de la aeronave en la base de datos
    val id: Int = 0,

    // Modelo de la aeronave (ej: A320, A400M)
    val modelo: String = "",

    // @SerialName mapea el nombre del campo en Kotlin con el nombre de la columna en Supabase
    // En Kotlin usamos camelCase (numeroSerie) pero en la BD se usa snake_case (numero_serie)
    @SerialName("numero_serie") val numeroSerie: String = "",

    // Ubicación física de la aeronave en el hangar (puede ser null si no está definida)
    val ubicacion: String? = null,

    // Indica si la aeronave está activa (en servicio) o inactiva (se ha ido del hangar)
    // Cuando una aeronave se va, se marca como inactiva y sus usuarios quedan desasignados
    val activa: Boolean = true
)