package com.example.fodmanager.data.repository

import com.example.fodmanager.data.models.Usuario
import com.example.fodmanager.data.remote.supabase
import io.github.jan.supabase.postgrest.*

// Objeto encargado de gestionar las operaciones relacionadas con los usuarios
object UsuarioRepository {

    // Obtiene el usuario actual desde la base de datos
    suspend fun getUsuarioActual(): Usuario {
        // Recupera el ID del usuario autenticado
        val authUserId = AuthRepository.getCurrentAuthUserId()

        // Recupera el email del usuario autenticado
        val email = AuthRepository.getCurrentUserEmail()

        // Si no existe ni ID de autenticación ni email, no hay sesión activa
        if (authUserId == null && email.isNullOrBlank()) {
            throw IllegalStateException("No hay sesión activa")
        }

        // Si existe el ID del usuario autenticado, se intenta buscar primero por ese campo
        if (authUserId != null) {
            try {
                return supabase.postgrest["usuarios"]
                    .select {
                        filter {
                            // Filtra por el campo auth_user_id
                            eq("auth_user_id", authUserId)
                        }
                    }
                    // Decodifica el resultado en un objeto Usuario
                    .decodeSingle<Usuario>()
            } catch (_: Exception) {
                // Si falla la búsqueda por auth_user_id, se continúa con la búsqueda por email
            }
        }

        // Si no se pudo obtener por auth_user_id, se busca por email
        return supabase.postgrest["usuarios"]
            .select {
                filter {
                    // Filtra por el campo email
                    eq("email", email ?: "")
                }
            }
            // Decodifica el resultado en un objeto Usuario
            .decodeSingle<Usuario>()
    }

    // Valida si el usuario puede acceder después del login
    suspend fun validarAccesoPostLogin(): Usuario {
        // Obtiene los datos del usuario actual
        val usuario = getUsuarioActual()

        // Si el usuario está inactivo, se cierra la sesión y se lanza una excepción
        if (!usuario.activo) {
            AuthRepository.logout()
            throw IllegalStateException("Tu usuario está inactivo")
        }

        // Devuelve el usuario si está activo
        return usuario
    }
}