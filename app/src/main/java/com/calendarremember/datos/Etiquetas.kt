package com.calendarremember.datos

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cómo se escribe en corto cuándo es un evento, igual en la app, la agenda
 * de la pantalla de bloqueo y el widget: "Hoy 17:30", "Mañana", "Vie 20
 * 09:00". Lo que dura varios días dice hasta cuándo: "Hasta dom 25" si ya
 * ha empezado, "Dom 27 → 25 oct" si no.
 */
object Etiquetas {

    private val ES = Locale("es", "ES")
    private val HORA = DateTimeFormatter.ofPattern("HH:mm")
    private val DIA = DateTimeFormatter.ofPattern("EEE d", ES)
    private val DIA_MES = DateTimeFormatter.ofPattern("d MMM", ES)

    /** "Hoy", "Mañana", "Vie 20". */
    fun dia(fecha: LocalDate, hoy: LocalDate): String = when (fecha) {
        hoy -> "Hoy"
        hoy.plusDays(1) -> "Mañana"
        else -> fecha.format(DIA).replace(".", "").replaceFirstChar { it.uppercase(ES) }
    }

    fun corta(evento: Evento, hoy: LocalDate): String {
        val inicio = evento.inicio.toLocalDate()
        val hora = if (evento.todoElDia) "" else " " + evento.inicio.format(HORA)
        if (!evento.variosDias) return dia(inicio, hoy) + hora

        val ultimo = evento.ultimoDia
        if (!inicio.isAfter(hoy)) {
            return "Hasta " + dia(ultimo, hoy).replaceFirstChar { it.lowercase(ES) }
        }
        return dia(inicio, hoy) + hora + " → " + finCorto(inicio, ultimo)
    }

    /** "27 – 29 sep", "27 sep – 25 oct": el tramo entero, para la lista de la app. */
    fun tramo(evento: Evento): String {
        val inicio = evento.inicio.toLocalDate()
        val ultimo = evento.ultimoDia
        val primero = if (inicio.month == ultimo.month) inicio.dayOfMonth.toString()
            else inicio.format(DIA_MES).replace(".", "")
        return primero + " – " + ultimo.format(DIA_MES).replace(".", "")
    }

    /** El final de un tramo: el día solo si es del mismo mes, con el mes si no. */
    private fun finCorto(inicio: LocalDate, ultimo: LocalDate): String =
        if (inicio.month == ultimo.month && inicio.year == ultimo.year) ultimo.dayOfMonth.toString()
        else ultimo.format(DIA_MES).replace(".", "")
}
