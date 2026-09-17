package com.calendarremember.voz

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.calendarremember.MainActivity
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import com.calendarremember.ui.DialogoCancelar
import com.calendarremember.ui.TemaNebula
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dictar de un toque.
 *
 * Es la actividad que abren el widget, el botón de los ajustes rápidos y el
 * acceso directo: no enseña el calendario, solo escucha, hace lo que se le
 * pide y se quita de en medio.
 *
 * Entiende dos cosas: apuntar algo nuevo y cancelar algo que ya existe.
 * Apuntar se resuelve solo; cancelar siempre pregunta antes.
 */
class VozActivity : ComponentActivity() {

    private val ES = Locale("es", "ES")

    private var candidatos by mutableStateOf<List<Evento>>(emptyList())

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
        atender(texto)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Almacen.cargar(this)

        setContent {
            TemaNebula {
                if (candidatos.isNotEmpty()) {
                    DialogoCancelar(
                        candidatos = candidatos,
                        alConfirmar = { evento ->
                            Almacen.borrar(this, evento.id)
                            avisar("Cancelado: ${evento.titulo}")
                            finish()
                        },
                        alCerrar = { finish() },
                    )
                }
            }
        }

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

    private fun atender(texto: String) {
        val leido = Interprete.interpretar(texto)
        when (leido.accion) {
            Accion.CREAR -> apuntar(leido)
            Accion.BORRAR -> cancelar(leido)
        }
    }

    private fun apuntar(leido: Interpretacion) {
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
            abrirApp(evento.id)
        } else {
            avisar(confirmacion(evento))
        }
        finish()
    }

    /**
     * Cancelar no borra nada por su cuenta: busca a qué se refería la frase y
     * lo enseña. Si hay varios parecidos, los muestra todos para elegir.
     */
    private fun cancelar(leido: Interpretacion) {
        if (leido.titulo.isBlank()) {
            avisar("Dime qué cancelo")
            finish()
            return
        }

        val encontrados = Buscador.candidatos(
            criterio = leido.titulo,
            fecha = if (leido.fechaDicha) leido.inicio.toLocalDate() else null,
            eventos = Almacen.eventos.value,
            ahora = LocalDateTime.now(),
        )

        if (encontrados.isEmpty()) {
            avisar("No he encontrado nada parecido a «${leido.titulo}»")
            finish()
            return
        }

        // Con un favorito claro se pregunta por ese; si no, se enseñan los
        // que compiten para que elija quien sabe cuál era.
        val unico = Buscador.unico(encontrados)
        candidatos = if (unico != null) listOf(unico) else encontrados.take(4).map { it.evento }
    }

    private fun abrirApp(eventoId: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("evento", eventoId)
            }
        )
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
