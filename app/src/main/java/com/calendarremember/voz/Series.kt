package com.calendarremember.voz

import com.calendarremember.datos.Evento
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Los eventos que se repiten: "todos los martes", "cada día", "cada dos
 * semanas", "los martes y jueves", "todos los años".
 *
 * Cada repetición se crea de verdad, como un evento normal de la misma serie,
 * hasta un horizonte; la pasada de medianoche la va alargando. Así nada más
 * en la app tiene que entender de repeticiones: el calendario las pinta, las
 * alarmas suenan y el buscador las encuentra como a cualquier otro evento.
 *
 * "Los martes y jueves" son dos cadenas semanales (una por día) con la misma
 * serie: se borran juntas y se describen juntas.
 */
object Series {

    private val ES = Locale("es", "ES")

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
    fun enesima(base: LocalDateTime, r: Repeticion, n: Long, intervalo: Int = 1): LocalDateTime {
        val k = n * intervalo.coerceAtLeast(1)
        return when (r) {
            Repeticion.DIARIA -> base.plusDays(k)
            Repeticion.SEMANAL -> base.plusWeeks(k)
            Repeticion.MENSUAL -> base.plusMonths(k)
            Repeticion.ANUAL -> base.plusYears(k)
            Repeticion.NINGUNA -> base
        }
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
    fun crear(
        primera: Evento,
        r: Repeticion,
        hoy: LocalDate,
        serie: String = UUID.randomUUID().toString(),
    ): List<Evento> {
        val hasta = hoy.plusDays(horizonteDias(r))
        return generateSequence(0L) { it + 1 }
            .map { n -> enesima(primera.inicio, r, n, primera.intervalo) }
            .takeWhile { !it.toLocalDate().isAfter(hasta) }
            .map { inicio ->
                primera.copy(
                    id = UUID.randomUUID().toString(), inicio = inicio, serie = serie, repeticion = r,
                    hasta = desplazado(primera, inicio),
                )
            }
            .toList()
    }

    /**
     * Las repeticiones que faltan para que cada serie vuelva a llegar a su
     * horizonte. Se llama a diario; si una serie ya llega, no añade nada.
     * Las semanales se alargan día a día de la semana: "los martes y
     * jueves" son dos cadenas.
     */
    fun alargar(eventos: List<Evento>, hoy: LocalDate): List<Evento> =
        eventos.filter { it.serie != null && it.repeticion != Repeticion.NINGUNA }
            .groupBy {
                it.serie!! + if (it.repeticion == Repeticion.SEMANAL) "#" + it.inicio.dayOfWeek else ""
            }
            .flatMap { (_, cadena) ->
                val base = cadena.minBy { it.inicio }
                val ultima = cadena.maxOf { it.inicio }
                val r = base.repeticion
                val hasta = hoy.plusDays(horizonteDias(r))
                // Se alarga cuando le queda menos de la mitad: así no se toca
                // el fichero de eventos todos los días por nada.
                if (ultima.toLocalDate().isAfter(hoy.plusDays(horizonteDias(r) / 2))) return@flatMap emptyList()
                generateSequence(1L) { it + 1 }
                    .map { n -> enesima(base.inicio, r, n, base.intervalo) }
                    .dropWhile { !it.isAfter(ultima) }
                    .takeWhile { !it.toLocalDate().isAfter(hasta) }
                    .map { inicio ->
                        base.copy(id = UUID.randomUUID().toString(), inicio = inicio, hasta = desplazado(base, inicio))
                    }
                    .toList()
            }

    /** El último día de una repetición que dura varios, movido con ella. */
    private fun desplazado(modelo: Evento, inicio: LocalDateTime): LocalDate? =
        if (!modelo.variosDias) null
        else modelo.hasta!!.plusDays(ChronoUnit.DAYS.between(modelo.inicio.toLocalDate(), inicio.toLocalDate()))

    /** Los días de la semana en que cae una serie semanal, mirando todas sus repeticiones. */
    fun diasDe(evento: Evento, todos: List<Evento>): List<DayOfWeek> =
        if (evento.serie == null || evento.repeticion != Repeticion.SEMANAL) listOf(evento.inicio.dayOfWeek)
        else todos.filter { it.serie == evento.serie }.map { it.inicio.dayOfWeek }.distinct().sorted()

    /**
     * "todos los martes a las 19:00", "cada día a las 09:00", "de lunes a
     * viernes a las 07:00", "cada dos semanas, los martes", "todos los años
     * el 3 de mayo". [dias]: los de la semana en que se repite, si son varios.
     */
    fun describir(primera: Evento, r: Repeticion, dias: List<DayOfWeek> = listOf(primera.inicio.dayOfWeek)): String {
        val hora = if (primera.todoElDia) "" else " a las " + primera.inicio.toLocalTime().toString().take(5)
        val cada = primera.intervalo
        return when (r) {
            Repeticion.DIARIA -> if (cada > 1) "cada ${numero(cada)} días$hora" else "cada día$hora"
            Repeticion.SEMANAL -> {
                val cuales = diasDeLaSemana(dias.ifEmpty { listOf(primera.inicio.dayOfWeek) })
                if (cada > 1) "cada ${numero(cada)} semanas, ${cuales.removePrefix("todos ")}$hora" else "$cuales$hora"
            }
            Repeticion.MENSUAL -> (if (cada > 1) "cada ${numero(cada)} meses" else "todos los meses") +
                " el día ${primera.inicio.dayOfMonth}$hora"
            Repeticion.ANUAL -> (if (cada > 1) "cada ${numero(cada)} años" else "todos los años") + " el " +
                primera.inicio.format(DateTimeFormatter.ofPattern("d 'de' MMMM", ES)) + hora
            Repeticion.NINGUNA -> ""
        }
    }

    /** "todos los martes", "todos los martes y jueves", "de lunes a viernes", "los fines de semana". */
    private fun diasDeLaSemana(dias: List<DayOfWeek>): String {
        val conjunto = dias.toSortedSet()
        val laborables = sortedSetOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        if (conjunto == laborables) return "de lunes a viernes"
        if (conjunto == sortedSetOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return "los fines de semana"
        // "todos los martes", pero "todos los sábados": los que acaban en o
        // hacen el plural con ese.
        val nombres = conjunto.map { d ->
            d.getDisplayName(TextStyle.FULL, ES).let { if (it.endsWith("o")) it + "s" else it }
        }
        val lista = if (nombres.size == 1) nombres[0]
            else nombres.dropLast(1).joinToString(", ") + " y " + nombres.last()
        return "todos los $lista"
    }

    private fun numero(n: Int): String = when (n) {
        2 -> "dos"
        3 -> "tres"
        4 -> "cuatro"
        5 -> "cinco"
        6 -> "seis"
        7 -> "siete"
        8 -> "ocho"
        9 -> "nueve"
        10 -> "diez"
        else -> n.toString()
    }
}
