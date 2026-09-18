package com.calendarremember.avisos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import com.calendarremember.datos.Preferencias
import com.calendarremember.voz.EscuchaServicio
import com.calendarremember.widget.WidgetProximos

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
                // El evento que acaba de sonar ya no es "lo que queda por
                // hacer hoy", así que la agenda se vuelve a pintar sin él.
                Notificaciones.refrescarAgenda(contexto)
                WidgetProximos.refrescar(contexto)
            }

            // Descartar desde la barra o desde el botón de la notificación:
            // callar el sonido es parte de descartar, no algo aparte.
            Notificaciones.ACCION_DESCARTAR -> {
                val id = intent.getStringExtra("evento")
                Sonido.parar()
                id?.let { Notificaciones.quitar(contexto, it.hashCode()) }
                Notificaciones.refrescarAgenda(contexto)
            }

            // "Apuntar" en el aviso de un plan de WhatsApp.
            Notificaciones.ACCION_APUNTAR_PLAN -> {
                val json = intent.getStringExtra("plan") ?: return
                val plan = runCatching { Evento.deJson(org.json.JSONObject(json)) }.getOrNull() ?: return
                Almacen.guardar(contexto, plan)
                Notificaciones.planApuntado(contexto, plan)
            }

            // Un evento acaba de empezar: la agenda de la pantalla de bloqueo
            // lo sigue anunciando como "próximo" y hay que quitarlo.
            Programador.ACCION_REFRESCAR -> {
                Notificaciones.refrescarAgenda(contexto)
                WidgetProximos.refrescar(contexto)
            }

            "com.calendarremember.MANTENIMIENTO",
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // Las series ("todos los martes") se alargan antes de
                // reprogramar, para que las nuevas repeticiones entren ya.
                Almacen.alargarSeries(contexto)
                // Al reiniciar el móvil Android borra todas las alarmas
                // programadas. Sin esto, los avisos se perderían en silencio.
                Programador.reprogramarTodo(contexto, Almacen.eventos.value)
                Notificaciones.refrescarAgenda(contexto)
                WidgetProximos.refrescar(contexto)

                // Tras reiniciar o actualizar, la escucha no puede volver sola
                // al micrófono: Android exige que la arranque el usuario.
                val arranque = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
                if (arranque && Preferencias.escuchaActiva(contexto) && !EscuchaServicio.enMarcha) {
                    Notificaciones.mostrarReactivarEscucha(contexto)
                }
            }
        }
    }
}
