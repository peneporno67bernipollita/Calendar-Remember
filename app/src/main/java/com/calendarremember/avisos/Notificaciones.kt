package com.calendarremember.avisos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.calendarremember.MainActivity
import com.calendarremember.R
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.ColorEvento
import com.calendarremember.datos.Etiquetas
import com.calendarremember.datos.Evento
import com.calendarremember.datos.Preferencias
import com.calendarremember.voz.EscuchaServicio
import com.calendarremember.voz.VozActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Notificaciones {

    const val CANAL_AVISOS = "avisos"

    // Un canal no se puede reconfigurar después de creado: Android ignora los
    // cambios. Como el canal "alarmas" original traía tono propio y ahora el
    // sonido lo lleva solo la pantalla de alarma, hace falta un canal nuevo y
    // borrar el viejo; si no, quien ya tenga la app seguiría oyendo los dos.
    const val CANAL_ALARMAS = "alarmas-sin-tono"
    private const val CANAL_ALARMAS_VIEJO = "alarmas"

    // La agenda también estrena canal. El viejo era de importancia baja, y
    // Android 13 esconde por defecto las notificaciones silenciosas en la
    // pantalla de bloqueo: justo donde tiene que verse. El nuevo es de
    // importancia normal pero sin sonido ni vibración.
    const val CANAL_AGENDA = "agenda-bloqueo"
    private const val CANAL_AGENDA_VIEJO = "agenda"
    const val ID_AGENDA = 7001
    private const val ID_DICTADO = 7002
    private const val CANAL_DICTADO = "dictado"
    private const val ID_REACTIVAR = 7003

    const val ACCION_DESCARTAR = "com.calendarremember.DESCARTAR"
    const val ACCION_APUNTAR_PLAN = "com.calendarremember.APUNTAR_PLAN"
    private const val CANAL_PLANES = "planes"

    private val FMT_HORA = DateTimeFormatter.ofPattern("HH:mm")
    private val ES = Locale("es", "ES")

    fun crearCanales(contexto: Context) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return

        gestor.deleteNotificationChannel(CANAL_ALARMAS_VIEJO)
        gestor.deleteNotificationChannel(CANAL_AGENDA_VIEJO)

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

        // El dictado sin pantalla: aparece arriba, pero sin sonido propio.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_DICTADO, "Dictado por voz", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Lo que entiende Nébula cuando la llamas por su nombre."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        // Los planes que llegan por WhatsApp: se ven y suenan como un
        // mensaje más, sin saltar encima de nada.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_PLANES, "Planes de WhatsApp", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Cuando te proponen un plan con día u hora, para apuntarlo de un toque."
            }
        )

        // La agenda fija, que hace de widget en la pantalla de bloqueo.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_AGENDA, "Agenda en la pantalla de bloqueo",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Tus próximos eventos, siempre a la vista."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
     * La agenda fija de la pantalla de bloqueo: hace de widget.
     *
     * Android 13 no deja poner widgets en el bloqueo, pero sí notificaciones,
     * así que esto es un widget con forma de notificación: plegada dice lo
     * siguiente que toca; desplegada, la fecha, los cuatro próximos eventos y
     * los botones de dictar y abrir.
     *
     * Solo cuenta lo que aún no ha pasado. Se repinta al cambiar los eventos,
     * cuando empieza cada uno y a medianoche, para que "Hoy" y "Mañana" no se
     * queden desfasados.
     */
    fun refrescarAgenda(contexto: Context) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return

        // Mientras Nébula escucha, esta notificación es además la obligatoria
        // del servicio: no se puede quitar aunque la agenda esté desactivada.
        if (!Preferencias.agendaEnBloqueo(contexto) && !EscuchaServicio.enMarcha) {
            gestor.cancel(ID_AGENDA)
            return
        }

        gestor.notify(ID_AGENDA, construirAgenda(contexto))

        // Cuando empiece el próximo evento, esta lista ya estará desfasada:
        // se deja programado el siguiente repintado.
        val ahora = LocalDateTime.now()
        Programador.programarRefresco(contexto, proximos().firstOrNull { !it.todoElDia && it.inicio.isAfter(ahora) })
    }

    /** Lo que aún no ha pasado, en orden: lo que enseña la agenda. */
    private fun proximos(): List<Evento> {
        val ahora = LocalDateTime.now()
        val hoy = LocalDate.now()
        return Almacen.eventos.value.filter {
            when {
                // Un viaje sigue a la vista mientras dura: "Hasta dom 25".
                it.variosDias -> !it.ultimoDia.isBefore(hoy)
                it.todoElDia -> !it.inicio.toLocalDate().isBefore(hoy)
                else -> it.inicio.isAfter(ahora)
            }
        }.take(4)
    }

    /** La notificación de la agenda, sin publicarla. La usa también el servicio. */
    fun construirAgenda(contexto: Context): Notification {
        val hoy = LocalDate.now()
        val proximos = proximos()
        val paquete = contexto.packageName

        // --- Plegada: una línea -------------------------------------------
        val plegada = RemoteViews(paquete, R.layout.notif_agenda_pequena)
        val primero = proximos.firstOrNull()
        if (primero == null) {
            plegada.setViewVisibility(R.id.np_cuando, View.GONE)
            plegada.setTextViewText(R.id.np_titulo, "Nada a la vista")
            plegada.setTextViewText(R.id.np_mas, "")
        } else {
            plegada.setViewVisibility(R.id.np_cuando, View.VISIBLE)
            plegada.setTextViewText(R.id.np_cuando, etiquetaCuando(primero, hoy))
            plegada.setTextColor(R.id.np_cuando, colorDe(primero))
            plegada.setTextViewText(R.id.np_titulo, primero.titulo)
            plegada.setTextViewText(
                R.id.np_mas,
                if (proximos.size > 1) "+${proximos.size - 1}" else "",
            )
        }

        // --- Desplegada: la fecha, cuatro eventos y los botones -----------
        val desplegada = RemoteViews(paquete, R.layout.notif_agenda_grande)
        desplegada.setTextViewText(
            R.id.ng_fecha,
            hoy.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).uppercase(ES),
        )
        desplegada.setViewVisibility(
            R.id.ng_vacio, if (proximos.isEmpty()) View.VISIBLE else View.GONE,
        )

        val filas = listOf(
            listOf(R.id.ng_fila_1, R.id.ng_barra_1, R.id.ng_cuando_1, R.id.ng_titulo_1),
            listOf(R.id.ng_fila_2, R.id.ng_barra_2, R.id.ng_cuando_2, R.id.ng_titulo_2),
            listOf(R.id.ng_fila_3, R.id.ng_barra_3, R.id.ng_cuando_3, R.id.ng_titulo_3),
            listOf(R.id.ng_fila_4, R.id.ng_barra_4, R.id.ng_cuando_4, R.id.ng_titulo_4),
        )
        filas.forEachIndexed { i, (fila, barra, cuando, titulo) ->
            val evento = proximos.getOrNull(i)
            if (evento == null) {
                desplegada.setViewVisibility(fila, View.GONE)
            } else {
                val color = colorDe(evento)
                desplegada.setViewVisibility(fila, View.VISIBLE)
                desplegada.setInt(barra, "setBackgroundColor", color)
                desplegada.setTextViewText(cuando, etiquetaCuando(evento, hoy))
                desplegada.setTextColor(cuando, color)
                desplegada.setTextViewText(titulo, evento.titulo)
            }
        }

        desplegada.setOnClickPendingIntent(
            R.id.ng_dictar,
            PendingIntent.getActivity(
                contexto, "agenda-dictar".hashCode(),
                Intent(contexto, VozActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        desplegada.setOnClickPendingIntent(R.id.ng_abrir, abrirApp(contexto, null))

        // El texto plano va también: algunas vistas de MIUI (y la lectura en
        // voz alta de accesibilidad) no pintan el diseño propio y tiran de él.
        val resumen = primero?.let { "${etiquetaCuando(it, hoy)} · ${it.titulo}" }
            ?: "Nada a la vista"

        return NotificationCompat.Builder(contexto, CANAL_AGENDA)
            .setSmallIcon(R.drawable.ic_aviso)
            .setContentTitle("Próximos")
            .setContentText(resumen)
            // Se ve junto al nombre de la app: avisa de que el micrófono está
            // atento, que es lo mínimo que hay que saber de algo que escucha.
            .setSubText(if (EscuchaServicio.enMarcha) "Escuchando «Nébula»" else null)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(plegada)
            .setCustomBigContentView(desplegada)
            // Fija: es un widget, no un aviso. Se quita desde los ajustes de
            // la app, no deslizándola.
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(abrirApp(contexto, null))
            .build()
    }

    /**
     * El dictado atendido por el servicio, sin pantalla: "Te escucho…" con lo
     * que va entendiendo, y luego la respuesta. Es lo que se ve cuando el
     * sistema no deja abrir el círculo; la respuesta además se dice en voz.
     * Sale por arriba sin sonar: un pitido ya ha avisado de que escucha, y un
     * sonido de notificación se colaría en la grabación.
     */
    fun mostrarDictado(contexto: Context, titulo: String, detalle: String?) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val notificacion = NotificationCompat.Builder(contexto, CANAL_DICTADO)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(titulo)
            .setContentText(detalle?.let { "«$it»" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(detalle?.let { "«$it»" } ?: ""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000)
            .setContentIntent(abrirApp(contexto, null))
            .build()
        gestor.notify(ID_DICTADO, notificacion)
    }

    /**
     * Un plan de WhatsApp: "¿Lo apunto?", con el mensaje entero y dos
     * botones. "Apuntar" lo guarda sin abrir nada; tocar el aviso (o
     * "Cambiar") lo abre en el editor, ya relleno, por si hay que retocarlo.
     */
    fun ofrecerPlan(contexto: Context, plan: Evento) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        crearCanales(contexto)
        val id = plan.id.hashCode()
        val json = plan.aJson().toString()

        val apuntar = PendingIntent.getBroadcast(
            contexto, id,
            Intent(contexto, ReceptorAviso::class.java).apply {
                action = ACCION_APUNTAR_PLAN
                putExtra("plan", json)
                data = Uri.parse("calendarremember://plan/${plan.id}")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val editar = PendingIntent.getActivity(
            contexto, id,
            Intent(contexto, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.BORRADOR, json)
                data = Uri.parse("calendarremember://editar/${plan.id}")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cuando = Etiquetas.corta(plan, LocalDate.now())
        val notificacion = NotificationCompat.Builder(contexto, CANAL_PLANES)
            .setSmallIcon(R.drawable.ic_aviso)
            .setContentTitle("¿Lo apunto? ${plan.titulo}")
            .setContentText(cuando)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$cuando\n${plan.notas.orEmpty()}"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(editar)
            .addAction(0, "Apuntar", apuntar)
            .addAction(0, "Cambiar", editar)
            .build()
        gestor.notify(id, notificacion)
    }

    /** Tras "Apuntar": lo confirma en el mismo sitio y se quita solo. */
    fun planApuntado(contexto: Context, plan: Evento) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val notificacion = NotificationCompat.Builder(contexto, CANAL_PLANES)
            .setSmallIcon(R.drawable.ic_aviso)
            .setContentTitle("Apuntado: ${plan.titulo}")
            .setContentText(Etiquetas.corta(plan, LocalDate.now()))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(5_000)
            .setContentIntent(abrirApp(contexto, plan.id))
            .build()
        gestor.notify(plan.id.hashCode(), notificacion)
    }

    /**
     * Abre el círculo del dictado encima de la pantalla de bloqueo. Es un
     * aviso de pantalla completa, como una llamada: con el móvil bloqueado,
     * el sistema no lo deja en la barra sino que abre directamente la
     * pantalla que lleva dentro, y enciende la pantalla si estaba apagada.
     * La pantalla del dictado lo quita en cuanto se abre.
     */
    fun mostrarLlamadaDictado(contexto: Context) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val abrir = PendingIntent.getActivity(
            contexto, "dictado-bloqueo".hashCode(),
            Intent(contexto, VozActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(VozActivity.DESDE_PALABRA, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(contexto, CANAL_DICTADO)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Nébula te escucha")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(abrir, true)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        gestor.notify(ID_DICTADO, notificacion)
    }

    fun quitarDictado(contexto: Context) {
        contexto.getSystemService(NotificationManager::class.java)?.cancel(ID_DICTADO)
    }

    /**
     * Tras reiniciar el móvil, Android no deja que el servicio vuelva a abrir
     * el micrófono solo: tiene que arrancarlo el usuario. Un toque aquí basta.
     */
    fun mostrarReactivarEscucha(contexto: Context) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val reactivar = PendingIntent.getForegroundService(
            contexto, "reactivar-escucha".hashCode(),
            Intent(contexto, EscuchaServicio::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(contexto, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Nébula no te escucha")
            .setContentText("Desde que reiniciaste el móvil. Toca para activarla.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(reactivar)
            .setAutoCancel(true)
            .build()
        gestor.notify(ID_REACTIVAR, notificacion)
    }

    /** "Hoy 17:30", "Mañana", "Vie 20 09:00", "Hasta dom 25". */
    private fun etiquetaCuando(evento: Evento, hoy: LocalDate): String = Etiquetas.corta(evento, hoy)

    private fun colorDe(evento: Evento): Int = when (evento.color) {
        ColorEvento.CIAN -> 0xFF00E5FF.toInt()
        ColorEvento.MAGENTA -> 0xFFFF2FD0.toInt()
        ColorEvento.VIOLETA -> 0xFF9D5CFF.toInt()
        ColorEvento.VERDE -> 0xFF39FF88.toInt()
        ColorEvento.AMBAR -> 0xFFFFB03A.toInt()
    }
}
