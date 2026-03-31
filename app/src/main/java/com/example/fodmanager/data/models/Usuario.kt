package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Indica que esta clase se puede serializar y deserializar
@Serializable
data class Usuario(
    // Identificador único del usuario
    val id: Int = 0,

    // Nombre del usuario
    val nombre: String = "",

    // Apellidos del usuario
    val apellidos: String = "",

    // Campo que viene de numero_empleado en la BD
    @SerialName("numero_empleado")
    // Número de empleado del usuario, puede ser nulo
    val numeroEmpleado: String? = null,

    // Correo electrónico del usuario
    val email: String = "",

    // Rol asignado al usuario dentro del sistema
    val rol: String = "",

    // Indica si el usuario está activo o no
    val activo: Boolean = true,

    // Campo que viene de aeronave_id en la BD
    @SerialName("aeronave_id")
    // Identificador de la aeronave asociada, puede ser nulo
    val aeronaveId: Int? = null,

    // Nuevo campo para enlazar con auth.users.id
    @SerialName("auth_user_id")
    // Identificador del usuario en el sistema de autenticación, puede ser nulo
    val authUserId: String? = null
)