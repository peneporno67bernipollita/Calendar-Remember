package com.calendarremember.voz

import com.calendarremember.datos.Evento
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Los eventos que se repiten: "todos los martes", "cada día", "todos los
 * años".
 *
 * Cada repetición se crea de verdad, como un evento normal de la misma serie,
 * hasta un horizonte; la pasada de medianoche la va alargando. Así nada más
 * en la app tiene que entender de repeticiones: el calendario las pinta, las
 * alarmas suenan y el buscador las encuentra como a cualquier otro evento.
 */
object Series {

    /**
     * Hasta dónde se crean por adelantado. Lo diario, poco: cada repetición
     * lleva sus alarmas, y Android no admite más de unos cientos por app.
     */
    fun horizonteDias(r: Repeticion): Long = when (r) {
        Repeticion.DIARIA -> 60
        Repeticion.SEMANAL -> 182
        Repeticion.MENSUAL -> 366
        Repeticion.ANUAL -> 5 * 366
        Repeticion.NINGUNA -> 0
    }

    /**
     * La repetición número [n] contando desde [base]. Se calcula siempre desde
     * la primera, no desde la anterior: un día 31 que cae en un mes de 30 no
     * arrastra el 30 a los meses siguientes.
     */
    fun enesima(base: LocalDateTime, r: Repeticion, n: Long): LocalDateTime = when (r) {
        Repeticion.DIARIA -> base.plusDays(n)
        Repeticion.SEMANAL -> base.plusWeeks(n)
        Repeticion.MENSUAL -> base.plusMonths(n)
        Repeticion.ANUAL -> base.plusYears(n)
        Repeticion.NINGUNA -> base
    }

    /**
     * Avisos razonables para algo que se repite, si no se dijeron otros. Lo
     * diario (una pastilla, sacar al perro) basta con que suene a su hora; un
     * aviso el día antes para algo de cada día sería ruido.
     */
    fun avisos(r: Repeticion, todoElDia: Boolean): List<Int> = when (r) {
        Repeticion.DIARIA -> listOf(0)
        Repeticion.SEMANAL -> if (todoElDia) listOf(0) else listOf(60, 0)
        Repeticion.MENSUAL -> listOf(1440, 0)
        Repeticion.ANUAL -> listOf(10080, 1440, 0)
        Repeticion.NINGUNA -> if (todoElDia) listOf(1440, 0) else listOf(1440, 60, 0)
    }

    /** Una serie nueva: la primera vez y todas las que caben en el horizonte. */
    fun crear(primera: Evento, r: Repeticion, hoy: LocalDate): List<Evento> {
        val serie = UUID.randomUUID().toString()
        val hasta = hoy.plusDays(horizonteDias(r))
        return generateSequence(0L) { it + 1 }
            .map { n -> enesima(primera.inicio, r, n) }
            .takeWhile { !it.toLocalDate().isAfter(hasta) }
            .map { inicio ->
                primera.copy(id = UUID.randomUUID().toString(), inicio = inicio, serie = serie, repeticion = r)
            }
            .toList()
    }

    /**
     * Las repeticiones que faltan para que cada serie vuelva a llegar a su
     * horizonte. Se llama a diario; si una serie ya llega, no añade nada.
     */
    fun alargar(eventos: List<Evento>, hoy: LocalDate): List<Evento> =
        eventos.filter { it.serie != null && it.repeticion != Repeticion.NINGUNA }
            .groupBy { it.serie!! }
            .flatMap { (_, delaSerie) ->
                val base = delaSerie.minBy { it.inicio }
                val ultima = delaSerie.maxOf { it.inicio }
                val r = base.repeticion
                val hasta = hoy.plusDays(horizonteDias(r))
                // Se alarga cuando le queda menos de la mitad: así no se toca
                // el fichero de eventos todos los días por nada.
                if (ultima.toLocalDate().isAfter(hoy.plusDays(horizonteDias(r) / 2))) return@flatMap emptyList()
                generateSequence(1L) { it + 1 }
                    .map { n -> enesima(base.inicio, r, n) }
                    .dropWhile { !it.isAfter(ultima) }
                    .takeWhile { !it.toLocalDate().isAfter(hasta) }
                    .map { inicio -> base.copy(id = UUID.randomUUID().toString(), inicio = inicio) }
                    .toList()
            }

    /** "todos los martes a las 19:00", "cada día a las 09:00", "todos los años el 3 de mayo". */
    fun describir(primera: Evento, r: Repeticion): String {
        val hora = if (primera.todoElDia) "" else " a las " +
            primera.inicio.toLocalTime().toString().take(5)
        // "todos los martes", pero "todos los sábados": los que acaban en o
        // hacen el plural con ese.
        val dia = primera.inicio.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES"))
            .let { if (it.endsWith("o")) it + "s" else it }
        return when (r) {
            Repeticion.DIARIA -> "cada día$hora"
            Repeticion.SEMANAL -> "todos los $dia$hora"
            Repeticion.MENSUAL -> "todos los meses el día ${primera.inicio.dayOfMonth}$hora"
            Repeticion.ANUAL -> "todos los años el " + primera.inicio.format(
                java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM", java.util.Locale("es", "ES"))
            ) + hora
            Repeticion.NINGUNA -> ""
        }
    }
}
