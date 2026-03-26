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

/**
 * Pantalla de login: es la primera activity que ve el usuario al abrir la aplicación.
 *
 * Responsabilidades:
 * - Recoger las credenciales (email y contraseña) del usuario.
 * - Autenticarlas contra Supabase Auth.
 * - Guardar las credenciales en SessionManager para restaurar la sesión
 *   en caso de que el usuario logueado cree un nuevo usuario (ver SessionManager).
 * - Navegar a[HomeActivity tras un login exitoso y destruirse con finish
 *   para que el usuario no pueda volver a esta pantalla con el botón atrás.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)

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

    /**
     * Autentica al usuario contra Supabase Auth usando el proveedor Email.
     *
     * Durante el proceso el botón queda deshabilitado y el ProgressBar visible
     * para evitar envíos duplicados. El bloque `finally` restaura ambos elementos
     * independientemente del resultado.
     *
     * Si las credenciales son correctas:
     * 1. Se guardan en SessionManager para posibles restauraciones de sesión.
     * 2. Se navega a HomeActivity y se destruye esta activity.
     *
     * Si las credenciales son incorrectas o hay un error de red, se muestra
     * el mensaje de error al usuario y se reactiva el formulario.
     */
    private fun login(email: String, password: String) {
        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Las credenciales se guardan por si se necesita restaurar la sesión
                // tras crear un nuevo usuario (signUpWith cierra la sesión actual)
                SessionManager.emailActual = email
                SessionManager.passwordActual = password

                Toast.makeText(this@MainActivity, "Login correcto", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // Se ejecuta siempre: restaura el estado de la UI
                progressBar.visibility = View.GONE
                btnLogin.isEnabled = true
            }
        }
    }
}