package com.example.fodmanager.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fodmanager.ui.home.HomeActivity
import com.example.fodmanager.R
import com.example.fodmanager.data.local.SessionManager
import com.example.fodmanager.data.remote.supabase
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

// Activity de login, es la primera pantalla que ve el usuario al abrir la app.
// Gestiona la autenticación contra Supabase Auth usando email y contraseña.
// Una vez autenticado navega a HomeActivity y se destruye a sí misma con finish()
// para que el usuario no pueda volver a la pantalla de login pulsando atrás.
class MainActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicialización de los elementos visuales del layout
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)

        // Listener del botón de login que valida los campos antes de intentar autenticar
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validación básica: ambos campos son obligatorios
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            login(email, password)
        }
    }

    // Realiza el proceso de autenticación contra Supabase Auth
    private fun login(email: String, password: String) {
        // Muestra el ProgressBar y deshabilita el botón para evitar
        // pulsaciones múltiples mientras se procesa la autenticación
        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false

        // lifecycleScope.launch ejecuta el código en una corrutina
        // para no bloquear el hilo principal de la UI durante la petición de red
        lifecycleScope.launch {
            try {
                // Autentica al usuario en Supabase Auth usando el proveedor de Email
                // Si las credenciales son incorrectas lanza una excepción
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Guarda las credenciales en SessionManager para poder restaurar
                // la sesión del usuario actual si crea un nuevo usuario desde la app,
                // ya que signUpWith cierra la sesión actual automáticamente
                SessionManager.emailActual = email
                SessionManager.passwordActual = password

                Toast.makeText(this@MainActivity, "Login correcto", Toast.LENGTH_SHORT).show()

                // Navega a HomeActivity y llama a finish() para destruir MainActivity,
                // impidiendo que el usuario vuelva al login pulsando el botón atrás
                val intent = Intent(this@MainActivity, HomeActivity::class.java)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                // Si las credenciales son incorrectas o hay un error de red
                // muestra el mensaje de error al usuario
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // finally se ejecuta siempre, tanto si hay éxito como si hay error.
                // Oculta el ProgressBar y reactiva el botón en cualquier caso
                progressBar.visibility = View.GONE
                btnLogin.isEnabled = true
            }
        }
    }
}