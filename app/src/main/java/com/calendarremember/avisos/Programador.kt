package com.calendarremember.avisos

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.calendarremember.datos.Evento
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Programa en el sistema los avisos de cada evento.
 *
 * Las alarmas las lanza Android, no la app: no hace falta que Nébula
 * esté abierta ni viva, y el aviso llega con el móvil suspendido.
 * Ese es justamente el motivo de que esto sea una app y no una página web.
 */
object Programador {

    /** Identificador estable de cada aviso, para poder cancelarlo después. */
    private fun codigo(idEvento: String, minutos: Int): Int =
        (idEvento + "#" + minutos).hashCode()

    /**
     * Solo se programan los avisos del próximo mes. El resto se programan
     * solos cuando la fecha se acerque, en la pasada de mantenimiento diaria:
     * dejar miles de alarmas puestas en el sistema no es gratis.
     */
    private const val HORIZONTE_DIAS = 31L

    fun reprogramarTodo(contexto: Context, eventos: List<Evento>) {
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return
        val ahora = System.currentTimeMillis()
        val limite = ahora + HORIZONTE_DIAS * 86_400_000L

        for (evento in eventos) {
            for (minutos in evento.avisos) {
                val cuando = evento.momentoAviso(minutos)
                val intencion = intencionAviso(contexto, evento, minutos)

                // Se cancela siempre antes: si el evento cambió de hora, la
                // alarma vieja seguiría puesta con la hora anterior.
                gestor.cancel(intencion)

                if (cuando <= ahora || cuando > limite) continue
                programar(gestor, cuando, intencion, exacta = minutos == 0)
            }
        }
        programarMantenimiento(contexto, gestor)
    }

    private fun programar(
        gestor: AlarmManager,
        cuando: Long,
        intencion: PendingIntent,
        exacta: Boolean,
    ) {
        val puedeExactas = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                gestor.canScheduleExactAlarms()

        if (exacta && puedeExactas) {
            // La alarma del propio evento tiene que sonar cuando toca, aunque
            // el móvil lleve horas en reposo.
            gestor.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion)
        } else {
            // Los avisos de días antes admiten unos minutos de margen: así no
            // gastan batería despertando el móvil al segundo exacto.
            gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion)
        }
    }

    private fun intencionAviso(contexto: Context, evento: Evento, minutos: Int): PendingIntent {
        val intent = Intent(contexto, ReceptorAviso::class.java).apply {
            action = "com.calendarremember.AVISO"
            putExtra("evento", evento.id)
            putExtra("minutos", minutos)
            // El id va también en los datos para que Android no reutilice un
            // PendingIntent de otro evento por parecerse el extra.
            data = android.net.Uri.parse("calendarremember://aviso/${evento.id}/$minutos")
        }
        return PendingIntent.getBroadcast(
            contexto, codigo(evento.id, minutos), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Una pasada diaria que vuelve a programar todo. Es lo que hace entrar en
     * el horizonte a los eventos lejanos según se acercan.
     *
     * Va justo después de medianoche y no de madrugada: la agenda de la
     * pantalla de bloqueo dice "Hoy" y "Mañana", y a las 00:00 esas palabras
     * cambian de significado.
     */
    private fun programarMantenimiento(contexto: Context, gestor: AlarmManager) {
        val intent = Intent(contexto, ReceptorAviso::class.java).apply {
            action = "com.calendarremember.MANTENIMIENTO"
            data = android.net.Uri.parse("calendarremember://mantenimiento")
        }
        val intencion = PendingIntent.getBroadcast(
            contexto, "mantenimiento".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val manana = LocalDateTime.now().toLocalDate().plusDays(1).atTime(0, 5)
        val cuando = manana.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion)
    }

    const val ACCION_REFRESCAR = "com.calendarremember.REFRESCAR"

    /**
     * Deja programado el repintado de la agenda para cuando empiece [evento].
     *
     * Un evento sin aviso "a la hora" no despierta a la app al empezar, así
     * que sin esto la agenda lo seguiría anunciando como próximo hasta el
     * siguiente cambio. Solo hace falta uno: cada repintado programa el suyo.
     */
    fun programarRefresco(contexto: Context, evento: Evento?) {
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return
        val intencion = PendingIntent.getBroadcast(
            contexto, ACCION_REFRESCAR.hashCode(),
            Intent(contexto, ReceptorAviso::class.java).apply {
                action = ACCION_REFRESCAR
                data = android.net.Uri.parse("calendarremember://refrescar")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        gestor.cancel(intencion)
        if (evento == null) return
        // Un minuto después de empezar, para que ya cuente como pasado. No
        // hace falta exactitud: da igual que la lista se ponga al día un
        // par de minutos tarde.
        val cuando = evento.inicioMillis + 60_000L
        if (cuando <= System.currentTimeMillis()) return
        gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion)
    }
}
