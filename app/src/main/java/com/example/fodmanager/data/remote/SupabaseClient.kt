package com.example.fodmanager.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage

// Instancia global del cliente Supabase, accesible desde cualquier parte de la app.
// Se crea una única vez al iniciar la app (patrón singleton implícito en Kotlin
// mediante una variable de nivel de fichero).
val supabase = createSupabaseClient(
    // URL del proyecto Supabase, identifica a qué base de datos nos conectamos
    supabaseUrl = "https://qrtgaaebinyjawjklvyn.supabase.co",

    // Clave anónima (anon key) del proyecto Supabase.
    // Esta clave es pública y permite acceso limitado según las políticas RLS
    // (Row Level Security) configuradas en Supabase.
    // NOTA: en producción nunca se debe usar la service_role key en el cliente,
    // ya que daría acceso total sin restricciones de seguridad.
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFydGdhYWViaW55amF3amtsdnluIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI2NDQyMTksImV4cCI6MjA4ODIyMDIxOX0.eNwOMFjs0sqNX-Tug4xGCcJRqrfCkELueWFzAguJk3Q"
) {
    // Instala el módulo Postgrest para poder hacer consultas SQL
    // a las tablas de la base de datos (SELECT, INSERT, UPDATE, DELETE)
    install(Postgrest)

    // Instala el módulo Auth para gestionar la autenticación de usuarios
    // (login, registro, gestión de sesiones)
    install(Auth)

    // Instala el módulo Storage para subir y recuperar archivos
    // En esta app se usa para almacenar las fotos de las incidencias FOD
    // en el bucket "fod-images"
    install(Storage)
}