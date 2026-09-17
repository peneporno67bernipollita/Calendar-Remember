package com.calendarremember.avisos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.calendarremember.MainActivity
import com.calendarremember.R
import com.calendarremember.datos.Evento
import java.time.format.DateTimeFormatter
import java.util.Locale

object Notificaciones {

    const val CANAL_AVISOS = "avisos"
    const val CANAL_ALARMAS = "alarmas"
    const val CANAL_AGENDA = "agenda"
    const val ID_AGENDA = 7001

    fun crearCanales(contexto: Context) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return

        // Los avisos de los días previos: se oyen, pero no interrumpen.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_AVISOS, "Avisos previos", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorios de los días anteriores a un evento."
            }
        )

        // La alarma del propio evento suena como una alarma de despertador:
        // por el canal de alarma, que ignora el modo silencio del timbre.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_ALARMAS, "Alarma del evento", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Suena a la hora exacta del evento."
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        // La agenda del día, fija en la pantalla de bloqueo. Es lo más cercano
        // a un widget de pantalla de bloqueo que permite Android en un móvil.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_AGENDA, "Agenda del día", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación fija con lo que tienes hoy."
                setShowBadge(false)
            }
        )
    }

    private fun abrirApp(contexto: Context, eventoId: String?): PendingIntent {
        val intent = Intent(contexto, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            eventoId?.let { putExtra("evento", it) }
        }
        return PendingIntent.getActivity(
            contexto, eventoId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Texto humano de la antelación: "Mañana a las 17:30", "En 2 días". */
    fun textoAntelacion(evento: Evento, minutos: Int): String {
        val hora = evento.inicio.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when {
            minutos == 0 -> if (evento.todoElDia) "Es hoy" else "Es ahora"
            minutos < 60 -> "En $minutos minutos"
            minutos < 1440 -> {
                val h = Math.round(minutos / 60f)
                if (h == 1) "En una hora" else "En $h horas"
            }
            else -> {
                val d = Math.round(minutos / 1440f)
                when (d) {
                    1 -> if (evento.todoElDia) "Mañana" else "Mañana a las $hora"
                    7 -> "Dentro de una semana"
                    else -> "Dentro de $d días"
                }
            }
        }
    }

    fun mostrarAviso(contexto: Context, evento: Evento, minutos: Int) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val notificacion = NotificationCompat.Builder(contexto, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_aviso)
            .setContentTitle(evento.titulo)
            .setContentText(textoAntelacion(evento, minutos))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(abrirApp(contexto, evento.id))
            .build()
        gestor.notify((evento.id + minutos).hashCode(), notificacion)
    }

    /**
     * La alarma del evento. Se manda con pantalla completa: si el móvil está
     * bloqueado, el aviso ocupa toda la pantalla como una llamada entrante en
     * vez de quedarse en una barra que nadie ve.
     */
    fun mostrarAlarma(contexto: Context, evento: Evento) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return

        val pantallaCompleta = PendingIntent.getActivity(
            contexto, evento.id.hashCode(),
            Intent(contexto, AlarmaActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("evento", evento.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificacion = NotificationCompat.Builder(contexto, CANAL_ALARMAS)
            .setSmallIcon(R.drawable.ic_aviso)
            .setContentTitle(evento.titulo)
            .setContentText(if (evento.todoElDia) "Es hoy" else "Es ahora")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pantallaCompleta, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pantallaCompleta)
            .build()
        gestor.notify(evento.id.hashCode(), notificacion)
    }

    fun quitar(contexto: Context, id: Int) {
        contexto.getSystemService(NotificationManager::class.java)?.cancel(id)
    }

    /**
     * Notificación fija con la agenda del día. Se queda en la pantalla de
     * bloqueo hasta que acabe el día.
     */
    fun mostrarAgendaDelDia(contexto: Context, eventos: List<Evento>) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        if (eventos.isEmpty()) {
            gestor.cancel(ID_AGENDA)
            return
        }
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val lineas = eventos.take(6).map {
            if (it.todoElDia) "· ${it.titulo}" else "${it.inicio.format(fmt)}  ${it.titulo}"
        }
        val estilo = NotificationCompat.InboxStyle()
        lineas.forEach { estilo.addLine(it) }

        val titulo = if (eventos.size == 1) "1 evento hoy" else "${eventos.size} eventos hoy"
        val notificacion = NotificationCompat.Builder(contexto, CANAL_AGENDA)
            .setSmallIcon(R.drawable.ic_aviso)
            .setContentTitle(titulo)
            .setContentText(lineas.joinToString("  ·  "))
            .setStyle(estilo)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(abrirApp(contexto, null))
            .build()
        gestor.notify(ID_AGENDA, notificacion)
    }

    fun locale(): Locale = Locale("es", "ES")
}
