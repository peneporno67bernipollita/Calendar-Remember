package com.calendarremember.avisos

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * El sonido de la alarma, en un único sitio.
 *
 * Antes sonaban dos a la vez: el tono del canal de notificación y el
 * reproductor de la pantalla de alarma, cada uno por su lado y con un
 * pequeño desfase entre ellos. La regla ahora es que el canal no suena y
 * que aquí solo puede haber un sonido en marcha: arrancar dos veces no
 * duplica nada, porque si ya está sonando no se hace nada.
 */
object Sonido {

    private var reproductor: MediaPlayer? = null
    private var vibrador: Vibrator? = null

    val sonando: Boolean
        get() = reproductor != null

    @Synchronized
    fun arrancar(contexto: Context) {
        if (reproductor != null) return

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        runCatching {
            reproductor = MediaPlayer().apply {
                setDataSource(contexto.applicationContext, uri)
                // Por el canal de alarma: suena aunque el timbre esté en silencio.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { reproductor = null }

        vibrador = contexto.getSystemService(Vibrator::class.java)
        runCatching {
            vibrador?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400), 0)
            )
        }
    }

    @Synchronized
    fun parar() {
        runCatching {
            reproductor?.stop()
            reproductor?.release()
        }
        reproductor = null
        runCatching { vibrador?.cancel() }
        vibrador = null
    }
}
