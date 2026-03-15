package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Clase que representa un usuario de la aplicación
// Se mapea directamente con la tabla usuarios de Supabase
@Serializable
data class Usuario(
    // Identificador único del usuario en la base de datos
    val id: Int = 0,

    // Nombre del usuario
    val nombre: String = "",

    // Apellidos del usuario
    val apellidos: String = "",

    // Número de empleado (puede ser null)
    @SerialName("numero_empleado") val numeroEmpleado: String? = null,

    // Email del usuario, usado también como credencial de acceso en Supabase Auth
    val email: String = "",

    // Rol del usuario en el sistema, determina qué puede ver y hacer en la aplicación
    // Roles disponibles:
    // "administrador" : acceso total al sistema
    // "mando_gp4" : gestiona operarios y ve datos de su aeronave
    // "quality" :  control de calidad, ve datos de su aeronave
    // "focal_point_fod"  : responsable FOD, ve todos los datos, gestiona mandos y quality
    // "head_plant" :  jefe de planta, visión general de todos los aviones
    // "operario" :  realiza inspecciones en su aeronave asignada
    val rol: String = "",

    // Indica si el usuario está activo en el sistema
    // Un usuario inactivo no puede acceder a la aplicación
    val activo: Boolean = true,

    // ID de la aeronave a la que está adscrito el usuario (puede ser null)
    // Solo aplica a roles: operario, mando_gp4, quality
    // Los roles administrador, focal_point_fod y head_plant no se adscriben a ninguna aeronave
    @SerialName("aeronave_id") val aeronaveId: Int? = null
)