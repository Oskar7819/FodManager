package com.example.fodmanager.data.auth

import android.content.Context
import android.content.Intent
import com.example.fodmanager.ui.home.HomeActivity
import com.example.fodmanager.ui.main.MainActivity
import com.example.fodmanager.data.repository.AuthRepository

// Objeto singleton encargado de controlar la navegación inicial de la app
object SessionController {

    // Función que decide qué pantalla debe abrirse al iniciar,
    // en función de si existe una sesión activa o no
    fun abrirPantallaInicial(context: Context): Intent {
        return if (AuthRepository.haySesionActiva()) {
            // Si hay una sesión activa, se redirige a la pantalla principal del usuario
            Intent(context, HomeActivity::class.java)
        } else {
            // Si no hay sesión activa, se redirige a la pantalla de inicio o acceso
            Intent(context, MainActivity::class.java)
        }
    }
}