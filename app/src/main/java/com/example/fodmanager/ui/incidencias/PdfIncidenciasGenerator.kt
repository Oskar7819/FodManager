// Define el paquete donde se encuentra este archivo dentro de la estructura del proyecto.
package com.example.fodmanager.ui.incidencias

// Importa la clase Context, necesaria para acceder a recursos y funciones del sistema Android.
import android.content.Context
// Importa Intent, utilizado para abrir o compartir el archivo PDF generado.
import android.content.Intent
// Importa Bitmap, que representa la imagen de la incidencia si existe evidencia fotográfica.
import android.graphics.Bitmap
// Importa Canvas, que permite dibujar texto, líneas e imágenes dentro del PDF.
import android.graphics.Canvas
// Importa Color, utilizado para definir los colores del texto y de las líneas.
import android.graphics.Color
// Importa Paint, que configura el estilo del dibujo: color, tamaño de texto, grosor, etc.
import android.graphics.Paint
// Importa PdfDocument, la clase encargada de crear el documento PDF.
import android.graphics.pdf.PdfDocument
// Importa Uri, normalmente usado para representar rutas seguras a archivos.
import android.net.Uri
// Importa Environment, utilizado para acceder a carpetas públicas del dispositivo.
import android.os.Environment

// Importa File para crear carpetas y archivos en el almacenamiento.
import java.io.File
// Importa FileOutputStream para escribir el contenido del PDF dentro del archivo final.
import java.io.FileOutputStream
// Importa FileProvider para poder abrir el PDF de forma segura desde otra aplicación.
import androidx.core.content.FileProvider

// Objeto encargado de generar y abrir informes PDF relacionados con incidencias FOD.
object PdfIncidenciaGenerator {

    // Función principal que crea un PDF con los datos de una incidencia concreta.
    fun generarPdfIncidencia(
        // Contexto de Android necesario para acceder al sistema de archivos y otros recursos.
        context: Context,
        // Identificador único de la incidencia que aparecerá en el informe.
        incidenciaId: Int,
        // Estado actual de la incidencia, por ejemplo abierta o cerrada.
        estado: String,
        // Prioridad asignada a la incidencia.
        prioridad: String,
        // Aeronave relacionada con la incidencia.
        aeronave: String,
        // Zona donde se ha detectado la incidencia FOD.
        zona: String,
        // Tipo de FOD detectado en la incidencia.
        tipoFod: String,
        // Fecha en la que se detectó la incidencia.
        fechaDeteccion: String,
        // Fecha de cierre de la incidencia, si ya ha sido cerrada.
        fechaCierre: String,
        // Número de días que la incidencia ha permanecido abierta.
        diasAbierta: String,
        // Nombre o datos del usuario que declara la incidencia.
        declarante: String,
        // Número de empleado del declarante.
        numeroEmpleado: String,
        // Descripción detallada de la incidencia.
        descripcion: String,
        // Imagen opcional que se añadirá como evidencia fotográfica si existe.
        imagen: Bitmap?
    ): File {
        // Crea un nuevo documento PDF vacío sobre el que se irá dibujando el contenido.
        val pdfDocument = PdfDocument()

        // Define el tamaño de la página PDF y su número de página.
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        // Inicia una nueva página dentro del documento PDF.
        val page = pdfDocument.startPage(pageInfo)
        // Obtiene el canvas de la página, que se usará para dibujar texto, líneas e imágenes.
        val canvas: Canvas = page.canvas

        // Crea el objeto Paint, que define cómo se dibujan los elementos en el PDF.
        val paint = Paint()
        // Activa el suavizado para que el texto y los elementos gráficos se vean mejor.
        paint.isAntiAlias = true

        // Variable que controla la posición vertical donde se irá escribiendo cada elemento.
        var y = 50

        // Título
        // Configura el color azul del título principal del informe.
        paint.color = Color.rgb(20, 70, 120)
        // Define el tamaño del texto del título.
        paint.textSize = 22f
        // Activa la negrita para destacar el título.
        paint.isFakeBoldText = true
        // Dibuja el título principal en la parte superior del PDF.
        canvas.drawText("Informe de Incidencia FOD", 40f, y.toFloat(), paint)

        // Aumenta la posición vertical para dejar espacio después del título.
        y += 35



        // Línea separadora
        // Configura el color gris claro para la línea separadora.
        paint.color = Color.LTGRAY
        // Define el grosor de la línea separadora.
        paint.strokeWidth = 2f
        // Dibuja una línea horizontal debajo del título.
        canvas.drawLine(40f, y.toFloat(), 555f, y.toFloat(), paint)

        // Desplaza la posición vertical para comenzar la sección de datos.
        y += 35

        // Datos principales
        // Configura el texto en color negro para el contenido principal.
        paint.color = Color.BLACK
        // Define un tamaño de texto más pequeño para los apartados del informe.
        paint.textSize = 14f
        // Activa la negrita para el encabezado de la sección.
        paint.isFakeBoldText = true
        // Dibuja el encabezado de la sección de datos de la incidencia.
        canvas.drawText("Datos de la incidencia", 40f, y.toFloat(), paint)

        // Deja espacio entre el encabezado y las líneas de información.
        y += 25
        // Desactiva la negrita para escribir los datos normales.
        paint.isFakeBoldText = false

        // Escribe cada dato principal de la incidencia y actualiza la posición vertical.
        y = escribirLinea(canvas, paint, "ID incidencia: $incidenciaId", y)
        y = escribirLinea(canvas, paint, "Estado: $estado", y)
        y = escribirLinea(canvas, paint, "Prioridad: $prioridad", y)
        y = escribirLinea(canvas, paint, "Aeronave: $aeronave", y)
        y = escribirLinea(canvas, paint, "$zona", y)
        y = escribirLinea(canvas, paint, "$tipoFod", y)
        y = escribirLinea(canvas, paint, "$fechaDeteccion", y)
        y = escribirLinea(canvas, paint, "$fechaCierre", y)
        y = escribirLinea(canvas, paint, "$diasAbierta", y)

        // Añade una pequeña separación antes de la sección del declarante.
        y += 15

        // Activa la negrita para el título de la sección del declarante.
        paint.isFakeBoldText = true
        // Dibuja el encabezado de la sección del declarante.
        canvas.drawText("Declarante", 40f, y.toFloat(), paint)

        // Deja espacio antes de escribir los datos del declarante.
        y += 25
        // Desactiva la negrita para los datos del declarante.
        paint.isFakeBoldText = false

        // Escribe los datos del declarante y actualiza la posición vertical.
        y = escribirLinea(canvas, paint, "$declarante", y)
        y = escribirLinea(canvas, paint, "$numeroEmpleado", y)

        // Añade separación antes de la descripción.
        y += 15

        // Activa la negrita para el encabezado de la descripción.
        paint.isFakeBoldText = true
        // Dibuja el encabezado de la sección de descripción.
        canvas.drawText("Descripción", 40f, y.toFloat(), paint)

        // Deja espacio antes de escribir el texto descriptivo.
        y += 25
        // Desactiva la negrita para escribir la descripción normal.
        paint.isFakeBoldText = false

        // Divide la descripción en varias líneas para que no se salga del ancho del PDF.
        val lineasDescripcion = dividirTexto(descripcion, 75)
        // Recorre cada línea de la descripción y la escribe en el documento.
        for (linea in lineasDescripcion) {
            y = escribirLinea(canvas, paint, linea, y)
        }

        // Deja espacio antes de añadir la imagen, en caso de existir.
        y += 20

        // Comprueba si se ha recibido una imagen para incluirla como evidencia fotográfica.
        if (imagen != null) {
            // Activa la negrita para el título de la sección de imagen.
            paint.isFakeBoldText = true
            // Dibuja el encabezado de evidencia fotográfica.
            canvas.drawText("Evidencia fotográfica", 40f, y.toFloat(), paint)

            // Deja espacio entre el encabezado y la imagen.
            y += 20

            // Escala la imagen para que encaje correctamente dentro del PDF.
            val imagenEscalada = escalarImagen(imagen, 400, 230)
            // Dibuja la imagen escalada en el documento PDF.
            canvas.drawBitmap(imagenEscalada, 40f, y.toFloat(), null)
        }

        // Finaliza la página actual para que quede guardada dentro del documento PDF.
        pdfDocument.finishPage(page)

        // Define la carpeta donde se guardará el PDF dentro de Documentos/FodManager.
        val carpeta = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "FodManager"
        )

        // Si la carpeta no existe, la crea antes de guardar el archivo.
        if (!carpeta.exists()) {
            carpeta.mkdirs()
        }

        // Crea el archivo PDF final usando el ID de la incidencia en el nombre.
        val archivo = File(carpeta, "incidencia_fod_$incidenciaId.pdf")

        // Escribe todo el contenido del documento PDF en el archivo físico.
        pdfDocument.writeTo(FileOutputStream(archivo))
        // Cierra el documento PDF para liberar recursos.
        pdfDocument.close()

        // Devuelve el archivo PDF generado para poder usarlo después.
        return archivo
    }

    // Función encargada de abrir o compartir el PDF generado mediante un Intent.
    fun compartirPdf(context: Context, archivo: File) {

        // Obtiene una URI segura del archivo PDF usando FileProvider.
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            archivo
        )

        // Crea un Intent para abrir el archivo PDF con una aplicación compatible.
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // Indica que el archivo que se va a abrir es un PDF.
            setDataAndType(uri, "application/pdf")

            // Permite abrir la actividad desde un contexto que no sea una Activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Concede permiso temporal de lectura a la aplicación que abra el PDF.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Lanza el Intent para abrir el PDF en el dispositivo.
        context.startActivity(intent)
    }




    // Función auxiliar que escribe una línea de texto en el PDF y devuelve la siguiente posición vertical.
    private fun escribirLinea(
        // Canvas sobre el que se dibuja la línea de texto.
        canvas: Canvas,
        // Paint con el estilo actual del texto.
        paint: Paint,
        // Texto que se va a escribir en el PDF.
        texto: String,
        // Posición vertical actual donde se dibujará el texto.
        y: Int
    ): Int {
        // Dibuja el texto en la posición indicada.
        canvas.drawText(texto, 40f, y.toFloat(), paint)
        // Devuelve una nueva posición vertical para la siguiente línea.
        return y + 22
    }

    // Función auxiliar que divide un texto largo en varias líneas con un máximo de caracteres.
    private fun dividirTexto(texto: String, maxCaracteres: Int): List<String> {
        // Si el texto cabe en una sola línea, se devuelve directamente como lista de una línea.
        if (texto.length <= maxCaracteres) return listOf(texto)

        // Separa el texto por palabras para evitar cortar una palabra por la mitad.
        val palabras = texto.split(" ")
        // Lista donde se guardarán las líneas resultantes.
        val lineas = mutableListOf<String>()
        // Variable que acumula las palabras de la línea actual.
        var lineaActual = ""

        // Recorre todas las palabras para construir líneas que no superen el límite indicado.
        for (palabra in palabras) {
            // Si al añadir la palabra se supera el máximo de caracteres, se guarda la línea actual.
            if ((lineaActual + palabra).length > maxCaracteres) {
                lineas.add(lineaActual.trim())
                lineaActual = palabra
            } else {
                // Si todavía cabe, se añade la palabra a la línea actual.
                lineaActual += " $palabra"
            }
        }

        // Añade la última línea si contiene texto.
        if (lineaActual.isNotBlank()) {
            lineas.add(lineaActual.trim())
        }

        // Devuelve la lista completa de líneas ya divididas.
        return lineas
    }

    // Función auxiliar que escala una imagen manteniendo su proporción original.
    private fun escalarImagen(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        // Calcula el factor de escala más adecuado para que la imagen no supere el ancho ni el alto máximos.
        val ratio = minOf(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )

        // Calcula el nuevo ancho de la imagen aplicando el ratio obtenido.
        val width = (bitmap.width * ratio).toInt()
        // Calcula la nueva altura de la imagen aplicando el mismo ratio para mantener la proporción.
        val height = (bitmap.height * ratio).toInt()

        // Crea y devuelve una nueva imagen escalada con las dimensiones calculadas.
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}