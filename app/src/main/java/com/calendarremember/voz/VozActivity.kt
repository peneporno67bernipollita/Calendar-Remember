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
import android.os.SystemClock
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
import com.calendarremember.avisos.Notificaciones
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import com.calendarremember.ui.EstadoDictado
import com.calendarremember.ui.PantallaDictado
import com.calendarremember.ui.TemaNebula
import java.util.Locale

/**
 * El dictado con pantalla: el círculo que escucha, entiende y hace.
 *
 * La abren el botón de la app, el widget, el de los ajustes rápidos, el
 * acceso directo y el servicio que escucha "Nébula". Usa el reconocedor de
 * Google (el mismo del teclado) dentro de una pantalla propia, que es lo que
 * le permite salir sobre la pantalla de bloqueo: la de Google no puede.
 *
 * Lo que se pide lo resuelve el Ejecutor, el mismo que usa el servicio
 * cuando atiende sin pantalla: las dos vías entienden y hacen lo mismo.
 */
class VozActivity : ComponentActivity() {

    companion object {
        /** La abrió la palabra clave: se responde también en voz alta. */
        const val DESDE_PALABRA = "desde_palabra"

        /**
         * Cuándo se vio por última vez en pantalla. El servicio lo mira para
         * saber si llegó a abrirse de verdad: MIUI a veces la bloquea sin
         * avisar (o la deja detrás del bloqueo), y entonces el servicio
         * atiende por su cuenta. Por eso se marca al quedar a la vista y no
         * al crearse.
         */
        @Volatile
        var abiertaEn = 0L
            private set
    }

    private val ES = Locale("es", "ES")
    private val principal = Handler(Looper.getMainLooper())

    private var estado by mutableStateOf<EstadoDictado>(EstadoDictado.Escuchando("", 0f))
    private var reconocedor: SpeechRecognizer? = null
    private var dictado = ""
    /** La orden a la espera de que se elija entre varios eventos. */
    private var pendiente: Interpretacion? = null
    /**
     * La pregunta que Nébula acaba de hacer ("¿Qué apunto para mañana?",
     * "¿Cuál borro?"): lo próximo que se oiga es su respuesta.
     */
    private var esperando: Respuesta? = null
    /** Preguntas seguidas: a la tercera sin aclararse, se deja. */
    private var rondas = 0
    /**
     * El reconocedor está escuchando. Lo que avise cuando ya no (un error
     * tardío tras tocar una opción en pantalla) no cuenta.
     */
    private var oyendo = false

    private var voz: TextToSpeech? = null
    private var vozLista = false
    /** Lo que espera a que la voz esté lista para hablar. */
    private var alEstarLaVozLista: (() -> Unit)? = null
    private var desdePalabra = false
    /** Empieza a escuchar la primera vez que queda a la vista, no antes. */
    private var arrancada = false

    private val agenda by lazy { Almacen.comoAgenda(this) }

    private val pedirMicro = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) saludarYEscuchar() else terminar("Necesito permiso para usar el micrófono", false, false)
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
        desdePalabra = intent.getBooleanExtra(DESDE_PALABRA, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            // Al llamarla con el móvil bloqueado y la pantalla apagada, hay
            // que encenderla: si no, el círculo escucharía a oscuras.
            if (desdePalabra) setTurnScreenOn(true)
        }
        // Un texto compartido desde otra app (un mensaje de WhatsApp): no hay
        // nada que escuchar, se abre para apuntarlo.
        if (intent.action == Intent.ACTION_SEND) {
            val texto = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!texto.isNullOrEmpty()) abrirBorrador(texto)
            finish()
            return
        }

        // Si el servicio ya está atendiendo por su cuenta (esta pantalla llegó
        // tarde), no se estorba: los dos se pelearían por el micrófono.
        if (EscuchaServicio.dictandoAhora) {
            finish()
            return
        }

        // El aviso que la ha abierto sobre el bloqueo ya no hace falta. (Va
        // después de lo anterior: si atiende el servicio, ese aviso es suyo.)
        Notificaciones.quitarDictado(this)
        Almacen.cargar(this)

        // Mientras se dicta, el micrófono es para el dictado.
        EscuchaServicio.pausar(this)

        voz = TextToSpeech(this) { resultado ->
            vozLista = resultado == TextToSpeech.SUCCESS
            if (vozLista) voz?.language = ES
            principal.post {
                alEstarLaVozLista?.invoke()
                alEstarLaVozLista = null
            }
        }

        setContent {
            TemaNebula {
                PantallaDictado(
                    estado = estado,
                    alCerrar = { finish() },
                    alElegir = { evento -> elegir(evento) },
                    alApuntar = {
                        dejarDeEsperar()
                        mostrar(Ejecutor.ejecutar(Interprete.interpretar(dictado, soloCrear = true), agenda), false)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (arrancada) return
        arrancada = true
        abiertaEn = SystemClock.elapsedRealtime()
        if (EscuchaServicio.dictandoAhora) {
            finish()
            return
        }
        when {
            !SpeechRecognizer.isRecognitionAvailable(this) -> dictadoConElSistema()
            tienePermisoMicro() -> saludarYEscuchar()
            else -> pedirMicro.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Lo compartido se abre en el editor, ya relleno, para revisarlo y guardarlo. */
    private fun abrirBorrador(texto: String) {
        val borrador = Planes.borrador(texto)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.BORRADOR, borrador.aJson().toString())
            }
        )
    }

    private fun tienePermisoMicro() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    // --- Escuchar ---------------------------------------------------------

    /**
     * "Te escucho", en voz alta, y a escuchar: así se sabe cuándo se puede
     * empezar a hablar, sin tener que calcularlo. Si la voz tarda en
     * arrancar, se escucha igual sin decir nada.
     */
    private fun saludarYEscuchar() {
        var hecho = false
        val empezar = {
            if (!hecho) {
                hecho = true
                decir("Te escucho") { escuchar() }
            }
        }
        if (vozLista) {
            empezar()
        } else {
            alEstarLaVozLista = empezar
            principal.postDelayed({
                if (!hecho) {
                    hecho = true
                    alEstarLaVozLista = null
                    escuchar()
                }
            }, 2_000)
        }
    }

    private fun escuchar() {
        runCatching { reconocedor?.destroy() }
        oyendo = true
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
            if (texto.isBlank()) return
            estado = when (val actual = estado) {
                is EstadoDictado.Escuchando -> actual.copy(parcial = texto)
                is EstadoDictado.Elegir -> actual.copy(oyendo = texto)
                is EstadoDictado.NoEncontrado -> actual.copy(oyendo = texto)
                else -> actual
            }
        }

        override fun onResults(results: Bundle?) {
            if (!oyendo) return
            oyendo = false
            val texto = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim()
            when {
                !texto.isNullOrEmpty() -> atender(texto)
                esperando != null -> sinRespuesta()
                else -> terminar("No te he oído", false, false)
            }
        }

        override fun onError(error: Int) {
            if (!oyendo) return
            oyendo = false
            // Esperando una respuesta, callarse también es contestar.
            if (esperando != null && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                sinRespuesta()
                return
            }
            terminar(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te he oído"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Necesito permiso para el micrófono"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para entenderte"
                    else -> "No he podido escucharte"
                },
                false, false,
            )
        }
    }

    private fun dictadoConElSistema() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime qué apunto, cambio o cancelo")
        }
        runCatching { dictadoSistema.launch(intent) }
            .onFailure { terminar("Este móvil no tiene reconocimiento de voz", false, false) }
    }

    // --- Hacer lo que se ha pedido -----------------------------------------

    private fun atender(texto: String) {
        // La respuesta a lo que Nébula acaba de preguntar.
        esperando?.let { pregunta ->
            esperando = null
            mostrar(Ejecutor.responder(pregunta, texto, agenda), true)
            return
        }
        dictado = texto
        val leido = Interprete.interpretar(texto)
        // Una pregunta se contesta siempre en voz alta: es a lo que se pregunta.
        mostrar(Ejecutor.ejecutar(leido, agenda), leido.accion == Accion.CONSULTAR)
    }

    /** No se contestó: el Ejecutor decide qué hacer con el silencio. */
    private fun sinRespuesta() {
        val pregunta = esperando ?: return
        esperando = null
        mostrar(Ejecutor.responder(pregunta, "", agenda), true)
    }

    private fun dejarDeEsperar() {
        esperando = null
        oyendo = false
        runCatching { reconocedor?.cancel() }
        voz?.stop()
    }

    private fun elegir(evento: Evento) {
        val orden = pendiente ?: return finish()
        pendiente = null
        dejarDeEsperar()
        mostrar(Ejecutor.aplicar(orden, evento, agenda), false)
    }

    /**
     * Dice la pregunta en voz alta y escucha la respuesta, como en una
     * conversación. A la tercera pregunta seguida se deja estar.
     */
    private fun preguntar(pregunta: Respuesta, enVoz: String) {
        rondas++
        if (rondas > 3) {
            mostrar(Ejecutor.responder(pregunta, "", agenda), true)
            return
        }
        esperando = pregunta
        decir(enVoz) { if (esperando === pregunta) escuchar() }
    }

    private fun mostrar(respuesta: Respuesta, esPregunta: Boolean) {
        when (respuesta) {
            is Respuesta.Hecha -> {
                val revisar = respuesta.revisar
                // Si no se entendió de qué va, se abre para corregirlo; pero con
                // el móvil bloqueado eso pediría la contraseña, así que ahí solo
                // se avisa.
                if (revisar != null && !bloqueado()) {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("evento", revisar.id)
                        }
                    )
                    finish()
                    return
                }
                terminar(respuesta.mensaje, respuesta.bien, esPregunta)
            }
            is Respuesta.Elegir -> {
                pendiente = respuesta.leido
                val boton = when (respuesta.leido.accion) {
                    Accion.MOVER, Accion.EDITAR -> "Cambiar"
                    else -> "Borrar"
                }
                estado = EstadoDictado.Elegir(respuesta.mensaje, respuesta.candidatos, boton)
                // En voz, las opciones: "¿Cuál borro? La cena con Marta, el
                // sábado, o la cena con Luis, el viernes 25."
                val ahora = java.time.LocalDateTime.now()
                val opciones = respuesta.candidatos.take(3).joinToString(", o ") {
                    "${it.titulo}, ${Ejecutor.cuando(it, ahora)}"
                }
                preguntar(respuesta, "${respuesta.mensaje} $opciones.")
            }
            is Respuesta.NoEncontrada -> {
                estado = EstadoDictado.NoEncontrado(respuesta.mensaje, dictado)
                preguntar(respuesta, "${respuesta.mensaje} ¿Lo apunto?")
            }
            is Respuesta.Preguntar -> {
                estado = EstadoDictado.Escuchando("", 0f, respuesta.mensaje)
                preguntar(respuesta, respuesta.mensaje)
            }
        }
    }

    // --- Terminar ---------------------------------------------------------

    /** Lo enseña, lo dice si toca, y se cierra. */
    private fun terminar(mensaje: String, bien: Boolean, esPregunta: Boolean) {
        estado = EstadoDictado.Hecho(mensaje, bien)
        if ((desdePalabra || esPregunta) && vozLista) {
            decir(mensaje) { finish() }
            // Por si la voz se queda colgada: la pantalla no se queda abierta.
            principal.postDelayed({ finish() }, 15_000)
        } else {
            // El tiempo justo para leerlo: más cuanto más largo.
            principal.postDelayed({ finish() }, maxOf(1_800L, mensaje.length * 55L))
        }
    }

    private fun decir(texto: String, alAcabar: () -> Unit) {
        val motor = voz ?: return alAcabar()
        if (!vozLista) return alAcabar()
        // Una sola vez, pase lo que pase: si la voz se cuelga, se sigue igual
        // pasado lo que tardaría en decirlo.
        var hecho = false
        val acabar = {
            if (!hecho) {
                hecho = true
                alAcabar()
            }
        }
        motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { principal.post { acabar() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { principal.post { acabar() } }
        })
        motor.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "respuesta")
        principal.postDelayed({ acabar() }, maxOf(3_000L, texto.length * 110L))
    }

    private fun bloqueado(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    override fun onDestroy() {
        principal.removeCallbacksAndMessages(null)
        runCatching { reconocedor?.destroy() }
        runCatching { voz?.shutdown() }
        if (intent?.action != Intent.ACTION_SEND) EscuchaServicio.reanudar(this)
        super.onDestroy()
    }
}
