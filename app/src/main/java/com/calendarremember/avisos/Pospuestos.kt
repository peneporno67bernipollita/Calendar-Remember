package com.calendarremember.avisos

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.calendarremember.datos.Evento

/**
 * Posponer no toca el evento: solo pone otra alarma dentro de un rato. Si
 * cambiase la hora del evento, el resto de avisos se recalcularían y un
 * "diez minutos más" acabaría moviendo la cita entera.
 */
object Pospuestos {

    fun posponer(contexto: Context, evento: Evento, minutos: Int) {
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return
        val cuando = System.currentTimeMillis() + minutos * 60_000L

        val intent = Intent(contexto, ReceptorAviso::class.java).apply {
            action = "com.calendarremember.AVISO"
            putExtra("evento", evento.id)
            putExtra("minutos", 0)
            data = Uri.parse("calendarremember://pospuesto/${evento.id}/$cuando")
        }
        val intencion = PendingIntent.getBroadcast(
            contexto, (evento.id + "pospuesto").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        Notificaciones.quitar(contexto, evento.id.hashCode())

        val puedeExactas = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                gestor.canScheduleExactAlarms()
        if (puedeExactas) {
            gestor.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion)
        } else {
            gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion)
        }
    }
}
