package com.example.fodmanager.data.remote

import com.example.fodmanager.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Indica que esta clase se puede serializar para enviarla como JSON
@Serializable
data class CreateUserRequest(
    // Nombre del usuario a crear
    val nombre: String,

    // Apellidos del usuario a crear
    val apellidos: String,

    // Número de empleado, puede ser nulo
    val numero_empleado: String?,

    // Correo electrónico del usuario
    val email: String,

    // Contraseña del usuario
    val password: String,

    // Rol asignado al usuario
    val rol: String,

    // ID de la aeronave asociada, puede ser nulo
    val aeronave_id: Int?
)

// Indica que esta clase se puede serializar para enviar los datos de una notificación FOD
@Serializable
data class NotificacionFodRequest(
    // ID de la incidencia creada en Supabase
    val incidencia_id: Int,

    // Prioridad de la incidencia: en tu proyecto solo usaremos "alta" o "baja"
    val prioridad: String,

    // ID de la aeronave asociada a la incidencia, puede ser nulo
    val aeronave_id: Int?,

    // Zona de la aeronave donde se encontró el FOD
    val zona: String?,

    // Descripción introducida por el usuario al crear la incidencia
    val descripcion: String?
)

// Objeto encargado de comunicarse con las Edge Functions
object EdgeFunctionsClient {

    // Función suspendida que crea un usuario remoto mediante una petición HTTP
    suspend fun createUser(request: CreateUserRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Recuperamos el access token del usuario logueado
            val accessToken = AuthRepository.getAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("No hay sesión activa"))

            // URL de la función
            val url = URL("$SUPABASE_URL/functions/v1/create-user")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                // Método HTTP usado para enviar los datos
                requestMethod = "POST"

                // Permite enviar datos en el cuerpo de la petición
                doOutput = true

                // Tiempo máximo de espera para establecer conexión
                connectTimeout = 15000

                // Tiempo máximo de espera para leer la respuesta
                readTimeout = 15000

                // Cabeceras necesarias
                // Indica que el contenido enviado es JSON
                setRequestProperty("Content-Type", "application/json")

                // Clave pública de Supabase para autorizar la petición
                setRequestProperty("apikey", SUPABASE_ANON_KEY)

                // Token Bearer del usuario autenticado
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            // Serializamos el cuerpo JSON
            val body = Json.encodeToString(request)
            connection.outputStream.use { output ->
                // Escribimos el JSON en el cuerpo de la petición
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            // Obtenemos el código de estado HTTP de la respuesta
            val statusCode = connection.responseCode

            // Intentamos leer el contenido de la respuesta
            val responseText = try {
                val stream =
                    if (statusCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } catch (_: Exception) {
                ""
            } finally {
                // Cerramos la conexión siempre al terminar
                connection.disconnect()
            }

            // Si la respuesta fue correcta, devolvemos éxito
            if (statusCode in 200..299) {
                Result.success(Unit)
            } else {
                // Si hubo error, intentamos extraer el mensaje desde el JSON de respuesta
                val message = try {
                    JSONObject(responseText).optString("error", "Error creando usuario")
                } catch (_: Exception) {
                    "Error creando usuario"
                }
                Result.failure(IllegalStateException(message))
            }
        } catch (e: Exception) {
            // Si ocurre cualquier excepción, la devolvemos como fallo
            Result.failure(e)
        }
    }

    // Función suspendida que solicita a Supabase el envío de una notificación FOD mediante OneSignal
    suspend fun enviarNotificacionFod(request: NotificacionFodRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Recuperamos el access token del usuario logueado
                val accessToken = AuthRepository.getAccessToken()
                    ?: return@withContext Result.failure(IllegalStateException("No hay sesión activa"))

                // URL de la Edge Function que enviará la notificación FOD
                val url = URL("$SUPABASE_URL/functions/v1/enviar-notificacion-fod")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    // Método HTTP usado para enviar los datos
                    requestMethod = "POST"

                    // Permite enviar datos en el cuerpo de la petición
                    doOutput = true

                    // Tiempo máximo de espera para establecer conexión
                    connectTimeout = 15000

                    // Tiempo máximo de espera para leer la respuesta
                    readTimeout = 15000

                    // Cabeceras necesarias
                    // Indica que el contenido enviado es JSON
                    setRequestProperty("Content-Type", "application/json")

                    // Clave pública de Supabase para autorizar la petición
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)

                    // Token Bearer del usuario autenticado
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }

                // Serializamos el cuerpo JSON con los datos de la incidencia
                val body = Json.encodeToString(request)
                connection.outputStream.use { output ->
                    // Escribimos el JSON en el cuerpo de la petición
                    output.write(body.toByteArray(Charsets.UTF_8))
                }

                // Obtenemos el código de estado HTTP de la respuesta
                val statusCode = connection.responseCode

                // Intentamos leer el contenido de la respuesta
                val responseText = try {
                    val stream =
                        if (statusCode in 200..299) connection.inputStream else connection.errorStream
                    stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } catch (_: Exception) {
                    ""
                } finally {
                    // Cerramos la conexión siempre al terminar
                    connection.disconnect()
                }

                // Si la respuesta fue correcta, devolvemos éxito
                if (statusCode in 200..299) {
                    Result.success(Unit)
                } else {
                    // Si hubo error, intentamos extraer el mensaje desde el JSON de respuesta
                    val message = try {
                        JSONObject(responseText).optString("error", "Error enviando notificación")
                    } catch (_: Exception) {
                        "Error enviando notificación"
                    }

                    Result.failure(IllegalStateException(message))
                }

            } catch (e: Exception) {
                // Si ocurre cualquier excepción, la devolvemos como fallo
                Result.failure(e)
            }
        }
}