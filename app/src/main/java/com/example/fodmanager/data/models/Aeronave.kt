package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Permitimos a Kotlin serializar/deserializar esta clase a/desde JSON
// necesario para comunicarse con Supabase
@Serializable
// data class es una clase especial de Kotlin optimizada para almacenar datos
// Se mapea directamente con la tabla "aeronaves" de Supabase
data class Aeronave(
    // Identificador único de la aeronave en la base de datos
    val id: Int = 0,

    // Modelo de la aeronave
    val modelo: String = "",

    // @SerialName mapea el nombre del campo en Kotlin con el nombre de la columna en Supabase
    // En Kotlin usamos camelCase (numeroSerie) pero en la BD se usa snake_case (numero_serie)
    @SerialName("numero_serie") val numeroSerie: String = "",

    // Ubicación física de la aeronave en el hangar pero puede ser null si no esta ubicada aún
    val ubicacion: String? = null,

    // Indica si la aeronave está en servicio  o se ha ido del hangar
    // Cuando una aeronave se va, se marca como inactiva y sus usuarios quedan desasignados
    val activa: Boolean = true
)