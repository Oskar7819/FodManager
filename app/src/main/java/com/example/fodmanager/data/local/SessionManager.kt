// Objeto singleton para almacenar temporalmente las credenciales del usuario logueado.
// Se usa para restaurar la sesión después de crear un nuevo usuario,
// ya que Supabase Auth cierra la sesión actual al registrar un nuevo usuario con signUpWith.
// Al ser un object (singleton), existe una única instancia durante toda la ejecución de la app.
package com.example.fodmanager.data.local

object SessionManager {
    // Email del usuario que ha iniciado sesión en la app
    var emailActual: String = ""

    // Contraseña del usuario que ha iniciado sesión en la app
    // Se almacena en memoria para poder restaurar la sesión tras crear un nuevo usuario
    var passwordActual: String = ""
}