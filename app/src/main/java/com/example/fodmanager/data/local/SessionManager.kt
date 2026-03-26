package com.example.fodmanager.data.local

/**
 * Singleton que almacena en memoria las credenciales del usuario autenticado.
 * Al ser un `object` de Kotlin, existe una única instancia durante toda
 * la ejecución de la aplicación (patrón Singleton).
 */
object SessionManager {

    /* Email del usuario que tiene la sesión activa en la aplicación. */
    var emailActual: String = ""

    // Contraseña del usuario que tiene la sesión activa.

    var passwordActual: String = ""
}