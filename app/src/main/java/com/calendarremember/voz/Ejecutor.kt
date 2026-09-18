package com.calendarremember.voz

import com.calendarremember.datos.ColorEvento
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

    /**
     * El evento del que se acaba de hablar: el que se apuntó, se cambió o se
     * preguntó hace un momento. Es lo que da sentido a "cancélalo" o
     * "cámbiale el nombre" sin decir cuál. Caduca a los pocos minutos.
     */
    val enContexto: Evento? get() = null
    fun ponerEnContexto(evento: Evento?) {}
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

    /** Varios eventos encajan por igual: hay que decir cuál. Se contesta de viva voz. */
    data class Elegir(
        override val mensaje: String,
        val candidatos: List<Evento>,
        val leido: Interpretacion,
    ) : Respuesta

    /** Sonaba a una orden sobre un evento, pero no encaja ninguno. "¿Lo apunto?" */
    data class NoEncontrada(
        override val mensaje: String,
        val leido: Interpretacion,
    ) : Respuesta

    /** Falta algo para poder hacerlo: "¿Qué apunto para mañana?". Se contesta de viva voz. */
    data class Preguntar(
        override val mensaje: String,
        val leido: Interpretacion,
    ) : Respuesta
}

/**
 * Hace lo que pide una frase ya interpretada: apuntar, cancelar, cambiar,
 * editar o contestar. No pide confirmación: si está claro de qué evento se
 * habla, lo hace y lo dice. Pregunta solo cuando falta algo que no se puede
 * suponer (qué apuntar, cuál de dos), y la respuesta se da hablando.
 *
 * Lleva la cuenta del evento del que se está hablando, como en una
 * conversación: "apunta la cena del sábado" y luego "cámbiale el nombre a
 * cena con Ana" hablan del mismo.
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
        Accion.EDITAR -> editar(leido, agenda, ahora)
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
        Accion.EDITAR -> modificar(leido, evento, agenda, ahora)
        else -> Respuesta.Hecha("${evento.titulo}, ${cuando(evento, ahora)}.", true).also {
            agenda.ponerEnContexto(evento)
        }
    }

    // --- La conversación ----------------------------------------------------

    /**
     * La respuesta a una pregunta de Nébula: el nombre que faltaba, cuál de
     * los candidatos, o si se apunta lo que no se encontró. [texto] vacío:
     * no se contestó.
     */
    fun responder(
        pregunta: Respuesta,
        texto: String,
        agenda: Agenda,
        ahora: LocalDateTime = LocalDateTime.now(),
    ): Respuesta {
        val dicho = texto.trim()
        return when (pregunta) {
            is Respuesta.Preguntar -> {
                if (NEGATIVO.containsMatchIn(normal(dicho))) return Respuesta.Hecha("Vale, no apunto nada.", true)
                // Sin respuesta: se apunta igual, para no perder la fecha, y se
                // dice cómo ponerle nombre luego.
                if (dicho.isBlank()) {
                    val r = apuntar(pregunta.leido.copy(confianza = Confianza.ALTA), agenda, ahora)
                    return if (r is Respuesta.Hecha) Respuesta.Hecha(
                        r.mensaje.removeSuffix(".") + ". Dime «cámbiale el nombre» cuando quieras.", true,
                    ) else r
                }
                val r = Interprete.interpretar(dicho, ahora, soloCrear = true)
                val titulo = r.titulo.takeIf { it != "Recordatorio" && it.isNotBlank() }
                    ?: dicho.replaceFirstChar { it.uppercase() }
                val base = pregunta.leido
                // Si en la respuesta va también el cuándo, manda la respuesta.
                val inicio = when {
                    r.fechaDicha && r.horaDicha -> r.inicio
                    r.fechaDicha -> r.inicio.toLocalDate().atTime(if (base.horaDicha) base.inicio.toLocalTime() else LocalTime.MIDNIGHT)
                    r.horaDicha -> (if (base.fechaDicha) base.inicio.toLocalDate() else r.inicio.toLocalDate()).atTime(r.inicio.toLocalTime())
                    else -> base.inicio
                }
                val conHora = if (r.horaDicha) true else if (r.fechaDicha) base.horaDicha else !base.todoElDia
                apuntar(
                    base.copy(
                        titulo = titulo, inicio = inicio, todoElDia = !conHora, confianza = Confianza.ALTA,
                        fechaDicha = base.fechaDicha || r.fechaDicha, horaDicha = conHora,
                        avisos = if (r.avisosDichos) r.avisos else if (conHora == !base.todoElDia) base.avisos
                            else if (conHora) listOf(1440, 60, 0) else listOf(1440, 0),
                        hasta = r.hasta ?: base.hasta,
                        repeticion = if (r.repeticion != Repeticion.NINGUNA) r.repeticion else base.repeticion,
                    ),
                    agenda, ahora,
                )
            }

            is Respuesta.Elegir -> {
                if (dicho.isBlank() || NEGATIVO.containsMatchIn(normal(dicho))) {
                    return Respuesta.Hecha("Vale, no toco nada.", true)
                }
                val elegido = cualDice(dicho, pregunta.candidatos, ahora)
                    ?: return Respuesta.Elegir("No sé cuál. ${pregunta.mensaje}", pregunta.candidatos, pregunta.leido)
                aplicar(pregunta.leido, elegido, agenda, ahora)
            }

            is Respuesta.NoEncontrada -> when {
                dicho.isBlank() || NEGATIVO.containsMatchIn(normal(dicho)) -> Respuesta.Hecha("Vale.", true)
                AFIRMATIVO.containsMatchIn(normal(dicho)) ->
                    ejecutar(Interprete.interpretar(pregunta.leido.dictado, ahora, soloCrear = true), agenda, ahora)
                // Otra cosa: una orden nueva.
                else -> ejecutar(Interprete.interpretar(dicho, ahora), agenda, ahora)
            }

            is Respuesta.Hecha ->
                if (dicho.isBlank() || NEGATIVO.containsMatchIn(normal(dicho))) Respuesta.Hecha("Vale.", true)
                else ejecutar(Interprete.interpretar(dicho, ahora), agenda, ahora)
        }
    }

    private val AFIRMATIVO = Regex("""^(?:si|vale|venga|claro|ok|okey|de\s+acuerdo|apuntalo|apuntala|hazlo|eso|""" +
        """correcto|exacto|por\s+favor|dale|adelante|perfecto)\b""")
    private val NEGATIVO = Regex("""^(?:no|nada|dejalo|deja|olvidalo|olvidate|cancela|cancelalo|ninguno|ninguna|""" +
        """da\s+igual|nada\s+nada|mejor\s+no|para|basta)\b""")

    /** "El primero", "el de las diez", "la de Marta", "el del sábado". */
    private fun cualDice(texto: String, candidatos: List<Evento>, ahora: LocalDateTime): Evento? {
        val n = normal(texto)
        val orden = Regex("""\b(primer[oa]?|segund[oa]|tercer[oa]?|cuart[oa]|ultim[oa]|uno|dos|tres|cuatro|1|2|3|4)\b""")
            .find(n)?.groupValues?.get(1)
        val porOrden = when {
            orden == null -> null
            orden.startsWith("primer") || orden == "uno" || orden == "1" -> 0
            orden.startsWith("segund") || orden == "dos" || orden == "2" -> 1
            orden.startsWith("tercer") || orden == "tres" || orden == "3" -> 2
            orden.startsWith("cuart") || orden == "cuatro" || orden == "4" -> 3
            orden.startsWith("ultim") -> candidatos.lastIndex
            else -> null
        }
        // Un número solo es un orden si no es una hora ("el de las dos").
        if (porOrden != null && !Regex("""\blas?\s+(?:uno|una|dos|tres|cuatro|\d)""").containsMatchIn(n)) {
            candidatos.getOrNull(porOrden)?.let { return it }
        }
        val r = Interprete.interpretar(texto, ahora, soloCrear = true)
        val encontrados = Buscador.candidatos(
            criterio = r.titulo.takeIf { it != "Recordatorio" } ?: "",
            fecha = if (r.fechaDicha) r.inicio.toLocalDate() else null,
            eventos = candidatos, ahora = ahora,
            hora = if (r.horaDicha) r.inicio.toLocalTime() else null,
        )
        return Buscador.unico(encontrados) ?: encontrados.firstOrNull()?.takeIf { encontrados.size == 1 }?.evento
    }

    // --- Apuntar ----------------------------------------------------------

    private fun apuntar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        // Sin nombre no se apunta a ciegas: se pregunta, y se contesta hablando.
        if (leido.confianza == Confianza.BAJA) {
            val cuando = if (leido.fechaDicha || leido.horaDicha) " para " + cuando(borrador(leido), ahora) else ""
            return Respuesta.Preguntar("¿Qué apunto$cuando?", leido)
        }

        val repite = leido.repeticion != Repeticion.NINGUNA
        val evento = borrador(leido)

        // Ya estaba: el mismo nombre el mismo día y a la misma hora. Se dice
        // en vez de apuntarlo dos veces.
        if (!repite) {
            agenda.eventos.firstOrNull { e ->
                normal(e.titulo) == normal(evento.titulo) && e.inicio.toLocalDate() == evento.inicio.toLocalDate() &&
                    (e.todoElDia == evento.todoElDia) && (e.todoElDia || e.inicio.toLocalTime() == evento.inicio.toLocalTime())
            }?.let { ya ->
                agenda.ponerEnContexto(ya)
                return Respuesta.Hecha("Ya lo tenías apuntado: ${ya.titulo}, ${cuando(ya, ahora)}.", true)
            }
        }

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
            agenda.ponerEnContexto(todas.minByOrNull { it.inicio })
            return Respuesta.Hecha(
                "Apuntado: ${evento.titulo}, ${Series.describir(evento, leido.repeticion, dias)}, " +
                    "empezando ${cuando(evento, ahora).substringBefore(" a las")}.",
                true,
            )
        }

        agenda.guardar(evento)
        agenda.ponerEnContexto(evento)
        return Respuesta.Hecha("Apuntado: ${evento.titulo}, ${cuando(evento, ahora)}.", true)
    }

    private fun borrador(leido: Interpretacion): Evento {
        val repite = leido.repeticion != Repeticion.NINGUNA
        return Evento(
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
    }

    /**
     * El evento del que se habla. Si hay un favorito claro, ese. Si empatan,
     * y uno es el del que se venía hablando, ese. Si empatan varias
     * repeticiones de una misma serie ("cancela la clase de yoga", y hay una
     * cada martes), la próxima: es de la que se habla.
     */
    private fun elegirUno(encontrados: List<Buscador.Candidato>, ahora: LocalDateTime, agenda: Agenda? = null): Evento? {
        Buscador.unico(encontrados)?.let { return it }
        val mejor = encontrados.firstOrNull()?.puntos ?: return null
        val empatados = encontrados.filter { it.puntos == mejor }.map { it.evento }
        agenda?.enContexto?.let { c -> empatados.firstOrNull { it.id == c.id }?.let { return it } }
        val serie = empatados.first().serie ?: return null
        if (empatados.any { it.serie != serie }) return null
        return empatados.filter { !it.inicio.isBefore(ahora) }.minByOrNull { it.inicio }
            ?: empatados.maxBy { it.inicio }
    }

    /**
     * Sin nada que lo identifique ("cancélalo", "cámbiale el nombre a...",
     * "el evento", "eso"), se habla del evento del contexto.
     */
    private fun sinCriterio(leido: Interpretacion) =
        Buscador.palabras(leido.titulo).isEmpty() && !leido.fechaDicha && !leido.horaDicha

    // --- Cancelar ---------------------------------------------------------

    private fun cancelar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        // "Borra lo último que he apuntado": lo más nuevo, y si era algo que
        // se repite, entero, que es lo que se acaba de apuntar.
        if (leido.elUltimo) {
            val ultimo = agenda.eventos.maxByOrNull { it.creado }
                ?: return Respuesta.Hecha("No hay nada apuntado.", false)
            return borrarConSerie(ultimo, agenda, ahora)
        }
        if (sinCriterio(leido)) {
            val c = agenda.enContexto ?: return Respuesta.Hecha("Dime qué cancelo.", false)
            return borrar(c, agenda, ahora)
        }

        // "Cancela todo lo de mañana": todo lo de ese día, que es lo que dice.
        if (leido.fechaDicha && Regex("""\btod[oa]s?\b""").containsMatchIn(sinTildes(leido.titulo))) {
            val dia = leido.inicio.toLocalDate()
            val delDia = agenda.eventos.filter { it.inicio.toLocalDate() == dia }
            if (delDia.isEmpty()) return Respuesta.Hecha("${etiquetaDia(dia, ahora)} no tienes nada.", false)
            agenda.borrarVarios(delDia.map { it.id })
            agenda.ponerEnContexto(null)
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
        if (todas && encontrados.first().evento.serie != null) {
            return borrarConSerie(encontrados.first().evento, agenda, ahora)
        }

        elegirUno(encontrados, ahora, agenda)?.let { evento ->
            val resto = if (evento.serie != null) " Las demás siguen." else ""
            agenda.borrar(evento.id)
            agenda.ponerEnContexto(null)
            return Respuesta.Hecha("Borrado: ${evento.titulo}, ${cuando(evento, ahora)}.$resto", true)
        }
        return Respuesta.Elegir("¿Cuál borro?", encontrados.take(4).map { it.evento }, leido)
    }

    private fun borrarConSerie(evento: Evento, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        val serie = evento.serie ?: return borrar(evento, agenda, ahora)
        val deLaSerie = agenda.eventos.filter { it.serie == serie }
        val primera = deLaSerie.minBy { it.inicio }
        val dias = Series.diasDe(primera, deLaSerie)
        agenda.borrarVarios(deLaSerie.map { it.id })
        agenda.ponerEnContexto(null)
        return Respuesta.Hecha(
            "Borrado: ${primera.titulo}, ${Series.describir(primera, primera.repeticion, dias)}.", true,
        )
    }

    private fun borrar(evento: Evento, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        agenda.borrar(evento.id)
        agenda.ponerEnContexto(null)
        return Respuesta.Hecha("Borrado: ${evento.titulo}, ${cuando(evento, ahora)}.", true)
    }

    // --- Cambiar ----------------------------------------------------------

    /** El evento del que habla una orden de cambiar o editar, o por qué no se sabe. */
    private fun objetivo(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime, verbo: String): Any {
        if (leido.elUltimo) {
            return agenda.eventos.maxByOrNull { it.creado } ?: Respuesta.Hecha("No hay nada apuntado.", false)
        }
        if (sinCriterio(leido)) {
            return agenda.enContexto ?: Respuesta.Hecha("Dime qué evento $verbo.", false)
        }
        val encontrados = buscar(leido, agenda, ahora)
        if (encontrados.isEmpty()) return noEncontrada(leido, verbo)
        return elegirUno(encontrados, ahora, agenda)
            ?: Respuesta.Elegir("¿Cuál $verbo?", encontrados.take(4).map { it.evento }, leido)
    }

    private fun cambiar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta =
        when (val o = objetivo(leido, agenda, ahora, "cambio")) {
            is Evento -> mover(leido, o, agenda, ahora)
            else -> o as Respuesta
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
        agenda.ponerEnContexto(nuevo)
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
                agenda.ponerEnContexto(alargado)
                val fin = alargado.inicio.plusMinutes(dura)
                return Respuesta.Hecha("Cambiado: ${alargado.titulo}, ahora hasta las ${fin.format(HORA)}.", true)
            }
            else -> evento
        }
        agenda.guardar(nuevo)
        agenda.ponerEnContexto(nuevo)
        return Respuesta.Hecha("Cambiado: ${nuevo.titulo}, ahora ${cuando(nuevo, ahora)}.", true)
    }

    // --- Editar ------------------------------------------------------------

    private fun editar(leido: Interpretacion, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        // "No es cena, es comida": el evento es el que tenga ese trozo en el
        // nombre, y si no se sabe, el del que se venía hablando.
        leido.reemplazo?.let { (viejo, _) ->
            val porNombre = Buscador.unico(Buscador.candidatos(viejo, null, agenda.eventos, ahora))
            val contexto = agenda.enContexto
            val evento = contexto?.takeIf { normal(it.titulo).contains(normal(viejo)) }
                ?: porNombre ?: contexto
                ?: return Respuesta.Hecha("Dime qué evento corrijo.", false)
            return modificar(leido, evento, agenda, ahora)
        }

        // "Cambia el nombre de la visita a la abuela a comida": de todas las
        // maneras de partirlo, la que encaja con un evento que existe.
        if (leido.particiones.size > 1) {
            val mejor = leido.particiones.map { (criterio, nuevo) ->
                val l = Interprete.interpretar(criterio, ahora, soloCrear = true)
                val encontrados = Buscador.candidatos(
                    l.titulo.takeIf { it != "Recordatorio" } ?: "",
                    if (l.fechaDicha) l.inicio.toLocalDate() else null, agenda.eventos, ahora,
                    if (l.horaDicha) l.inicio.toLocalTime() else null,
                )
                Triple(encontrados, nuevo, encontrados.firstOrNull()?.puntos ?: 0)
            }.maxByOrNull { it.third }
            if (mejor != null && mejor.third > 0) {
                val evento = elegirUno(mejor.first, ahora, agenda)
                    ?: return Respuesta.Elegir("¿Cuál cambio?", mejor.first.take(4).map { it.evento },
                        leido.copy(nuevoTitulo = mejor.second))
                return modificar(leido.copy(nuevoTitulo = mejor.second), evento, agenda, ahora)
            }
        }

        return when (val o = objetivo(leido, agenda, ahora, "cambio")) {
            is Evento -> modificar(leido, o, agenda, ahora)
            is Respuesta.NoEncontrada -> {
                // "Avísame dos días antes del cumple de Ana el 14": si no
                // existe pero se dijo el día, es para apuntarlo así.
                if (leido.nuevosAvisos != null && leido.fechaDicha && leido.titulo.isNotBlank()) {
                    apuntar(leido.copy(accion = Accion.CREAR, avisos = leido.nuevosAvisos, avisosDichos = true), agenda, ahora)
                } else o
            }
            else -> o as Respuesta
        }
    }

    private fun modificar(leido: Interpretacion, evento: Evento, agenda: Agenda, ahora: LocalDateTime): Respuesta {
        var nuevo = evento
        val hecho = mutableListOf<String>()
        leido.nuevoTitulo?.takeIf { it.isNotBlank() }?.let {
            nuevo = nuevo.copy(titulo = it)
            hecho += "ahora se llama «$it»"
        }
        leido.reemplazo?.let { (viejo, por) ->
            val i = normal(nuevo.titulo).indexOf(normal(viejo))
            val titulo = if (i >= 0) nuevo.titulo.substring(0, i) + por + nuevo.titulo.substring(i + viejo.length)
                else por
            nuevo = nuevo.copy(titulo = titulo.trim().replaceFirstChar { it.uppercase() })
            hecho += "ahora se llama «${nuevo.titulo}»"
        }
        leido.nuevaNota?.let {
            nuevo = nuevo.copy(notas = listOfNotNull(nuevo.notas?.takeIf { n -> n.isNotBlank() }, it).joinToString("\n"))
            hecho += "con la nota «$it»"
        }
        leido.nuevoColor?.let { c ->
            runCatching { ColorEvento.valueOf(c) }.getOrNull()?.let {
                nuevo = nuevo.copy(color = it)
                hecho += "en ${NOMBRE_COLOR[it]}"
            }
        }
        leido.nuevosAvisos?.let {
            nuevo = nuevo.copy(avisos = it)
            hecho += "te aviso ${antelacion(it.first())}"
        }
        if (hecho.isEmpty()) return Respuesta.Hecha("No sé qué cambiar de ${evento.titulo}.", false)

        // Si es de una serie, el nombre, el color y los avisos cambian en
        // todas: "la clase de yoga" es una sola cosa que se repite.
        val serie = evento.serie
        if (serie != null) {
            val todas = agenda.eventos.filter { it.serie == serie }.map { e ->
                e.copy(titulo = nuevo.titulo, color = nuevo.color, avisos = nuevo.avisos,
                    notas = if (e.id == evento.id) nuevo.notas else e.notas)
            }
            agenda.guardarVarios(todas)
        } else {
            agenda.guardar(nuevo)
        }
        agenda.ponerEnContexto(nuevo)
        val quien = if (leido.nuevoTitulo != null || leido.reemplazo != null) evento.titulo else nuevo.titulo
        return Respuesta.Hecha("Cambiado: $quien, ${cuando(nuevo, ahora)}, ${enumerar(hecho)}.", true)
    }

    private val NOMBRE_COLOR = mapOf(
        ColorEvento.CIAN to "azul", ColorEvento.MAGENTA to "rosa", ColorEvento.VIOLETA to "morado",
        ColorEvento.VERDE to "verde", ColorEvento.AMBAR to "naranja",
    )

    private fun antelacion(minutos: Int): String = when {
        minutos == 0 -> "a la hora"
        minutos % 10080 == 0 -> if (minutos == 10080) "una semana antes" else "${minutos / 10080} semanas antes"
        minutos % 1440 == 0 -> if (minutos == 1440) "un día antes" else "${minutos / 1440} días antes"
        minutos % 60 == 0 -> if (minutos == 60) "una hora antes" else "${minutos / 60} horas antes"
        else -> "$minutos minutos antes"
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
                agenda.ponerEnContexto(siguiente)
                Respuesta.Hecha("Lo próximo es ${siguiente.titulo}, ${cuando(siguiente, ahora)}.", true)
            }

            Consulta.CUANDO -> {
                val encontrados = Buscador.candidatos(leido.titulo, leido.desde, agenda.eventos, ahora)
                if (encontrados.isEmpty()) {
                    return Respuesta.Hecha("No encuentro nada parecido a «${leido.titulo}».", false)
                }
                // Algo que se repite: se contesta con la próxima vez.
                elegirUno(encontrados, ahora, agenda)?.takeIf { it.serie != null }?.let { e ->
                    val dias = Series.diasDe(e, agenda.eventos)
                    agenda.ponerEnContexto(e)
                    return Respuesta.Hecha(
                        "${e.titulo} es ${Series.describir(e, e.repeticion, dias)}. La próxima, ${cuando(e, ahora)}.", true,
                    )
                }
                val mejor = encontrados.first().puntos
                val empatados = encontrados.filter { it.puntos == mejor }.map { it.evento }.sortedBy { it.inicio }
                if (empatados.size == 1) {
                    val e = empatados.first()
                    agenda.ponerEnContexto(e)
                    Respuesta.Hecha("${e.titulo} es ${cuando(e, ahora)}.", true)
                } else {
                    agenda.ponerEnContexto(null)
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
                // Si solo hay uno, "cancélalo" o "retrásalo" hablan de él.
                agenda.ponerEnContexto(enRango.singleOrNull())

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

    /** Sin tildes ni mayúsculas ni signos de los extremos. */
    private fun normal(texto: String): String = sinTildes(texto).trim(' ', '.', ',', '¿', '?', '¡', '!')
}
