package com.example.fodmanager.data.models

import kotlinx.serialization.Serializable

/**
 * Modelos de proyección parcial para consultas optimizadas a Supabase.
 *
 * En lugar de deserializar el modelo [Usuario] completo cada vez, estas clases
 * permiten seleccionar únicamente la columna necesaria en cada consulta
 * (usando `.select("columna")`), reduciendo el payload de red y evitando
 * procesar campos innecesarios.
 */

/**
 * Proyección que devuelve únicamente el rol de un usuario.
 *
 * Uso típico: verificar permisos y controlar la visibilidad de funcionalidades
 * en múltiples pantallas según el rol del usuario autenticado.
 *
 * Consulta equivalente: `SELECT rol FROM usuarios WHERE ...`
 */
@Serializable
data class UsuarioRol(val rol: String)

/**
 * Proyección que devuelve únicamente el ID interno de un usuario.
 *
 * Uso típico: obtener el ID del usuario autenticado para asociarlo como
 * [IncidenciaFod.usuarioId] al registrar un nuevo hallazgo FOD.
 *
 * Consulta equivalente: `SELECT id FROM usuarios WHERE ...`
 */
@Serializable
data class UsuarioId(val id: Int)

/**
 * Proyección que devuelve únicamente el nombre de un usuario.
 *
 * Uso típico: mostrar el nombre del inspector en la pantalla de detalle
 * de una [Inspeccion], sin necesidad de cargar el resto de sus datos.
 *
 * Consulta equivalente: `SELECT nombre FROM usuarios WHERE ...`
 */
@Serializable
data class UsuarioNombre(val nombre: String)