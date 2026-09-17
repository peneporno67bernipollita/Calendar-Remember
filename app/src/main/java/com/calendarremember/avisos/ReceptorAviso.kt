package com.calendarremember.avisos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calendarremember.datos.Almacen
import java.time.LocalDate

/**
 * Punto de entrada de todo lo que dispara el sistema: los avisos, la pasada
 * de mantenimiento y el arranque del móvil.
 *
 * Se ejecuta con la app cerrada, así que lo primero es siempre cargar los
 * eventos del disco: aquí no hay nada en memoria de antes.
 */
class ReceptorAviso : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        Almacen.cargar(contexto)
        Notificaciones.crearCanales(contexto)

        when (intent.action) {
            "com.calendarremember.AVISO" -> {
                val id = intent.getStringExtra("evento") ?: return
                val minutos = intent.getIntExtra("minutos", 0)
                val evento = Almacen.porId(id) ?: return

                if (minutos == 0) {
                    Notificaciones.mostrarAlarma(contexto, evento)
                } else {
                    Notificaciones.mostrarAviso(contexto, evento, minutos)
                }
                refrescarAgendaDelDia(contexto)
            }

            "com.calendarremember.MANTENIMIENTO",
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // Al reiniciar el móvil Android borra todas las alarmas
                // programadas. Sin esto, los avisos se perderían en silencio.
                Programador.reprogramarTodo(contexto, Almacen.eventos.value)
                refrescarAgendaDelDia(contexto)
                com.calendarremember.widget.WidgetProximos.refrescar(contexto)
            }
        }
    }

    private fun refrescarAgendaDelDia(contexto: Context) {
        val hoy = LocalDate.now()
        val deHoy = Almacen.eventos.value.filter { it.inicio.toLocalDate() == hoy }
        Notificaciones.mostrarAgendaDelDia(contexto, deHoy)
    }
}
