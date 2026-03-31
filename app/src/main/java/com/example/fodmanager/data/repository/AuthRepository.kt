package com.example.fodmanager.data.repository

import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

// Objeto encargado de gestionar la autenticación del usuario
object AuthRepository {

    // Login con email/password contra Supabase Auth
    suspend fun login(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            // Asigna el email introducido por el usuario
            this.email = email

            // Asigna la contraseña introducida por el usuario
            this.password = password
        }
    }

    // Cierre de sesión
    suspend fun logout() {
        // Finaliza la sesión activa del usuario
        supabase.auth.signOut()
    }

    // Comprueba si ya hay una sesión abierta en el cliente
    fun haySesionActiva(): Boolean {
        // Devuelve true si existe una sesión activa, false si no existe
        return supabase.auth.currentSessionOrNull() != null
    }

    // Devuelve el email del usuario autenticado
    fun getCurrentUserEmail(): String? {
        // Obtiene el email del usuario de la sesión actual
        return supabase.auth.currentSessionOrNull()?.user?.email
    }

    // Devuelve el id del usuario autenticado en Auth
    fun getCurrentAuthUserId(): String? {
        // Obtiene el identificador del usuario autenticado
        return supabase.auth.currentSessionOrNull()?.user?.id
    }

    // Devuelve el access token actual para llamar a la Edge Function
    fun getAccessToken(): String? {
        // Obtiene el token de acceso de la sesión actual
        return supabase.auth.currentSessionOrNull()?.accessToken
    }
}