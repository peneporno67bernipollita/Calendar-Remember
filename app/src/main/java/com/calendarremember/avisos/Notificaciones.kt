package com.calendarremember.avisos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.calendarremember.MainActivity
import com.calendarremember.R
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Notificaciones {

    const val CANAL_AVISOS = "avisos"

    // Un canal no se puede reconfigurar después de creado: Android ignora los
    // cambios. Como el canal "alarmas" original traía tono propio y ahora el
    // sonido lo lleva solo la pantalla de alarma, hace falta un canal nuevo y
    // borrar el viejo; si no, quien ya tenga la app seguiría oyendo los dos.
    const val CANAL_ALARMAS = "alarmas-sin-tono"
    private const val CANAL_ALARMAS_VIEJO = "alarmas"

    const val CANAL_AGENDA = "agenda"
    const val ID_AGENDA = 7001

    const val ACCION_DESCARTAR = "com.calendarremember.DESCARTAR"

    private val FMT_HORA = DateTimeFormatter.ofPattern("HH:mm")

    fun crearCanales(contexto: Context) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return

        gestor.deleteNotificationChannel(CANAL_ALARMAS_VIEJO)

        // Los avisos de los días previos: se oyen, pero no interrumpen.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_AVISOS, "Avisos previos", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorios de los días anteriores a un evento."
            }
        )

        // Sin tono ni vibración propios: de eso se encarga la pantalla de
        // alarma, que es la única que suena y la que sabe cuándo callarse.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_ALARMAS, "Alarma del evento", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Salta a la hora exacta del evento."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        // La agenda del día, visible en la pantalla de bloqueo.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_AGENDA, "Agenda del día", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lo que te queda por hacer hoy."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
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
        val hora = evento.inicio.format(FMT_HORA)
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
     * La alarma del evento. Va con pantalla completa: si el móvil está
     * bloqueado, ocupa toda la pantalla como una llamada entrante en vez de
     * quedarse en una barra que nadie ve.
     *
     * No es fija: se puede descartar deslizando o con su botón. Una alarma que
     * no hay manera de quitar es peor que no tener alarma.
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

        val descartar = PendingIntent.getBroadcast(
            contexto, (evento.id + "descartar").hashCode(),
            Intent(contexto, ReceptorAviso::class.java).apply {
                action = ACCION_DESCARTAR
                putExtra("evento", evento.id)
                data = Uri.parse("calendarremember://descartar/${evento.id}")
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
            // Al descartarla desde la barra también hay que callar el sonido.
            .setDeleteIntent(descartar)
            .addAction(0, "Hecho", descartar)
            .setContentIntent(pantallaCompleta)
            .build()
        gestor.notify(evento.id.hashCode(), notificacion)
    }

    fun quitar(contexto: Context, id: Int) {
        contexto.getSystemService(NotificationManager::class.java)?.cancel(id)
    }

    /**
     * Notificación con lo que queda por hacer hoy, visible en la pantalla de
     * bloqueo. Solo lista lo que aún no ha pasado: una agenda que sigue
     * anunciando un evento de hace media hora estorba más de lo que informa.
     * Cuando no queda nada, desaparece sola.
     */
    fun refrescarAgendaDelDia(contexto: Context) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val ahora = LocalDateTime.now()
        val hoy = LocalDate.now()

        val pendientes = Almacen.eventos.value.filter {
            it.inicio.toLocalDate() == hoy &&
                (it.todoElDia || it.inicio.isAfter(ahora))
        }

        if (pendientes.isEmpty()) {
            gestor.cancel(ID_AGENDA)
            return
        }

        val lineas = pendientes.take(6).map {
            if (it.todoElDia) "· ${it.titulo}" else "${it.inicio.format(FMT_HORA)}  ${it.titulo}"
        }
        val estilo = NotificationCompat.InboxStyle()
        lineas.forEach { estilo.addLine(it) }

        val titulo = if (pendientes.size == 1) "1 evento hoy" else "${pendientes.size} eventos hoy"
        val notificacion = NotificationCompat.Builder(contexto, CANAL_AGENDA)
            .setSmallIcon(R.drawable.ic_aviso)
            .setContentTitle(titulo)
            .setContentText(lineas.joinToString("  ·  "))
            .setStyle(estilo)
            // Descartable a propósito: si molesta, se quita de un gesto y
            // vuelve al próximo cambio de la agenda.
            .setOngoing(false)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(abrirApp(contexto, null))
            .build()
        gestor.notify(ID_AGENDA, notificacion)
    }
}
