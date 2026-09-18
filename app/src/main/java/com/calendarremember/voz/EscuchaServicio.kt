package com.calendarremember.voz

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.calendarremember.avisos.Notificaciones
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Preferencias
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Escucha "Nébula" y, al oírla, atiende lo que se diga a continuación.
 *
 * Solo tiene el micrófono abierto con la pantalla encendida: así cumple lo
 * que se le pide (encender el móvil y hablarle) sin estar oyendo toda la
 * noche en el bolsillo.
 *
 * Cómo distingue "Nébula" de todo lo demás está medido, no supuesto. El
 * modelo no conoce la palabra, pero se le da a elegir entre "nebulosa" (que
 * es como la oye) y 121 palabras corrientes: así "me mola" o "me gusta" se
 * quedan en lo que son en vez de colarse como "nebulosa". Además tiene que
 * mantenerse dos trozos seguidos de un cuarto de segundo, porque lo que el
 * motor dice de pasada cambia enseguida. Con audio de prueba: 12 de 12
 * "Nébula" detectadas en conversación y ninguna falsa en 140 segundos de
 * charla llena de palabras parecidas (antes eran 9 falsas en 43 segundos).
 */
class EscuchaServicio : Service() {

    companion object {
        private const val PALABRA = "nebulosa"
        private const val ACCION_PAUSAR = "com.calendarremember.escucha.PAUSAR"
        private const val ACCION_REANUDAR = "com.calendarremember.escucha.REANUDAR"
        private const val ACCION_PARAR = "com.calendarremember.escucha.PARAR"

        private const val FRECUENCIA = 16_000
        /** Un cuarto de segundo, como en las mediciones. */
        private const val MUESTRAS_TROZO = 4_000
        /** Trozos seguidos en que tiene que oírse la palabra para darla por buena. */
        private const val TROZOS_SEGUIDOS = 2

        /** Entre dos activaciones: evita que un "Nébula" dispare dos veces. */
        private const val PAUSA_ENTRE_ACTIVACIONES_MS = 3_000L
        /** Cuánto se espera a que se abra la pantalla del dictado. */
        private const val ESPERA_PANTALLA_MS = 1_500L
        /**
         * Si el dictado no llega a devolver el micrófono (se cierra de golpe,
         * el sistema lo mata...), la escucha vuelve sola pasado este tiempo.
         */
        private const val REANUDAR_SIEMPRE_TRAS_MS = 45_000L

        /**
         * Lo que compite con "nebulosa" en el reconocedor. Son palabras
         * corrientes a propósito: probadas contra listas con las confusiones
         * concretas ("me mola", "médula"...), estas daban la misma precisión
         * y detectaban mucho mejor con ruido.
         */
        private val COMPETIDORAS = ("de la que el en y a los se del las un por con no una su para es " +
            "al lo como mas o pero sus le ha me si sin sobre este ya entre cuando todo esta ser son " +
            "dos tambien fue muy hasta desde mi porque que solo han yo hay vez puede todos asi nos ni " +
            "tiene uno donde bien tiempo mismo ese ahora cada otro despues te aunque esa eso hace otra " +
            "tan siempre dia tanto ella tres si menos antes casa hoy manana tarde noche vale venga bueno " +
            "buena nada mucho tio mira pues hola gracias luego llamo voy vamos cama playa luna madre mia " +
            "hora lunes martes cena comer apunta cancela borra oye eh").split(" ")

        private val GRAMATICA: String =
            JSONArray(listOf(PALABRA) + COMPETIDORAS + listOf("[unk]")).toString()

        @Volatile
        var enMarcha = false
            private set

        /**
         * El servicio está atendiendo una orden por su cuenta. Si la pantalla
         * del dictado llega a abrirse tarde, se cierra sola: dos a la vez se
         * pelearían por el micrófono.
         */
        @Volatile
        var dictandoAhora = false
            private set

        fun arrancar(contexto: Context) {
            ContextCompat.startForegroundService(
                contexto, Intent(contexto, EscuchaServicio::class.java)
            )
        }

        fun parar(contexto: Context) {
            if (enMarcha) enviar(contexto, ACCION_PARAR)
        }

        /** El dictado pide el micrófono para él. */
        fun pausar(contexto: Context) {
            if (enMarcha) enviar(contexto, ACCION_PAUSAR)
        }

        fun reanudar(contexto: Context) {
            if (enMarcha) enviar(contexto, ACCION_REANUDAR)
        }

        private fun enviar(contexto: Context, accion: String) {
            runCatching {
                contexto.startService(
                    Intent(contexto, EscuchaServicio::class.java).setAction(accion)
                )
            }
        }
    }

    private val principal = Handler(Looper.getMainLooper())
    private val cargador = Executors.newSingleThreadExecutor()

    @Volatile private var modelo: Model? = null
    /** El hilo que lee el micrófono buscando la palabra. */
    private var captura: Thread? = null
    @Volatile private var capturando = false

    private var pausado = false
    /** Atendiendo una orden desde aquí mismo, sin pantalla. */
    private var dictando = false
    private var pantallaEncendida = true
    private var ultimaActivacion = 0L

    private var voz: TextToSpeech? = null
    private var vozLista = false

    private val reanudarSiempre = Runnable {
        if (pausado && !dictando) {
            pausado = false
            actualizar()
        }
    }

    private val receptorPantalla = object : BroadcastReceiver() {
        override fun onReceive(contexto: Context, intent: Intent) {
            pantallaEncendida = intent.action == Intent.ACTION_SCREEN_ON
            actualizar()
        }
    }

    override fun onCreate() {
        super.onCreate()
        enMarcha = true
        Almacen.cargar(this)
        Notificaciones.crearCanales(this)

        // La notificación obligatoria de un servicio que usa el micrófono es la
        // misma agenda de la pantalla de bloqueo: así no hay dos fijas.
        ServiceCompat.startForeground(
            this,
            Notificaciones.ID_AGENDA,
            Notificaciones.construirAgenda(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )

        ContextCompat.registerReceiver(
            this, receptorPantalla,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        pantallaEncendida = getSystemService(PowerManager::class.java)?.isInteractive ?: true

        voz = TextToSpeech(this) { resultado ->
            vozLista = resultado == TextToSpeech.SUCCESS
            if (vozLista) voz?.language = Locale("es", "ES")
        }

        // Cargar el modelo lleva unos segundos (y la primera vez, copiarlo):
        // fuera del hilo principal.
        cargador.execute {
            try {
                val cargado = Model(ModeloVoz.preparar(this).absolutePath)
                principal.post {
                    modelo = cargado
                    actualizar()
                }
            } catch (e: Exception) {
                principal.post { stopSelf() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_PAUSAR -> {
                pausado = true
                actualizar()
                principal.removeCallbacks(reanudarSiempre)
                principal.postDelayed(reanudarSiempre, REANUDAR_SIEMPRE_TRAS_MS)
            }
            ACCION_REANUDAR -> {
                principal.removeCallbacks(reanudarSiempre)
                if (!dictando) {
                    pausado = false
                    actualizar()
                }
            }
            ACCION_PARAR -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // Si el sistema lo mata por falta de memoria, que lo vuelva a levantar.
        return START_STICKY
    }

    /** Abre o suelta el micrófono según toque. */
    private fun actualizar() {
        val debe = modelo != null && pantallaEncendida && !pausado && !dictando
        val vivo = captura?.isAlive == true
        if (debe && !vivo) empezar()
        if (!debe && vivo) soltar()
    }

    private fun empezar() {
        val m = modelo ?: return
        capturando = true
        captura = Thread({ buscarLaPalabra(m) }, "escucha-nebula").also { it.start() }
    }

    private fun soltar() {
        capturando = false
        captura?.let { hilo -> if (hilo !== Thread.currentThread()) runCatching { hilo.join(1_000) } }
        captura = null
    }

    @SuppressLint("MissingPermission")
    private fun abrirMicro(): AudioRecord? {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return null
        val minimo = AudioRecord.getMinBufferSize(
            FRECUENCIA, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimo <= 0) return null
        return runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, FRECUENCIA,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimo, MUESTRAS_TROZO * 2 * 2),
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.getOrNull()
    }

    // --- Oír la palabra (en su propio hilo) --------------------------------

    private fun buscarLaPalabra(m: Model) {
        val micro = abrirMicro()
        if (micro == null) {
            // Otro programa tiene el micrófono o falta el permiso: se vuelve a
            // intentar en un momento en vez de rendirse.
            capturando = false
            principal.postDelayed({ actualizar() }, 2_000)
            return
        }
        // Todo dentro del try: un error sin recoger en un hilo propio cierra la
        // app entera, no solo la escucha.
        var reconocedor: Recognizer? = null
        val trozo = ShortArray(MUESTRAS_TROZO)
        var seguidos = 0
        var fallo = false
        try {
            reconocedor = Recognizer(m, FRECUENCIA.toFloat(), GRAMATICA)
            micro.startRecording()
            while (capturando) {
                val leidas = micro.read(trozo, 0, trozo.size)
                if (leidas <= 0) { fallo = true; break }
                if (reconocedor.acceptWaveForm(trozo, leidas)) {
                    seguidos = 0
                    continue
                }
                val parcial = JSONObject(reconocedor.partialResult).optString("partial")
                seguidos = if (parcial.split(' ').contains(PALABRA)) seguidos + 1 else 0
                if (seguidos >= TROZOS_SEGUIDOS) {
                    seguidos = 0
                    reconocedor.reset()
                    val ahora = SystemClock.elapsedRealtime()
                    if (ahora - ultimaActivacion >= PAUSA_ENTRE_ACTIVACIONES_MS) {
                        ultimaActivacion = ahora
                        capturando = false
                        principal.post { alOirLaPalabra() }
                    }
                }
            }
        } catch (e: Throwable) {
            fallo = true
        } finally {
            runCatching { micro.stop() }
            runCatching { micro.release() }
            runCatching { reconocedor?.close() }
        }
        if (fallo) {
            capturando = false
            principal.postDelayed({ actualizar() }, 2_000)
        }
    }

    // --- Al oírla ----------------------------------------------------------

    /**
     * Se intenta abrir la pantalla del dictado, con su círculo. Si en un
     * segundo y medio no se ha abierto —falta el permiso de mostrarse sobre
     * otras apps, o MIUI la bloquea sin decir nada—, se atiende desde aquí:
     * pitido, se escucha y se contesta en voz alta. Nunca hace falta tocar
     * nada para que escuche.
     */
    private fun alOirLaPalabra() {
        pausado = true
        principal.removeCallbacks(reanudarSiempre)
        principal.postDelayed(reanudarSiempre, REANUDAR_SIEMPRE_TRAS_MS)

        if (!Settings.canDrawOverlays(this)) {
            dictarAqui()
            return
        }
        val disparo = SystemClock.elapsedRealtime()
        runCatching {
            startActivity(
                Intent(this, VozActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(VozActivity.DESDE_PALABRA, true)
                }
            )
        }
        principal.postDelayed({
            val abrio = VozActivity.abiertaEn >= disparo
            Preferencias.ponerAperturaBloqueada(this, !abrio)
            if (!abrio) dictarAqui()
        }, ESPERA_PANTALLA_MS)
    }

    private fun dictarAqui() {
        val m = modelo ?: return terminarDictado()
        dictando = true
        dictandoAhora = true
        pitar()
        Notificaciones.mostrarDictado(this, "Te escucho…", null)
        Thread({
            // Lo justo para que el pitido no se lo coma la grabación.
            SystemClock.sleep(300)
            val texto = transcribir(m)
            principal.post { atender(texto) }
        }, "dictado-nebula").start()
    }

    /**
     * Transcribe lo que se diga a continuación con el reconocedor sin
     * conexión, ya en modo libre. Acaba al callarse, o a los 4,5 segundos si
     * no se ha dicho nada, o a los 10 como mucho.
     */
    private fun transcribir(m: Model): String {
        val micro = abrirMicro() ?: return ""
        var reconocedor: Recognizer? = null
        val trozo = ShortArray(MUESTRAS_TROZO)
        val inicio = SystemClock.elapsedRealtime()
        var texto = ""
        var hablado = false
        var ultimoParcial = ""
        try {
            reconocedor = Recognizer(m, FRECUENCIA.toFloat())
            micro.startRecording()
            while (SystemClock.elapsedRealtime() - inicio < 10_000) {
                val leidas = micro.read(trozo, 0, trozo.size)
                if (leidas <= 0) break
                if (reconocedor.acceptWaveForm(trozo, leidas)) {
                    val t = JSONObject(reconocedor.result).optString("text")
                    if (t.isNotBlank()) { texto = t; break }
                } else {
                    val parcial = JSONObject(reconocedor.partialResult).optString("partial")
                    if (parcial.isNotBlank()) {
                        hablado = true
                        if (parcial != ultimoParcial) {
                            ultimoParcial = parcial
                            principal.post { Notificaciones.mostrarDictado(this, "Te escucho…", parcial) }
                        }
                    }
                    if (!hablado && SystemClock.elapsedRealtime() - inicio > 4_500) break
                }
            }
            if (texto.isBlank()) texto = JSONObject(reconocedor.finalResult).optString("text")
        } catch (e: Throwable) {
            // Lo que se haya entendido hasta aquí, si algo.
        } finally {
            runCatching { micro.stop() }
            runCatching { micro.release() }
            runCatching { reconocedor?.close() }
        }
        return texto.trim()
    }

    private fun atender(texto: String) {
        val respuesta = if (texto.isBlank()) "No te he oído." else {
            val leido = Interprete.interpretar(texto)
            when (val r = Ejecutor.ejecutar(leido, Almacen.comoAgenda(this))) {
                is Respuesta.Hecha -> r.mensaje
                // Sin pantalla no se puede elegir de una lista: se dicen los
                // que compiten para que se repita con más detalle.
                is Respuesta.Elegir -> "Hay varios parecidos: " +
                    r.candidatos.take(3).joinToString(" y ") { it.titulo } + ". Dímelo con más detalle."
                is Respuesta.NoEncontrada -> r.mensaje
            }
        }
        Notificaciones.mostrarDictado(this, respuesta, texto.ifBlank { null })
        hablar(respuesta) { terminarDictado() }
    }

    private fun terminarDictado() {
        dictando = false
        dictandoAhora = false
        pausado = false
        principal.removeCallbacks(reanudarSiempre)
        actualizar()
    }

    private fun pitar() {
        runCatching {
            val tono = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tono.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            principal.postDelayed({ tono.release() }, 400)
        }
    }

    private fun hablar(texto: String, alAcabar: () -> Unit) {
        val motor = voz
        if (motor == null || !vozLista) {
            alAcabar()
            return
        }
        var hecho = false
        val acabar = { if (!hecho) { hecho = true; alAcabar() } }
        motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { principal.post { acabar() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { principal.post { acabar() } }
        })
        motor.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "respuesta")
        // Si la voz se queda colgada, la escucha no se queda parada con ella.
        principal.postDelayed({ acabar() }, 12_000)
    }

    override fun onDestroy() {
        enMarcha = false
        dictandoAhora = false
        principal.removeCallbacksAndMessages(null)
        soltar()
        runCatching { unregisterReceiver(receptorPantalla) }
        cargador.shutdown()
        runCatching { voz?.shutdown() }
        runCatching { modelo?.close() }
        modelo = null
        super.onDestroy()
        // La agenda sigue (o se quita, si estaba desactivada), ya sin la
        // marca de que Nébula escucha.
        Notificaciones.refrescarAgenda(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
