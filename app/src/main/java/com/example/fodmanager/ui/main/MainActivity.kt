package com.example.fodmanager.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.R
import com.example.fodmanager.data.repository.AuthRepository
import com.example.fodmanager.data.repository.UsuarioRepository
import com.example.fodmanager.ui.home.HomeActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

// Activity principal de acceso a la aplicación
class MainActivity : AppCompatActivity() {

    // Campo de texto para introducir el email
    private lateinit var etEmail: TextInputEditText

    // Campo de texto para introducir la contraseña
    private lateinit var etPassword: TextInputEditText

    // Botón para iniciar sesión
    private lateinit var btnLogin: Button

    // Barra de progreso mostrada durante el proceso de login
    private lateinit var progressBar: ProgressBar

    // Método que se ejecuta al crear la activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si ya hay sesión, no tiene sentido enseñar login otra vez
        if (AuthRepository.haySesionActiva()) {
            // Abre directamente la pantalla principal
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        // Asigna el layout de la pantalla de login
        setContentView(R.layout.activity_main)

        // Vincula las vistas con sus elementos del layout
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)

        // Acción al pulsar el botón de login
        btnLogin.setOnClickListener {
            // Obtiene y limpia los valores introducidos
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Comprueba que ambos campos estén rellenos
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Llama a la función que realiza el inicio de sesión
            login(email, password)
        }
    }

    // Función que gestiona el proceso de inicio de sesión
    private fun login(email: String, password: String) {
        // Muestra la barra de progreso
        progressBar.visibility = View.VISIBLE

        // Desactiva el botón para evitar múltiples pulsaciones
        btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                // Login real contra Supabase Auth
                AuthRepository.login(email, password)

                // Validamos que el perfil exista y esté activo
                UsuarioRepository.validarAccesoPostLogin()

                // Muestra mensaje de éxito
                Toast.makeText(this@MainActivity, "Login correcto", Toast.LENGTH_SHORT).show()

                // Abre la pantalla principal de la app
                startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                finish()

            } catch (e: Exception) {
                // Muestra mensaje de error si falla el login
                Toast.makeText(
                    this@MainActivity,
                    "No se pudo iniciar sesión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                // Oculta la barra de progreso al finalizar
                progressBar.visibility = View.GONE

                // Vuelve a activar el botón de login
                btnLogin.isEnabled = true
            }
        }
    }
}