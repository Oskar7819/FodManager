package com.example.fodmanager.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage


// Importa el módulo de autenticación de Supabase
// Importa la función para crear el cliente de Supabase
// Importa el módulo Postgrest para trabajar con la base de datos
// Importa el módulo Storage para gestionar archivos

// URL de tu proyecto Supabase
const val SUPABASE_URL = "https://qrtgaaebinyjawjklvyn.supabase.co"

// OJO:
// aquí debes poner tu ANON KEY / PUBLISHABLE KEY del proyecto.
// Nunca pongas aquí la service_role key.
const val SUPABASE_ANON_KEY =  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFydGdhYWViaW55amF3amtsdnluIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI2NDQyMTksImV4cCI6MjA4ODIyMDIxOX0.eNwOMFjs0sqNX-Tug4xGCcJRqrfCkELueWFzAguJk3Q"

// Cliente global de Supabase para toda la app
val supabase = createSupabaseClient(
    // URL base del proyecto Supabase
    supabaseUrl = SUPABASE_URL,

    // Clave pública del proyecto Supabase
    supabaseKey = SUPABASE_ANON_KEY
) {
    // Postgrest para acceder a tablas
    install(Postgrest)

    // Auth para login, logout y sesión
    install(Auth)

    // Storage si tu app ya lo usa
    install(Storage)
}