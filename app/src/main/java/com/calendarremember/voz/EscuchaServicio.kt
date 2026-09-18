package com.calendarremember.voz

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.app.KeyguardManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * Escucha con la pantalla encendida, en el escritorio y en la pantalla de
 * bloqueo; con la pantalla apagada, solo si se activa en los ajustes, porque
 * mantiene el procesador despierto. Apagada, además, el reconocedor no
 * recibe el silencio: solo el audio con algo de voz, que es lo que gasta.
 *
 * Cómo distingue "Nébula" de todo lo demás está medido, no supuesto. El
 * modelo no conoce la palabra, pero se le da a elegir entre "nebulosa" (que
 * es como la oye) y 120 palabras corrientes: así "me mola" o "me gusta" se
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
        private const val ACCION_AJUSTES = "com.calendarremember.escucha.AJUSTES"

        private const val FRECUENCIA = 16_000
        /** Un cuarto de segundo, como en las mediciones. */
        private const val MUESTRAS_TROZO = 4_000
        /** Trozos seguidos en que tiene que oírse la palabra para darla por buena. */
        private const val TROZOS_SEGUIDOS = 2

        /** Entre dos activaciones: evita que un "Nébula" dispare dos veces. */
        private const val PAUSA_ENTRE_ACTIVACIONES_MS = 3_000L
        /** Cuánto se espera a que se abra la pantalla del dictado. */
        private const val ESPERA_PANTALLA_MS = 1_500L
        /** Sobre el bloqueo hay que encender la pantalla antes: más margen. */
        private const val ESPERA_BLOQUEO_MS = 3_000L

        /**
         * La puerta de la pantalla apagada: por debajo de esto (o de 1,6
         * veces el ruido de fondo) un trozo es silencio. Con más de un
         * segundo seguido de silencio, el reconocedor deja de recibir audio
         * hasta que vuelva a haber voz. Medido sobre las mismas
         * conversaciones: detecta lo mismo, y el reconocedor se ahorra todo
         * el silencio.
         */
        private const val VOZ_MINIMA = 100.0
        private const val TROZOS_DE_SILENCIO = 4
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

        /** Han cambiado los ajustes (escuchar con la pantalla apagada). */
        fun releerAjustes(contexto: Context) {
            if (enMarcha) enviar(contexto, ACCION_AJUSTES)
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
    /** La lee también el hilo de la captura: decide si hay puerta de silencio. */
    @Volatile private var pantallaEncendida = true
    private var ultimaActivacion = 0L
    /** Con la pantalla apagada, el procesador no puede dormirse mientras escucha. */
    private var despierto: PowerManager.WakeLock? = null
    /**
     * Y tampoco mientras atiende lo que se dice tras la palabra: mirar el
     * sensor, abrir el círculo o escuchar y contestar. Con tope, por si algo
     * se queda a medias.
     */
    private var atendiendo: PowerManager.WakeLock? = null

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
            ACCION_AJUSTES -> actualizar()
        }
        // Si el sistema lo mata por falta de memoria, que lo vuelva a levantar.
        return START_STICKY
    }

    /** Abre o suelta el micrófono según toque. */
    private fun actualizar() {
        val oye = pantallaEncendida || Preferencias.escucharApagada(this)
        val debe = modelo != null && oye && !pausado && !dictando
        val vivo = captura?.isAlive == true
        if (debe && !vivo) empezar()
        if (!debe && vivo) soltar()
        mantenerDespierto(debe && !pantallaEncendida)
    }

    private fun mantenerDespierto(si: Boolean) {
        val candado = despierto ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nebula:escucha")
            ?.also {
                it.setReferenceCounted(false)
                despierto = it
            } ?: return
        if (si && !candado.isHeld) candado.acquire()
        if (!si && candado.isHeld) candado.release()
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

    /** El volumen de un trozo: la raíz de la media de los cuadrados. */
    private fun energia(trozo: ShortArray, n: Int): Double {
        var suma = 0.0
        for (i in 0 until n) {
            val v = trozo[i].toDouble()
            suma += v * v
        }
        return kotlin.math.sqrt(suma / maxOf(1, n))
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
        var trozo = ShortArray(MUESTRAS_TROZO)
        // El trozo anterior, para no perder el principio de la palabra al
        // abrir la puerta de silencio.
        var previo = ShortArray(MUESTRAS_TROZO)
        var previoLeidas = 0
        var seguidos = 0
        var fallo = false
        var ruido = 300.0
        var callado = 0
        var dormido = false
        try {
            reconocedor = Recognizer(m, FRECUENCIA.toFloat(), GRAMATICA)
            micro.startRecording()
            while (capturando) {
                val leidas = micro.read(trozo, 0, trozo.size)
                if (leidas <= 0) { fallo = true; break }

                // La puerta de silencio, solo con la pantalla apagada: con
                // ella encendida todo sigue exactamente como se midió.
                if (!pantallaEncendida) {
                    val volumen = energia(trozo, leidas)
                    ruido = if (volumen < ruido) 0.9 * ruido + 0.1 * volumen else 0.995 * ruido + 0.005 * volumen
                    callado = if (volumen > maxOf(ruido * 1.6, VOZ_MINIMA)) 0 else callado + 1
                    if (callado > TROZOS_DE_SILENCIO) {
                        if (!dormido) {
                            reconocedor.reset()
                            dormido = true
                            seguidos = 0
                        }
                        val hueco = previo
                        previo = trozo
                        trozo = hueco
                        previoLeidas = leidas
                        continue
                    }
                } else {
                    callado = 0
                }
                if (dormido) {
                    dormido = false
                    if (previoLeidas > 0) reconocedor.acceptWaveForm(previo, previoLeidas)
                }

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
     * Se intenta abrir la pantalla del dictado, con su círculo. Si no llega a
     * abrirse —falta el permiso de mostrarse sobre otras apps, o MIUI la
     * bloquea sin decir nada—, se atiende desde aquí: pitido, se escucha y se
     * contesta en voz alta. Nunca hace falta tocar nada para que escuche.
     */
    private fun alOirLaPalabra() {
        mantenerDespiertoAtendiendo(true)
        pausado = true
        // Por si entre la detección y este momento algo (la pantalla que se
        // enciende de nuevo, por ejemplo) volvió a arrancar la escucha: el
        // micrófono tiene que quedar libre para el dictado.
        actualizar()
        principal.removeCallbacks(reanudarSiempre)
        principal.postDelayed(reanudarSiempre, REANUDAR_SIEMPRE_TRAS_MS)

        val bloqueado = !pantallaEncendida ||
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (bloqueado) {
            // Con la pantalla apagada puede estar en un bolsillo. Si el
            // sensor de proximidad está tapado, no se enciende nada: se
            // atiende solo con la voz, y si no se dice nada, en silencio.
            if (!pantallaEncendida) {
                mirarSiEstaTapado { tapado ->
                    if (tapado) dictarAqui(silencioso = true) else abrirSobreElBloqueo()
                }
            } else {
                abrirSobreElBloqueo()
            }
            return
        }

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
            if (!abrio) dictarAqui() else mantenerDespiertoAtendiendo(false)
        }, ESPERA_PANTALLA_MS)
    }

    private fun mantenerDespiertoAtendiendo(si: Boolean) {
        val candado = atendiendo ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nebula:atender")
            ?.also {
                it.setReferenceCounted(false)
                atendiendo = it
            } ?: return
        if (si) candado.acquire(30_000L) else if (candado.isHeld) candado.release()
    }

    /**
     * Con el móvil bloqueado, una app no puede abrir una pantalla sin más.
     * Lo que sí puede es lanzar un aviso de pantalla completa, como una
     * llamada entrante o una alarma: el propio sistema lo abre encima del
     * bloqueo y enciende la pantalla. Es el mismo camino que la alarma de los
     * eventos. Si no llega a abrirse (en Xiaomi, sin "mostrar en pantalla de
     * bloqueo"), se atiende con la voz.
     */
    private fun abrirSobreElBloqueo() {
        val disparo = SystemClock.elapsedRealtime()
        Notificaciones.mostrarLlamadaDictado(this)
        principal.postDelayed({
            val abrio = VozActivity.abiertaEn >= disparo
            Preferencias.ponerAperturaBloqueadaEnBloqueo(this, !abrio)
            if (!abrio) {
                Notificaciones.quitarDictado(this)
                dictarAqui()
            } else {
                mantenerDespiertoAtendiendo(false)
            }
        }, ESPERA_BLOQUEO_MS)
    }

    /**
     * El sensor de proximidad dice si algo tapa la pantalla (un bolsillo, una
     * funda cerrada). Se mira una sola vez; si no contesta enseguida, se da
     * por destapado.
     */
    private fun mirarSiEstaTapado(alSaber: (Boolean) -> Unit) {
        val sensores = getSystemService(SensorManager::class.java)
        val sensor = sensores?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensores == null || sensor == null) return alSaber(false)
        var hecho = false
        val oyente = object : SensorEventListener {
            override fun onSensorChanged(evento: SensorEvent) {
                if (hecho) return
                hecho = true
                sensores.unregisterListener(this)
                alSaber(evento.values[0] < minOf(sensor.maximumRange, 3f))
            }
            override fun onAccuracyChanged(s: Sensor?, precision: Int) {}
        }
        sensores.registerListener(oyente, sensor, SensorManager.SENSOR_DELAY_FASTEST, principal)
        principal.postDelayed({
            if (!hecho) {
                hecho = true
                sensores.unregisterListener(oyente)
                alSaber(false)
            }
        }, 400)
    }

    /** [silencioso]: desde un bolsillo; si no se oye nada, no se dice nada. */
    private fun dictarAqui(silencioso: Boolean = false) {
        val m = modelo ?: return terminarDictado()
        dictando = true
        dictandoAhora = true
        actualizar()
        pitar()
        Notificaciones.mostrarDictado(this, "Te escucho…", null)
        Thread({
            // Lo justo para que el pitido no se lo coma la grabación.
            SystemClock.sleep(300)
            val texto = transcribir(m)
            principal.post { atender(texto, silencioso) }
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

    private fun atender(texto: String, silencioso: Boolean) {
        if (texto.isBlank() && silencioso) {
            Notificaciones.quitarDictado(this)
            return terminarDictado()
        }
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
        mantenerDespiertoAtendiendo(false)
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
        mantenerDespierto(false)
        mantenerDespiertoAtendiendo(false)
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
