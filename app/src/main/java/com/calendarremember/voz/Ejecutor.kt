package com.calendarremember.voz

import com.calendarremember.datos.Evento
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Dónde viven los eventos. En la app es el Almacén; en las pruebas, una
 * lista en memoria. Así lo que hace cada orden se puede probar sin Android.
 */
interface Agenda {
    val eventos: List<Evento>
    fun guardar(evento: Evento)
    fun borrar(id: String)
    /** De una vez: una serie son decenas de eventos, y guardarlos uno a uno es lento. */
    fun guardarVarios(eventos: List<Evento>) = eventos.forEach { guardar(it) }
    fun borrarVarios(ids: List<String>) = ids.forEach { borrar(it) }
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
        val repite = leido.repeticion != Repeticion.NINGUNA
        val evento = Evento(
            titulo = leido.titulo,
            inicio = leido.inicio,
            todoElDia = leido.todoElDia,
            avisos = if (repite && !leido.avisosDichos) Series.avisos(leido.repeticion, leido.todoElDia)
                else leido.avisos,
            duracionMin = leido.duracionMin,
            dictado = leido.dictado,
            hasta = leido.hasta,
            intervalo = leido.intervalo,
        )

        if (repite) {
            // "Los martes y jueves": una cadena por día, todas de la misma
            // serie. Cada una empieza el primero de su día desde la primera vez.
            val dias = leido.diasSemana.ifEmpty { listOf(evento.inicio.dayOfWeek) }
            val serie = java.util.UUID.randomUUID().toString()
            val todas = dias.flatMap { d ->
                val primera = evento.copy(
                    inicio = evento.inicio.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(d))
                        .atTime(evento.inicio.toLocalTime()),
                )
                val cadena = if (leido.repeticion == Repeticion.SEMANAL) primera else evento
                Series.crear(cadena, leido.repeticion, ahora.toLocalDate(), serie)
            }.distinctBy { it.inicio }
            agenda.guardarVarios(todas)
            return Respuesta.Hecha(
                "Apuntado: ${evento.titulo}, ${Series.describir(evento, leido.repeticion, dias)}, " +
                    "empezando ${cuando(evento, ahora).substringBefore(" a las")}.",
                true,
            )
        }

        agenda.guardar(evento)
        if (leido.confianza == Confianza.BAJA) {
            return Respuesta.Hecha(
                "Apuntado sin título, ${cuando(evento, ahora)}. Revísalo.", false, revisar = evento,
            )
        }
        return Respuesta.Hecha("Apuntado: ${evento.titulo}, ${cuando(evento, ahora)}.", true)
    }

    /**
     * El evento del que se habla. Si hay un favorito claro, ese. Si empatan
     * varias repeticiones de una misma serie ("cancela la clase de yoga", y
     * hay una cada martes), se entiende la próxima: es de la que se habla.
     */
    private fun elegirUno(encontrados: List<Buscador.Candidato>, ahora: LocalDateTime): Evento? {
        Buscador.unico(encontrados)?.let { return it }
        val mejor = encontrados.firstOrNull()?.puntos ?: return null
        val empatados = encontrados.filter { it.puntos == mejor }.map { it.evento }
        val serie = empatados.first().serie ?: return null
        if (empatados.any { it.serie != serie }) return null
        return empatados.filter { !it.inicio.isBefore(ahora) }.minByOrNull { it.inicio }
            ?: empatados.maxBy { it.inicio }
    }

    // --- Cancelar ---------------------------------------------------------

    private fun cancelar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        // "Borra lo último que he apuntado": lo más nuevo, y si era algo que
        // se repite, entero, que es lo que se acaba de apuntar.
        if (leido.elUltimo) {
            val ultimo = agenda.eventos.maxByOrNull { it.creado }
                ?: return Respuesta.Hecha("No hay nada apuntado.", false)
            val serie = ultimo.serie
            if (serie != null) {
                val deLaSerie = agenda.eventos.filter { it.serie == serie }
                val primera = deLaSerie.minBy { it.inicio }
                val dias = Series.diasDe(primera, deLaSerie)
                agenda.borrarVarios(deLaSerie.map { it.id })
                return Respuesta.Hecha(
                    "Borrado: ${primera.titulo}, ${Series.describir(primera, primera.repeticion, dias)}.", true,
                )
            }
            return borrar(ultimo, agenda, ahora)
        }
        if (sinPistas(leido)) return Respuesta.Hecha("Dime qué cancelo.", false)

        // "Cancela todo lo de mañana": todo lo de ese día, que es lo que dice.
        if (leido.fechaDicha && Regex("""\btod[oa]s?\b""").containsMatchIn(sinTildes(leido.titulo))) {
            val dia = leido.inicio.toLocalDate()
            val delDia = agenda.eventos.filter { it.inicio.toLocalDate() == dia }
            if (delDia.isEmpty()) return Respuesta.Hecha("${etiquetaDia(dia, ahora)} no tienes nada.", false)
            agenda.borrarVarios(delDia.map { it.id })
            val cuantos = if (delDia.size == 1) "Borrado" else "Borrados ${delDia.size} eventos"
            return Respuesta.Hecha(
                "$cuantos de ${etiquetaDia(dia, ahora).replaceFirstChar { it.lowercase() }}: " +
                    enumerar(delDia.map { it.titulo }) + ".", true,
            )
        }

        val encontrados = buscar(leido, agenda, ahora)
        if (encontrados.isEmpty()) return noEncontrada(leido, "cancelar")

        // "Borra la clase de yoga de todos los martes", "cancela todas las
        // clases de yoga": la serie entera, no solo la próxima.
        val todas = leido.repeticion != Repeticion.NINGUNA ||
            Regex("""\b(?:todos|todas|siempre)\b""").containsMatchIn(sinTildes(leido.titulo))
        val serie = encontrados.first().evento.serie
        if (todas && serie != null) {
            val deLaSerie = agenda.eventos.filter { it.serie == serie }
            val primera = deLaSerie.minBy { it.inicio }
            val dias = Series.diasDe(primera, deLaSerie)
            agenda.borrarVarios(deLaSerie.map { it.id })
            return Respuesta.Hecha(
                "Borrado: ${primera.titulo}, ${Series.describir(primera, primera.repeticion, dias)}.", true,
            )
        }

        elegirUno(encontrados, ahora)?.let { evento ->
            val resto = if (evento.serie != null) " Las demás siguen." else ""
            agenda.borrar(evento.id)
            return Respuesta.Hecha("Borrado: ${evento.titulo}, ${cuando(evento, ahora)}.$resto", true)
        }
        return Respuesta.Elegir("¿Cuál borro?", encontrados.take(4).map { it.evento }, leido)
    }

    private fun borrar(evento: Evento, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        agenda.borrar(evento.id)
        return Respuesta.Hecha("Borrado: ${evento.titulo}, ${cuando(evento, ahora)}.", true)
    }

    // --- Cambiar ----------------------------------------------------------

    private fun cambiar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        if (leido.elUltimo) {
            val ultimo = agenda.eventos.maxByOrNull { it.creado }
                ?: return Respuesta.Hecha("No hay nada apuntado.", false)
            return mover(leido, ultimo, agenda, ahora)
        }
        if (sinPistas(leido)) return Respuesta.Hecha("Dime qué evento cambio.", false)
        val encontrados = buscar(leido, agenda, ahora)
        if (encontrados.isEmpty()) return noEncontrada(leido, "cambiar")
        elegirUno(encontrados, ahora)?.let { return mover(leido, it, agenda, ahora) }
        return Respuesta.Elegir("¿Cuál cambio?", encontrados.take(4).map { it.evento }, leido)
    }

    /**
     * Lo que no se dijo, no se toca: "al sábado" conserva la hora, "a las
     * diez" conserva el día. Un evento de todo el día que recibe una hora pasa
     * a tener hora, con los avisos de un evento con hora.
     */
    private fun mover(leido: Interpretacion, evento: Evento, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        // Alargar o acortar: cambia dónde acaba, no dónde empieza.
        if (leido.nuevoHasta != null || leido.alargarMin != null) return alargar(leido, evento, agenda, ahora)

        val desplazamiento = leido.desplazamientoMin
        val movido = if (desplazamiento != null) {
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
        // Lo que dura varios días se mueve entero: el viaje del 27 al 3
        // pasado al 28 acaba el 4.
        val dias = ChronoUnit.DAYS.between(evento.inicio.toLocalDate(), movido.inicio.toLocalDate())
        val nuevo = if (evento.variosDias) movido.copy(hasta = evento.hasta!!.plusDays(dias)) else movido
        agenda.guardar(nuevo)
        return Respuesta.Hecha("Cambiado: ${nuevo.titulo}, ahora ${cuando(nuevo, ahora)}.", true)
    }

    private fun alargar(leido: Interpretacion, evento: Evento, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        val inicio = evento.inicio.toLocalDate()
        val minutos = leido.alargarMin
        val hastaDicho = leido.nuevoHasta
        val nuevo = when {
            hastaDicho != null -> {
                if (hastaDicho.isBefore(inicio)) {
                    return Respuesta.Hecha("${evento.titulo} empieza ${cuando(evento, ahora)}: no puede acabar antes.", false)
                }
                evento.copy(hasta = hastaDicho.takeIf { it.isAfter(inicio) })
            }
            // Días enteros: se mueve el último día.
            minutos != null && minutos % 1440 == 0L -> {
                val hasta = evento.ultimoDia.plusDays(minutos / 1440)
                evento.copy(hasta = if (hasta.isAfter(inicio)) hasta else null)
            }
            minutos != null -> {
                if (evento.todoElDia) {
                    return Respuesta.Hecha("${evento.titulo} es de todo el día: no tiene hora de acabar.", false)
                }
                val dura = ((evento.duracionMin ?: 60) + minutos).coerceAtLeast(5)
                val alargado = evento.copy(duracionMin = dura.toInt())
                agenda.guardar(alargado)
                val fin = alargado.inicio.plusMinutes(dura)
                return Respuesta.Hecha("Cambiado: ${alargado.titulo}, ahora hasta las ${fin.format(HORA)}.", true)
            }
            else -> evento
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
                val encontrados = Buscador.candidatos(leido.titulo, leido.desde, agenda.eventos, ahora)
                if (encontrados.isEmpty()) {
                    return Respuesta.Hecha("No encuentro nada parecido a «${leido.titulo}».", false)
                }
                // Algo que se repite: se contesta con la próxima vez.
                elegirUno(encontrados, ahora)?.takeIf { it.serie != null }?.let { e ->
                    val dias = Series.diasDe(e, agenda.eventos)
                    return Respuesta.Hecha(
                        "${e.titulo} es ${Series.describir(e, e.repeticion, dias)}. La próxima, ${cuando(e, ahora)}.", true,
                    )
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
                // Lo que toca alguno de esos días, aunque empiece antes: el
                // viaje que empezó ayer también es de hoy.
                val enRango = agenda.eventos
                    .filter { !it.inicio.toLocalDate().isAfter(hasta) && !it.ultimoDia.isBefore(desde) }
                    // Hoy solo cuenta lo que aún no ha pasado: lo de esta
                    // mañana ya no es "lo que tengo".
                    .filter {
                        it.todoElDia || it.variosDias || it.inicio.toLocalDate() != ahora.toLocalDate() ||
                            it.inicio.isAfter(ahora)
                    }
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
                    if (unDia && e.variosDias) {
                        // En un día suelto, de lo que dura varios basta con
                        // decir hasta cuándo sigue.
                        val empieza = e.inicio.toLocalDate() == desde
                        val momento = if (empieza && !e.todoElDia) "a las ${e.inicio.format(HORA)}" else "todo el día"
                        val sigue = if (e.ultimoDia == desde) "último día"
                            else "hasta " + etiquetaDia(e.ultimoDia, ahora).replaceFirstChar { it.lowercase() }
                        "$momento, ${e.titulo}, $sigue"
                    } else {
                        val momento = if (unDia) {
                            if (e.todoElDia) "todo el día" else "a las ${e.inicio.format(HORA)}"
                        } else cuando(e, ahora)
                        "$momento, ${e.titulo}"
                    }
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

    /**
     * "hoy a las 17:30", "mañana", "el sábado 19 a las 21:00", "el martes 3
     * de noviembre", y lo que dura varios días: "del domingo 27 de septiembre
     * al domingo 25 de octubre", "desde mañana hasta el domingo 20".
     */
    fun cuando(evento: Evento, ahora: LocalDateTime): String {
        val hora = if (evento.todoElDia) "" else " a las ${evento.inicio.format(HORA)}"
        if (evento.variosDias) return tramo(evento.inicio.toLocalDate(), evento.ultimoDia, ahora, hora)
        val dia = etiquetaDia(evento.inicio.toLocalDate(), ahora).replaceFirstChar { it.lowercase() }
        return "$dia$hora"
    }

    private fun tramo(primero: LocalDate, ultimo: LocalDate, ahora: LocalDateTime, hora: String): String {
        val hoy = ahora.toLocalDate()
        // El mes, cuando hace falta: si queda lejos o ya pasó.
        val lejos = ultimo.isAfter(hoy.plusDays(6)) || primero.isBefore(hoy)
        val conMes = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
        val sinMes = DateTimeFormatter.ofPattern("EEEE d", ES)
        val fin = ultimo.format(if (lejos) conMes else sinMes)
        return when (primero) {
            hoy -> "desde hoy$hora hasta el $fin"
            hoy.plusDays(1) -> "desde mañana$hora hasta el $fin"
            else -> {
                // "del domingo 27 al martes 29 de septiembre": el mes una
                // vez, salvo que las puntas caigan en meses distintos.
                val mismoMes = primero.month == ultimo.month && primero.year == ultimo.year
                val ini = primero.format(if (lejos && !mismoMes) conMes else sinMes)
                "del $ini$hora al $fin"
            }
        }
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
