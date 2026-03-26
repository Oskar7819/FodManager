package com.example.fodmanager.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage

/**
 * Instancia global del cliente Supabase.
 *
 * Se inicializa una única vez al arrancar la aplicación y es accesible
 * desde cualquier capa (UI, repositorios, etc.) mediante importación directa.
 *
 * Módulos instalados:
 * - [Postgrest] → consultas SQL (SELECT, INSERT, UPDATE, DELETE) sobre las tablas.
 * - [Auth]      → autenticación de usuarios (login, registro, gestión de sesiones).
 * - [Storage]   → subida y descarga de imágenes de incidencias FOD.
 *
 * NOTA DE SEGURIDAD: se usa la `anon key`, que es pública y está limitada por las
 * políticas RLS (Row Level Security) configuradas en Supabase.
 * Nunca debe usarse la `service_role key` en el cliente móvil, ya que
 * saltaría todas las restricciones de seguridad.
 */
val supabase = createSupabaseClient(
    supabaseUrl = "https://qrtgaaebinyjawjklvyn.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFydGdhYWViaW55amF3amtsdnluIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI2NDQyMTksImV4cCI6MjA4ODIyMDIxOX0.eNwOMFjs0sqNX-Tug4xGCcJRqrfCkELueWFzAguJk3Q"
) {
    install(Postgrest)
    install(Auth)
    install(Storage)
}