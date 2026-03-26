package com.example.fodmanager.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representa un usuario registrado en la aplicacion.
 * Se mapea directamente con la tabla "usuarios" de Supabase.
 *
 * Cada usuario tiene un rol que determina su nivel de acceso y las
 * operaciones que puede realizar. Adicionalmente, su identidad en
 * Supabase Auth queda vinculada mediante el [email].
 *
 * Roles disponibles y sus permisos generales:
 * - "administrador"     → acceso total al sistema; puede gestionar cualquier entidad.
 * - "focal_point_fod"   → visión global de todas las aeronaves; gestiona mandos y quality.
 * - "head_plant"        → visión general de todas las aeronaves; perfil de supervisión.
 * - "mando_gp4"         → gestiona operarios de su aeronave asignada.
 * - "quality"           → control de calidad sobre los datos de su aeronave asignada.
 * - "operario"          → realiza inspecciones en su aeronave asignada.
 */
@Serializable
data class Usuario(

    // Identificador único autogenerado por la base de datos.
    val id: Int = 0,

    // Nombre de pila del usuario.
    val nombre: String = "",

    // Apellidos del usuario.
    val apellidos: String = "",

    // Número de empleado corporativo del usuario.

    @SerialName("numero_empleado") val numeroEmpleado: String? = null,

    // Dirección de correo electrónico del usuario. Actúa también como credencial de acceso en Supabase Auth.

    val email: String = "",

    // Rol del usuario. Determina qué pantallas, datos y acciones están disponibles en la aplicación.

    val rol: String = "",

    // Indica si el usuario puede acceder a la aplicación.

    val activo: Boolean = true,

    /**
     * ID de la aeronave a la que está adscrito el usuario.
     * Solo aplica a los roles: "operario", "mando_gp4" y "quality".
     * Los roles "administrador", "focal_point_fod" y "head_plant" no se
     * adscriben a ninguna aeronave concreta (este campo será null para ellos).
     */
    @SerialName("aeronave_id") val aeronaveId: Int? = null
)