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
 * Las alarmas las lanza Android, no la app: no hace falta que Calendar
 * Remember esté abierta ni viva, y el aviso llega con el móvil suspendido.
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
     * Una pasada diaria de madrugada que vuelve a programar todo. Es lo que
     * hace entrar en el horizonte a los eventos lejanos según se acercan.
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
        val manana = LocalDateTime.now().toLocalDate().plusDays(1).atTime(3, 30)
        val cuando = manana.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion)
    }
}
