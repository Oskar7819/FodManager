package com.example.fodmanager.data.models

import kotlinx.serialization.Serializable

/* Archivo que contiene modelos compartidos y reutilizables en toda la aplicación
   Estas clases son versiones simplificadas del modelo Usuario completo,
   usadas cuando solo necesitamos un campo específico de la tabla "usuarios"
   para evitar deserializar datos innecesarios.  */

/* Clase usada para obtener únicamente el rol de un usuario desde Supabase.
   Se usa en múltiples pantallas para verificar permisos y controlar
   qué funcionalidades son visibles según el rol del usuario  */
@Serializable
data class UsuarioRol(val rol: String)

/*  Clase usada para obtener únicamente el ID de un usuario desde Supabase.
    Se usa principalmente al crear incidencias FOD para asociarlas
   al usuario que las registra mediante su ID.   */
@Serializable
data class UsuarioId(val id: Int)

/* Clase usada para obtener únicamente el nombre de un usuario desde Supabase.
   Se usa en la pantalla de detalle de inspección para mostrar
   el nombre del inspector que realizó la inspección  */
@Serializable
data class UsuarioNombre(val nombre: String)