package com.example.fodmanager.ui.incidencias

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

/**
 * Utilidades para cargar imágenes corrigiendo la orientación EXIF.
 *
 * Esto evita que las fotos hechas en vertical aparezcan giradas
 * al mostrarlas o al subirlas al servidor.
 */
object ImageUtils {

    /**
     * Decodifica un archivo de imagen y aplica la rotación correcta
     * según los metadatos EXIF.
     */
    fun decodeBitmapCorregido(filePath: String): Bitmap {
        val bitmap = BitmapFactory.decodeFile(filePath)
            ?: throw IllegalArgumentException("No se pudo decodificar la imagen: $filePath")

        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        } else {
            bitmap
        }
    }

    /**
     * Reescala un bitmap manteniendo proporción.
     * Si ya es más pequeño que maxSize, lo devuelve tal cual.
     */
    fun escalarBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val ratio = minOf(
            maxSize.toFloat() / width.toFloat(),
            maxSize.toFloat() / height.toFloat()
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}