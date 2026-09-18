package com.calendarremember.voz

import com.calendarremember.datos.Evento
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dónde viven los eventos. En la app es el Almacén; en las pruebas, una
 * lista en memoria. Así lo que hace cada orden se puede probar sin Android.
 */
interface Agenda {
    val eventos: List<Evento>
    fun guardar(evento: Evento)
    fun borrar(id: String)
}

/** Lo que pasó al atender una orden, y qué decirle al usuario. */
sealed interface Respuesta {
    /** Lo que se enseña en pantalla y, si toca, se dice en voz alta. */
    val mensaje: String

    /** Hecho o contestado. [revisar]: el evento que conviene abrir para corregir. */
    data class Hecha(
        override val mensaje: String,
        val bien: Boolean,
        val revisar: Evento? = null,
    ) : Respuesta

    /** Varios eventos encajan por igual: hay que decir cuál. */
    data class Elegir(
        override val mensaje: String,
        val candidatos: List<Evento>,
        val leido: Interpretacion,
    ) : Respuesta

    /** Sonaba a una orden sobre un evento, pero no encaja ninguno. */
    data class NoEncontrada(
        override val mensaje: String,
        val leido: Interpretacion,
    ) : Respuesta
}

/**
 * Hace lo que pide una frase ya interpretada: apuntar, cancelar, cambiar o
 * contestar. No pide confirmación: si está claro de qué evento se habla, lo
 * hace y lo dice. Solo pregunta cuál cuando dos encajan por igual, porque
 * ahí no hay manera de saberlo.
 */
object Ejecutor {

    private val ES = Locale("es", "ES")
    private val HORA = DateTimeFormatter.ofPattern("HH:mm")

    fun ejecutar(
        leido: Interpretacion,
        agenda: Agenda,
        ahora: LocalDateTime = LocalDateTime.now(),
    ): Respuesta = when (leido.accion) {
        Accion.CREAR -> apuntar(leido, agenda, ahora)
        Accion.BORRAR -> cancelar(leido, agenda, ahora)
        Accion.MOVER -> cambiar(leido, agenda, ahora)
        Accion.CONSULTAR -> contestar(leido, agenda, ahora)
    }

    /** Aplica la orden al evento elegido cuando había varios candidatos. */
    fun aplicar(
        leido: Interpretacion,
        evento: Evento,
        agenda: Agenda,
        ahora: LocalDateTime = LocalDateTime.now(),
    ): Respuesta = when (leido.accion) {
        Accion.BORRAR -> borrar(evento, agenda, ahora)
        Accion.MOVER -> mover(leido, evento, agenda, ahora)
        else -> Respuesta.Hecha("${evento.titulo}, ${cuando(evento, ahora)}.", true)
    }

    // --- Apuntar ----------------------------------------------------------

    private fun apuntar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        val evento = Evento(
            titulo = leido.titulo,
            inicio = leido.inicio,
            todoElDia = leido.todoElDia,
            avisos = leido.avisos,
            duracionMin = leido.duracionMin,
            dictado = leido.dictado,
        )
        agenda.guardar(evento)
        if (leido.confianza == Confianza.BAJA) {
            return Respuesta.Hecha(
                "Apuntado sin título, ${cuando(evento, ahora)}. Revísalo.", false, revisar = evento,
            )
        }
        // Los eventos que se repiten aún no existen: mejor decirlo que dejar
        // creer que el martes que viene también sonará.
        val repeticion = if (leido.repeticion != Repeticion.NINGUNA)
            " Solo esta vez: todavía no repito eventos." else ""
        return Respuesta.Hecha("Apuntado: ${evento.titulo}, ${cuando(evento, ahora)}.$repeticion", true)
    }

    // --- Cancelar ---------------------------------------------------------

    private fun cancelar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        if (sinPistas(leido)) return Respuesta.Hecha("Dime qué cancelo.", false)

        // "Cancela todo lo de mañana": todo lo de ese día, que es lo que dice.
        if (leido.fechaDicha && Regex("""\btod[oa]s?\b""").containsMatchIn(sinTildes(leido.titulo))) {
            val dia = leido.inicio.toLocalDate()
            val delDia = agenda.eventos.filter { it.inicio.toLocalDate() == dia }
            if (delDia.isEmpty()) return Respuesta.Hecha("${etiquetaDia(dia, ahora)} no tienes nada.", false)
            delDia.forEach { agenda.borrar(it.id) }
            val cuantos = if (delDia.size == 1) "Borrado" else "Borrados ${delDia.size} eventos"
            return Respuesta.Hecha(
                "$cuantos de ${etiquetaDia(dia, ahora).replaceFirstChar { it.lowercase() }}: " +
                    enumerar(delDia.map { it.titulo }) + ".", true,
            )
        }

        val encontrados = buscar(leido, agenda, ahora)
        if (encontrados.isEmpty()) return noEncontrada(leido, "cancelar")
        Buscador.unico(encontrados)?.let { return borrar(it, agenda, ahora) }
        return Respuesta.Elegir("¿Cuál borro?", encontrados.take(4).map { it.evento }, leido)
    }

    private fun borrar(evento: Evento, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        agenda.borrar(evento.id)
        return Respuesta.Hecha("Borrado: ${evento.titulo}, ${cuando(evento, ahora)}.", true)
    }

    // --- Cambiar ----------------------------------------------------------

    private fun cambiar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        if (sinPistas(leido)) return Respuesta.Hecha("Dime qué evento cambio.", false)
        val encontrados = buscar(leido, agenda, ahora)
        if (encontrados.isEmpty()) return noEncontrada(leido, "cambiar")
        Buscador.unico(encontrados)?.let { return mover(leido, it, agenda, ahora) }
        return Respuesta.Elegir("¿Cuál cambio?", encontrados.take(4).map { it.evento }, leido)
    }

    /**
     * Lo que no se dijo, no se toca: "al sábado" conserva la hora, "a las
     * diez" conserva el día. Un evento de todo el día que recibe una hora pasa
     * a tener hora, con los avisos de un evento con hora.
     */
    private fun mover(leido: Interpretacion, evento: Evento, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        val desplazamiento = leido.desplazamientoMin
        val nuevo = if (desplazamiento != null) {
            if (evento.todoElDia && desplazamiento % 1440 != 0L) {
                return Respuesta.Hecha(
                    "${evento.titulo} es de todo el día: dime a qué hora lo pongo.", false,
                )
            }
            evento.copy(inicio = evento.inicio.plusMinutes(desplazamiento))
        } else {
            val fecha = leido.nuevaFecha ?: evento.inicio.toLocalDate()
            val hora = leido.nuevaHora ?: if (evento.todoElDia) null else evento.inicio.toLocalTime()
            evento.copy(
                inicio = fecha.atTime(hora ?: LocalTime.MIDNIGHT),
                todoElDia = hora == null,
                avisos = if (evento.todoElDia && hora != null) listOf(1440, 60, 0) else evento.avisos,
            )
        }
        agenda.guardar(nuevo)
        return Respuesta.Hecha("Cambiado: ${nuevo.titulo}, ahora ${cuando(nuevo, ahora)}.", true)
    }

    // --- Contestar --------------------------------------------------------

    private fun contestar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        val futuros = agenda.eventos
            .filter { if (it.todoElDia) !it.inicio.toLocalDate().isBefore(ahora.toLocalDate()) else it.inicio.isAfter(ahora) }
            .sortedBy { it.inicio }

        return when (leido.consulta) {
            Consulta.PROXIMO -> {
                val siguiente = futuros.firstOrNull()
                    ?: return Respuesta.Hecha("No tienes nada a la vista.", true)
                Respuesta.Hecha("Lo próximo es ${siguiente.titulo}, ${cuando(siguiente, ahora)}.", true)
            }

            Consulta.CUANDO -> {
                val encontrados = Buscador.candidatos(leido.titulo, null, agenda.eventos, ahora)
                if (encontrados.isEmpty()) {
                    return Respuesta.Hecha("No encuentro nada parecido a «${leido.titulo}».", false)
                }
                val mejor = encontrados.first().puntos
                val empatados = encontrados.filter { it.puntos == mejor }.map { it.evento }.sortedBy { it.inicio }
                if (empatados.size == 1) {
                    val e = empatados.first()
                    Respuesta.Hecha("${e.titulo} es ${cuando(e, ahora)}.", true)
                } else {
                    Respuesta.Hecha(
                        "Tienes ${cuantos(empatados.size)}: " +
                            empatados.take(3).joinToString("; ") { "${it.titulo}, ${cuando(it, ahora)}" } + ".",
                        true,
                    )
                }
            }

            else -> {
                val desde = leido.desde ?: ahora.toLocalDate()
                val hasta = leido.hasta ?: desde
                val enRango = agenda.eventos
                    .filter { !it.inicio.toLocalDate().isBefore(desde) && !it.inicio.toLocalDate().isAfter(hasta) }
                    // Hoy solo cuenta lo que aún no ha pasado: lo de esta
                    // mañana ya no es "lo que tengo".
                    .filter { it.todoElDia || it.inicio.toLocalDate() != ahora.toLocalDate() || it.inicio.isAfter(ahora) }
                    .sortedBy { it.inicio }
                val unDia = desde == hasta
                val cuandoRango = if (unDia) etiquetaDia(desde, ahora) else etiquetaRango(desde, hasta, ahora)

                if (enRango.isEmpty()) {
                    val despues = if (!leido.fechaDicha) futuros.firstOrNull()?.let {
                        " Lo próximo es ${it.titulo}, ${cuando(it, ahora)}."
                    } ?: "" else ""
                    return Respuesta.Hecha("$cuandoRango no tienes nada.$despues", true)
                }
                val lista = enRango.take(6).joinToString("; ") { e ->
                    val momento = if (unDia) {
                        if (e.todoElDia) "todo el día" else "a las ${e.inicio.format(HORA)}"
                    } else cuando(e, ahora)
                    "$momento, ${e.titulo}"
                }
                val resto = if (enRango.size > 6) "; y ${enRango.size - 6} más" else ""
                Respuesta.Hecha("$cuandoRango tienes ${cuantos(enRango.size)}: $lista$resto.", true)
            }
        }
    }

    // --- Utilidades --------------------------------------------------------

    private fun sinPistas(leido: Interpretacion) =
        leido.titulo.isBlank() && !leido.fechaDicha && !leido.horaDicha

    private fun buscar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime) =
        Buscador.candidatos(
            criterio = leido.titulo,
            fecha = if (leido.fechaDicha) leido.inicio.toLocalDate() else null,
            eventos = agenda.eventos,
            ahora = ahora,
            hora = if (leido.horaDicha) leido.inicio.toLocalTime() else null,
        )

    private fun noEncontrada(leido: Interpretacion, verbo: String) = Respuesta.NoEncontrada(
        if (leido.titulo.isNotBlank()) "No encuentro nada parecido a «${leido.titulo}» para $verbo."
        else "No encuentro nada que $verbo ese día.",
        leido,
    )

    /** "hoy a las 17:30", "mañana", "el sábado 19 a las 21:00", "el martes 3 de noviembre". */
    fun cuando(evento: Evento, ahora: LocalDateTime): String {
        val dia = etiquetaDia(evento.inicio.toLocalDate(), ahora).replaceFirstChar { it.lowercase() }
        return if (evento.todoElDia) dia else "$dia a las ${evento.inicio.format(HORA)}"
    }

    /** "Hoy", "Mañana", "El viernes 18", "El martes 3 de noviembre". */
    private fun etiquetaDia(dia: LocalDate, ahora: LocalDateTime): String {
        val hoy = ahora.toLocalDate()
        return when (dia) {
            hoy -> "Hoy"
            hoy.plusDays(1) -> "Mañana"
            hoy.plusDays(2) -> "Pasado mañana"
            else -> {
                // Pasada una semana, el día de la semana solo ya no basta.
                val patron = if (dia.isAfter(hoy.plusDays(6)) || dia.isBefore(hoy)) "EEEE d 'de' MMMM" else "EEEE d"
                "El " + dia.format(DateTimeFormatter.ofPattern(patron, ES))
            }
        }
    }

    private fun etiquetaRango(desde: LocalDate, hasta: LocalDate, ahora: LocalDateTime): String {
        val hoy = ahora.toLocalDate()
        val lunesQueViene = hoy.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        return when {
            desde == hoy && hasta.dayOfWeek == java.time.DayOfWeek.SUNDAY && hasta.isBefore(lunesQueViene) -> "Esta semana"
            desde == lunesQueViene && hasta == lunesQueViene.plusDays(6) -> "La semana que viene"
            desde == lunesQueViene.plusDays(5) && hasta == desde.plusDays(1) -> "El fin de semana que viene"
            hasta.dayOfWeek == java.time.DayOfWeek.SUNDAY && hasta.isBefore(lunesQueViene) &&
                (desde.dayOfWeek == java.time.DayOfWeek.SATURDAY || desde == hoy) -> "Este fin de semana"
            else -> "Del " + desde.format(DateTimeFormatter.ofPattern("d", ES)) + " al " +
                hasta.format(DateTimeFormatter.ofPattern("d 'de' MMMM", ES))
        }
    }

    private fun cuantos(n: Int) = when (n) {
        1 -> "una cosa"
        2 -> "dos cosas"
        3 -> "tres cosas"
        4 -> "cuatro cosas"
        5 -> "cinco cosas"
        else -> "$n cosas"
    }

    private fun enumerar(titulos: List<String>): String = when (titulos.size) {
        0 -> ""
        1 -> titulos[0]
        else -> titulos.dropLast(1).joinToString(", ") + " y " + titulos.last()
    }

    private fun sinTildes(texto: String): String {
        val con = "áéíóúüñ"
        val sin = "aeiouun"
        return texto.lowercase().map { c -> con.indexOf(c).let { if (it >= 0) sin[it] else c } }.joinToString("")
    }
}
