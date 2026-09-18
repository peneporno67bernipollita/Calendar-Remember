package com.calendarremember.voz

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.calendarremember.avisos.Notificaciones
import com.calendarremember.datos.Almacen
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.Executors

/**
 * Escucha "Nébula" y, al oírla, abre el dictado.
 *
 * Solo tiene el micrófono abierto con la pantalla encendida: así cumple lo
 * que se le pide (encender el móvil y hablarle) sin estar oyendo toda la
 * noche en el bolsillo, y la batería lo nota mucho menos.
 *
 * El motor no conoce la palabra "nébula": no está en su vocabulario. Pero
 * limitado a una sola palabra cercana que sí conoce, "nebulosa", reconoce
 * "Nébula" como ella y rechaza lo demás ("novela", "nevera", una charla
 * normal). Se probó con audio real antes de montarlo así.
 */
class EscuchaServicio : Service(), RecognitionListener {

    companion object {
        private const val PALABRA = "nebulosa"
        private const val ACCION_PAUSAR = "com.calendarremember.escucha.PAUSAR"
        private const val ACCION_REANUDAR = "com.calendarremember.escucha.REANUDAR"
        private const val ACCION_PARAR = "com.calendarremember.escucha.PARAR"

        /** Entre dos activaciones: evita que un "Nébula" dispare dos veces. */
        private const val PAUSA_ENTRE_ACTIVACIONES_MS = 3_000L

        /**
         * Si el dictado no llega a devolver el micrófono (se cierra de golpe,
         * el sistema lo mata...), la escucha vuelve sola pasado este tiempo.
         */
        private const val REANUDAR_SIEMPRE_TRAS_MS = 45_000L

        @Volatile
        var enMarcha = false
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
    private val hilo = Executors.newSingleThreadExecutor()

    private var modelo: Model? = null
    private var escucha: SpeechService? = null
    private var pausado = false
    private var pantallaEncendida = true
    private var ultimaActivacion = 0L

    private val reanudarSiempre = Runnable {
        if (pausado) {
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

        // Cargar el modelo lleva unos segundos (y la primera vez, copiarlo):
        // fuera del hilo principal.
        hilo.execute {
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
                pausado = false
                actualizar()
            }
            ACCION_PARAR -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // Si el sistema lo mata por falta de memoria, que lo vuelva a levantar.
        return START_STICKY
    }

    /** Abre o suelta el micrófono según toque: pantalla, pausa y modelo. */
    private fun actualizar() {
        val debeEscuchar = modelo != null && pantallaEncendida && !pausado
        if (debeEscuchar && escucha == null) empezar()
        if (!debeEscuchar && escucha != null) soltar()
    }

    private fun empezar() {
        val m = modelo ?: return
        escucha = try {
            val reconocedor = Recognizer(m, 16000f, "[\"$PALABRA\", \"[unk]\"]")
            SpeechService(reconocedor, 16000f).also { it.startListening(this) }
        } catch (e: Exception) {
            null
        }
    }

    private fun soltar() {
        escucha?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        escucha = null
    }

    // --- Lo que va oyendo -------------------------------------------------

    override fun onPartialResult(hipotesis: String?) = comprobar(hipotesis)
    override fun onResult(hipotesis: String?) = comprobar(hipotesis)
    override fun onFinalResult(hipotesis: String?) = comprobar(hipotesis)

    override fun onError(e: Exception?) {
        // Otro programa se ha quedado el micrófono, o falló al abrirlo. Se
        // suelta y se vuelve a intentar en un momento en vez de rendirse.
        soltar()
        principal.postDelayed({ actualizar() }, 2_000)
    }

    override fun onTimeout() {
        soltar()
        actualizar()
    }

    private fun comprobar(hipotesis: String?) {
        if (hipotesis == null || !hipotesis.contains(PALABRA)) return
        val ahora = SystemClock.elapsedRealtime()
        if (ahora - ultimaActivacion < PAUSA_ENTRE_ACTIVACIONES_MS) return
        ultimaActivacion = ahora

        // El micrófono se suelta ya: el dictado lo necesita libre.
        pausado = true
        soltar()
        principal.removeCallbacks(reanudarSiempre)
        principal.postDelayed(reanudarSiempre, REANUDAR_SIEMPRE_TRAS_MS)

        abrirDictado()
    }

    private fun abrirDictado() {
        val intent = Intent(this, VozActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(VozActivity.DESDE_PALABRA, true)
        }
        // Android no deja que un servicio abra pantallas por su cuenta, salvo
        // que la app tenga permiso para mostrarse sobre otras. Sin él, se
        // ofrece el dictado con una notificación que salta a la vista.
        if (Settings.canDrawOverlays(this)) {
            runCatching { startActivity(intent) }
                .onFailure { Notificaciones.mostrarLlamadaDictado(this) }
        } else {
            Notificaciones.mostrarLlamadaDictado(this)
            pausado = false
            actualizar()
        }
    }

    override fun onDestroy() {
        enMarcha = false
        principal.removeCallbacksAndMessages(null)
        soltar()
        runCatching { unregisterReceiver(receptorPantalla) }
        hilo.shutdown()
        runCatching { modelo?.close() }
        modelo = null
        super.onDestroy()
        // La agenda sigue (o se quita, si estaba desactivada), ya sin la
        // marca de que Nébula escucha.
        Notificaciones.refrescarAgenda(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
