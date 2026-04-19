package com.example.fodmanager.ui.incidencias

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fodmanager.R
import io.getstream.photoview.PhotoView

/**
 * Activity para mostrar una imagen a pantalla completa.
 *
 * Funcionalidades:
 * - Visualización completa de la imagen
 * - Zoom con dedos (pinch zoom)
 * - Arrastre de la imagen
 * - Botón de cerrar
 */
class ImagenCompletaActivity : AppCompatActivity() {

    // Vista de imagen con zoom
    private lateinit var photoView: PhotoView

    // Botón de cerrar
    private lateinit var btnCerrar: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout pantalla completa
        setContentView(R.layout.activity_imagen_completa)

        // Inicialización de vistas
        photoView = findViewById(R.id.photoViewImagenCompleta)
        btnCerrar = findViewById(R.id.btnCerrarImagen)

        // Recupera la URL de la imagen
        val imagenUrl = intent.getStringExtra("imagen_url")

        // Si no hay imagen, se cierra la activity
        if (imagenUrl.isNullOrBlank()) {
            finish()
            return
        }

        // Carga la imagen con Glide
        Glide.with(this)
            .load(imagenUrl)
            .into(photoView)

        // Acción botón cerrar
        btnCerrar.setOnClickListener {
            finish()
        }
    }
}