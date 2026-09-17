package com.calendarremember.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.calendarremember.MainActivity
import com.calendarremember.R
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import com.calendarremember.voz.VozActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * El widget de la pantalla de inicio: lo que viene y un botón para dictar.
 *
 * Son tres huecos fijos en vez de una lista desplazable. Un widget se mira de
 * paso, no se navega: con los tres siguientes eventos ya sabes si hoy tienes
 * algo, y a cambio no hace falta un servicio aparte para alimentar la lista.
 */
class WidgetProximos : AppWidgetProvider() {

    override fun onUpdate(
        contexto: Context,
        gestor: AppWidgetManager,
        ids: IntArray,
    ) {
        Almacen.cargar(contexto)
        for (id in ids) pintar(contexto, gestor, id)
    }

    companion object {

        private val ES = Locale("es", "ES")
        private val FMT_HORA = DateTimeFormatter.ofPattern("HH:mm")
        private val FMT_DIA = DateTimeFormatter.ofPattern("EEE d", ES)

        /** Se llama cada vez que cambian los eventos. */
        fun refrescar(contexto: Context) {
            val gestor = AppWidgetManager.getInstance(contexto) ?: return
            val ids = gestor.getAppWidgetIds(
                ComponentName(contexto, WidgetProximos::class.java)
            )
            for (id in ids) pintar(contexto, gestor, id)
        }

        private fun pintar(contexto: Context, gestor: AppWidgetManager, id: Int) {
            val vistas = RemoteViews(contexto.packageName, R.layout.widget_proximos)
            val proximos = Almacen.proximos(3)

            val filas = listOf(
                Triple(R.id.fila_1, R.id.cuando_1, R.id.titulo_1),
                Triple(R.id.fila_2, R.id.cuando_2, R.id.titulo_2),
                Triple(R.id.fila_3, R.id.cuando_3, R.id.titulo_3),
            )

            filas.forEachIndexed { indice, (fila, cuando, titulo) ->
                val evento = proximos.getOrNull(indice)
                if (evento == null) {
                    vistas.setViewVisibility(fila, View.GONE)
                } else {
                    vistas.setViewVisibility(fila, View.VISIBLE)
                    vistas.setTextViewText(cuando, etiqueta(evento))
                    vistas.setTextViewText(titulo, evento.titulo)
                    vistas.setTextColor(cuando, colorDe(evento))
                }
            }

            vistas.setViewVisibility(
                R.id.vacio,
                if (proximos.isEmpty()) View.VISIBLE else View.GONE,
            )

            // Un toque en el micro dicta; un toque en el resto abre la app.
            vistas.setOnClickPendingIntent(R.id.boton_voz, intencion(contexto, VozActivity::class.java, 1))
            vistas.setOnClickPendingIntent(R.id.raiz, intencion(contexto, MainActivity::class.java, 2))

            gestor.updateAppWidget(id, vistas)
        }

        private fun intencion(contexto: Context, clase: Class<*>, codigo: Int): PendingIntent =
            PendingIntent.getActivity(
                contexto, codigo,
                Intent(contexto, clase).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun etiqueta(evento: Evento): String {
            val dia = evento.inicio.toLocalDate()
            val hoy = LocalDate.now()
            val cuando = when (dia) {
                hoy -> "Hoy"
                hoy.plusDays(1) -> "Mañana"
                else -> dia.format(FMT_DIA).replaceFirstChar { it.uppercase(ES) }
            }
            return if (evento.todoElDia) cuando
            else "$cuando ${evento.inicio.format(FMT_HORA)}"
        }

        private fun colorDe(evento: Evento): Int = when (evento.color) {
            com.calendarremember.datos.ColorEvento.CIAN -> 0xFF00E5FF.toInt()
            com.calendarremember.datos.ColorEvento.MAGENTA -> 0xFFFF2FD0.toInt()
            com.calendarremember.datos.ColorEvento.VIOLETA -> 0xFF9D5CFF.toInt()
            com.calendarremember.datos.ColorEvento.VERDE -> 0xFF39FF88.toInt()
            com.calendarremember.datos.ColorEvento.AMBAR -> 0xFFFFB03A.toInt()
        }
    }
}
