package com.calendarremember.voz

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.calendarremember.MainActivity
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dictar un evento de un toque.
 *
 * Es la actividad que abren el widget y el acceso directo, y la que arranca
 * cuando le pides a Google que abra la app: no enseña el calendario, solo
 * escucha, apunta y se quita de en medio.
 *
 * Usa el reconocedor del sistema (el mismo que el teclado), así que no hace
 * falta ni conexión propia ni claves de nadie.
 */
class VozActivity : ComponentActivity() {

    private val ES = Locale("es", "ES")

    private val dictado = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode != Activity.RESULT_OK) {
            finish()
            return@registerForActivityResult
        }
        val texto = resultado.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()

        if (texto.isNullOrEmpty()) {
            avisar("No te he oído")
            finish()
            return@registerForActivityResult
        }
        apuntar(texto)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Almacen.cargar(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime qué apunto")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        runCatching { dictado.launch(intent) }.onFailure {
            avisar("Este móvil no tiene reconocimiento de voz")
            finish()
        }
    }

    private fun apuntar(texto: String) {
        val leido = Interprete.interpretar(texto)

        val evento = Evento(
            titulo = leido.titulo,
            inicio = leido.inicio,
            todoElDia = leido.todoElDia,
            avisos = leido.avisos,
            duracionMin = leido.duracionMin,
            dictado = leido.dictado,
        )
        Almacen.guardar(this, evento)

        // Cuando no se ha entendido bien, la app se abre por el evento para
        // corregirlo. Guardar algo torcido en silencio es peor que preguntar.
        if (leido.confianza == Confianza.BAJA) {
            avisar("No lo he entendido bien, revísalo")
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("evento", evento.id)
                }
            )
        } else {
            avisar(confirmacion(evento))
        }
        finish()
    }

    /** Lo que se le dice al usuario: la fecha entera, para que note un error. */
    private fun confirmacion(evento: Evento): String {
        val dia = evento.inicio.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES))
        return if (evento.todoElDia) {
            "Apuntado: ${evento.titulo}, el $dia"
        } else {
            val hora = evento.inicio.format(DateTimeFormatter.ofPattern("HH:mm"))
            "Apuntado: ${evento.titulo}, el $dia a las $hora"
        }
    }

    private fun avisar(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
