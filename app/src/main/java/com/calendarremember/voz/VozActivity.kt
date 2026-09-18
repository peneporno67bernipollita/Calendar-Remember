package com.calendarremember.voz

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.calendarremember.MainActivity
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import com.calendarremember.ui.EstadoDictado
import com.calendarremember.ui.PantallaDictado
import com.calendarremember.ui.TemaNebula
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * El dictado: escucha, entiende y hace lo que se le pide.
 *
 * La abren el botón de la app, el widget, el de los ajustes rápidos, el
 * acceso directo y el servicio que escucha "Nébula". Usa el reconocedor de
 * Google (el mismo del teclado) pero dentro de una pantalla propia, que es
 * lo que le permite salir sobre la pantalla de bloqueo: la de Google no
 * puede, y obligaba a desbloquear.
 *
 * No pide confirmación: si se entiende qué evento es, se apunta o se borra y
 * lo dice. Solo pregunta cuando no hay manera de saberlo, porque dos eventos
 * encajan por igual.
 */
class VozActivity : ComponentActivity() {

    companion object {
        /** La abrió la palabra clave: se responde también en voz alta. */
        const val DESDE_PALABRA = "desde_palabra"
    }

    private val ES = Locale("es", "ES")
    private val principal = Handler(Looper.getMainLooper())

    private var estado by mutableStateOf<EstadoDictado>(EstadoDictado.Escuchando("", 0f))
    private var reconocedor: SpeechRecognizer? = null
    private var dictadoOriginal = ""

    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var responderEnVoz = false

    private val pedirMicro = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) escuchar() else terminar("Necesito permiso para usar el micrófono", false)
    }

    /** Para móviles sin el servicio de reconocimiento: el diálogo del sistema. */
    private val dictadoSistema = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        val texto = resultado.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.trim()
        if (resultado.resultCode == Activity.RESULT_OK && !texto.isNullOrEmpty()) atender(texto)
        else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)

        Almacen.cargar(this)
        // Mientras se dicta, el micrófono es para el dictado.
        EscuchaServicio.pausar(this)

        responderEnVoz = intent.getBooleanExtra(DESDE_PALABRA, false)
        if (responderEnVoz) {
            tts = TextToSpeech(this) { resultado ->
                ttsListo = resultado == TextToSpeech.SUCCESS
                if (ttsListo) tts?.language = ES
            }
        }

        setContent {
            TemaNebula {
                PantallaDictado(
                    estado = estado,
                    alCerrar = { finish() },
                    alElegir = { borrar(it) },
                    alApuntar = { apuntar(Interprete.interpretar(dictadoOriginal, soloCrear = true)) },
                )
            }
        }

        when {
            !SpeechRecognizer.isRecognitionAvailable(this) -> dictadoConElSistema()
            tienePermisoMicro() -> principal.postDelayed({ escuchar() }, 250)
            else -> pedirMicro.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun tienePermisoMicro() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    // --- Escuchar ---------------------------------------------------------

    private fun escuchar() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        reconocedor = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(oyente)
            startListening(intent)
        }
    }

    private val oyente = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            val actual = estado as? EstadoDictado.Escuchando ?: return
            // El volumen llega en decibelios, entre -2 y 10 más o menos.
            estado = actual.copy(nivel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texto = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            val actual = estado as? EstadoDictado.Escuchando ?: return
            if (texto.isNotBlank()) estado = actual.copy(parcial = texto)
        }

        override fun onResults(results: Bundle?) {
            val texto = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim()
            if (texto.isNullOrEmpty()) terminar("No te he oído", false) else atender(texto)
        }

        override fun onError(error: Int) {
            terminar(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te he oído"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Necesito permiso para el micrófono"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para entenderte"
                    else -> "No he podido escucharte"
                },
                false,
            )
        }
    }

    private fun dictadoConElSistema() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime qué apunto o qué cancelo")
        }
        runCatching { dictadoSistema.launch(intent) }
            .onFailure { terminar("Este móvil no tiene reconocimiento de voz", false) }
    }

    // --- Hacer lo que se ha pedido -----------------------------------------

    private fun atender(texto: String) {
        dictadoOriginal = texto
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

        // Si no se entendió de qué va, se abre para corregirlo; pero con el
        // móvil bloqueado eso pediría la contraseña, así que ahí solo se avisa.
        if (leido.confianza == Confianza.BAJA && !bloqueado()) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("evento", evento.id)
                }
            )
            finish()
            return
        }
        terminar("Apuntado: ${evento.titulo}, ${cuando(evento)}", true)
    }

    /**
     * Cancelar borra directamente el evento del que se habla. Solo si hay
     * varios igual de parecidos pregunta cuál, porque ahí no hay forma de
     * saberlo; y si no encaja ninguno, ofrece apuntar la frase por si era eso.
     */
    private fun cancelar(leido: Interpretacion) {
        if (leido.titulo.isBlank() && !leido.fechaDicha && !leido.horaDicha) {
            terminar("Dime qué cancelo", false)
            return
        }
        val encontrados = Buscador.candidatos(
            criterio = leido.titulo,
            fecha = if (leido.fechaDicha) leido.inicio.toLocalDate() else null,
            eventos = Almacen.eventos.value,
            ahora = LocalDateTime.now(),
            hora = if (leido.horaDicha) leido.inicio.toLocalTime() else null,
        )
        if (encontrados.isEmpty()) {
            estado = EstadoDictado.NoEncontrado(leido.titulo, leido.dictado)
            return
        }
        val unico = Buscador.unico(encontrados)
        if (unico != null) borrar(unico)
        else estado = EstadoDictado.Elegir(encontrados.take(4).map { it.evento })
    }

    private fun borrar(evento: Evento) {
        Almacen.borrar(this, evento.id)
        terminar("Borrado: ${evento.titulo}, ${cuando(evento)}", true)
    }

    // --- Terminar ---------------------------------------------------------

    /** Lo dice en pantalla (y en voz, si vino de "Nébula") y se cierra. */
    private fun terminar(mensaje: String, bien: Boolean) {
        estado = EstadoDictado.Hecho(mensaje, bien)
        val motor = tts
        if (responderEnVoz && motor != null && ttsListo) {
            motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { principal.post { finish() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { principal.post { finish() } }
            })
            motor.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "respuesta")
            // Por si la voz se queda colgada: la pantalla no se queda abierta.
            principal.postDelayed({ finish() }, 6_000)
        } else {
            principal.postDelayed({ finish() }, 1_800)
        }
    }

    /** "el sábado 19", "hoy a las 17:30". */
    private fun cuando(evento: Evento): String {
        val hoy = java.time.LocalDate.now()
        val dia = evento.inicio.toLocalDate()
        val nombreDia = when (dia) {
            hoy -> "hoy"
            hoy.plusDays(1) -> "mañana"
            else -> "el " + dia.format(DateTimeFormatter.ofPattern("EEEE d", ES))
        }
        return if (evento.todoElDia) nombreDia
        else "$nombreDia a las ${evento.inicio.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    private fun bloqueado(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    override fun onDestroy() {
        principal.removeCallbacksAndMessages(null)
        runCatching { reconocedor?.destroy() }
        runCatching { tts?.shutdown() }
        EscuchaServicio.reanudar(this)
        super.onDestroy()
    }
}
