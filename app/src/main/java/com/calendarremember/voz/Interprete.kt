package com.calendarremember.voz

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Convierte una frase dictada en español en un evento.
 *
 * No hay modelo de lenguaje detrás: son reglas. Es instantáneo, funciona sin
 * red y, sobre todo, falla de forma predecible: cuando no entiende algo lo
 * dice (confianza baja) en vez de inventárselo.
 */

enum class Confianza { ALTA, MEDIA, BAJA }

/** Lo que pide la frase: apuntar algo nuevo o quitar algo que ya existe. */
enum class Accion { CREAR, BORRAR }

data class Interpretacion(
    val accion: Accion,
    /**
     * Para CREAR es el título del evento. Para BORRAR es lo que hay que
     * buscar entre los eventos ya guardados.
     */
    val titulo: String,
    val inicio: LocalDateTime,
    val todoElDia: Boolean,
    val avisos: List<Int>,
    val duracionMin: Int?,
    /**
     * Si la frase decía una fecha. Al buscar un evento para cancelarlo hay
     * que distinguir entre "el viernes" y el día de hoy puesto por defecto.
     */
    val fechaDicha: Boolean,
    val dictado: String,
    val confianza: Confianza,
)

object Interprete {

    private val NUMEROS = mapOf(
        "cero" to 0, "un" to 1, "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3,
        "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8,
        "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13,
        "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17,
        "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20, "veintiuno" to 21,
        "veintiun" to 21, "veintidos" to 22, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
        "treinta" to 30,
    )

    private val MESES = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5,
        "junio" to 6, "julio" to 7, "agosto" to 8, "septiembre" to 9,
        "setiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12,
    )

    private val DIAS = mapOf(
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY, "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY,
    )

    /**
     * Verbos y muletillas con los que arranca un dictado. Se quitan del título
     * porque "recuérdame comprar pan" es un evento llamado "Comprar pan".
     * "cita" no está en la lista: "cita con el dentista" es un buen título.
     */
    private val ARRANQUES = listOf(
        // Al dictar se tiende a llamar a la app por su nombre antes de pedir
        // nada: "Nébula, apúntame...". No es parte de lo que se apunta.
        "nebula",
        "anademe", "anade", "apuntame", "apunta", "recuerdame", "recuerda",
        "agendame", "agenda", "ponme", "pon", "creame", "crea", "programame",
        "programa", "meteme", "mete", "guardame", "guarda", "tengo que", "tengo",
        "nuevo evento", "nuevo recordatorio", "evento", "recordatorio",
        "en el calendario", "al calendario", "para el calendario",
    )

    /**
     * Sustitución 1:1 para normalizar SIN que cambien las posiciones de los
     * caracteres. Es lo que permite buscar sobre el texto sin tildes y luego
     * recortar sobre el original conservando las tildes del título.
     */
    private const val CON_TILDE = "áéíóúüñàèìòùâêç"
    private const val SIN_TILDE = "aeiouunaeiouaec"

    private fun normalizar(texto: String): String = buildString {
        for (c in texto.lowercase()) {
            val i = CON_TILDE.indexOf(c)
            append(if (i >= 0) SIN_TILDE[i] else c)
        }
    }

    private fun numero(palabra: String?): Int? {
        val limpio = palabra?.trim() ?: return null
        return limpio.toIntOrNull() ?: NUMEROS[limpio]
    }

    private val N: String by lazy { "\\d{1,2}|" + NUMEROS.keys.joinToString("|") }

    fun interpretar(dictado: String, ahora: LocalDateTime = LocalDateTime.now()): Interpretacion {
        val original = dictado.trim()
        val texto = normalizar(original)

        // Tramos de la frase ya consumidos por una regla. Se borran del título.
        val consumido = mutableListOf<IntRange>()

        fun pisaLoYaLeido(rango: IntRange) =
            consumido.any { rango.first <= it.last && it.first <= rango.last }

        /**
         * Busca saltándose lo que otra regla ya se llevó. Sin esto, "a las
         * nueve de la mañana" acabaría en el día siguiente: la regla de la
         * hora consume "de la mañana" entera, pero la regla de los relativos
         * volvería a encontrar ese "mañana" suelto y lo leería como un día.
         */
        fun buscar(patron: String): MatchResult? {
            val m = Regex(patron).findAll(texto).firstOrNull { !pisaLoYaLeido(it.range) }
            if (m != null) consumido.add(m.range)
            return m
        }

        // Qué se pide. El verbo puede ir delante ("cancela la cena del
        // viernes") o detrás ("lo del finde, cancélalo"), que es como sale al
        // hablar. Al final solo cuenta si lleva pronombre pegado —"cancélalo",
        // "bórralo"—, porque así no hay duda de que es una orden: un "para
        // cancelar el contrato" en mitad de un título no la dispara.
        val VERBOS = "cancela|borra|elimina|quita|anula|suprime"
        val accion = if (
            buscar("""^\s*(?:nebula\s*[,.]?\s*)?(?:$VERBOS)(?:me|lo|la)?\b""") != null ||
            buscar("""\b(?:$VERBOS)(?:me)?(?:lo|la)\b""") != null
        ) Accion.BORRAR else Accion.CREAR

        val hoy = ahora.toLocalDate()
        var fecha: LocalDate? = null
        var hora: Pair<Int, Int>? = null      // hora, minuto
        var avisos: List<Int>? = null
        var duracionMin: Int? = null

        // 1. Avisos explícitos: "avísame dos días antes".
        //    Va el primero para que su "dos días" no lo capture otra regla.
        buscar("\\b(?:avisa(?:me)?|recuerda(?:melo)?|aviso)\\s+(?:con\\s+)?($N)\\s+" +
                "(minutos?|horas?|dias?|semanas?)\\s+antes\\b")?.let { m ->
            val n = numero(m.groupValues[1])
            val u = m.groupValues[2]
            val factor = when {
                u.startsWith("minuto") -> 1
                u.startsWith("hora") -> 60
                u.startsWith("dia") -> 1440
                else -> 10080
            }
            if (n != null) avisos = listOf(n * factor, 0)
        }

        // 2. Duración: "durante dos horas".
        buscar("\\bdurante\\s+($N)\\s+(horas?|minutos?)\\b")?.let { m ->
            val n = numero(m.groupValues[1])
            if (n != null) {
                duracionMin = if (m.groupValues[2].startsWith("hora")) n * 60 else n
            }
        }

        // 3. "dentro de tres horas" — resuelve fecha y hora de una vez.
        buscar("\\bdentro\\s+de\\s+($N)\\s+(minutos?|horas?|dias?|semanas?|meses?)\\b")?.let { m ->
            val n = numero(m.groupValues[1])
            val u = m.groupValues[2]
            if (n != null) {
                val destino = when {
                    u.startsWith("minuto") -> ahora.plusMinutes(n.toLong())
                    u.startsWith("hora") -> ahora.plusHours(n.toLong())
                    u.startsWith("dia") -> ahora.plusDays(n.toLong())
                    u.startsWith("semana") -> ahora.plusWeeks(n.toLong())
                    else -> ahora.plusMonths(n.toLong())
                }
                fecha = destino.toLocalDate()
                hora = destino.hour to destino.minute
            }
        }

        // 4. Hora. Antes que la fecha a propósito: "de la mañana" lleva dentro
        //    la palabra "mañana", y si la leyese la regla de fecha entendería
        //    "el día siguiente" cuando solo era el tramo del día.
        if (hora == null) {
            if (buscar("\\b(?:a|al)\\s+(?:el\\s+)?mediodia\\b") != null) {
                hora = 14 to 0
            } else if (buscar("\\ba\\s+medianoche\\b") != null) {
                hora = 0 to 0
            } else {
                val patron = Regex(
                    "\\b(?:a\\s+las?\\s+|a\\s+la\\s+)?($N)" +
                    "(?:\\s*[:.]\\s*(\\d{2})|\\s+(y\\s+media|y\\s+cuarto|menos\\s+cuarto))?" +
                    "\\s*(?:(?:de\\s+la|por\\s+la)\\s+(manana|tarde|noche|madrugada)|(am|pm)|h(?:oras)?)?\\b"
                )
                // Se recorren TODAS las coincidencias, no solo la primera: en
                // "el 3/11 a las 8" el "3" casa antes que el "8", y en "una
                // alarma a las siete" lo hace el "una".
                val m = patron.findAll(texto).firstOrNull { c ->
                    val g = c.groupValues
                    !pisaLoYaLeido(c.range) && (
                        Regex("a\\s+las?\\s+|a\\s+la\\s+").containsMatchIn(c.value) ||
                            g[2].isNotEmpty() || g[3].isNotEmpty() ||
                            g[4].isNotEmpty() || g[5].isNotEmpty() ||
                            Regex("\\dh\\b").containsMatchIn(c.value)
                        )
                }
                if (m != null) {
                    consumido.add(m.range)
                    var h = numero(m.groupValues[1])
                    var min = m.groupValues[2].toIntOrNull() ?: 0
                    when (m.groupValues[3].replace(Regex("\\s+"), " ")) {
                        "y media" -> min = 30
                        "y cuarto" -> min = 15
                        "menos cuarto" -> { min = 45; h = h?.let { (it + 23) % 24 } }
                    }
                    val tramo = m.groupValues[4]
                    val meridiano = m.groupValues[5]
                    if (h != null && h!! <= 24) {
                        var hh = h!!
                        when {
                            (tramo == "tarde" || tramo == "noche") && hh < 12 -> hh += 12
                            tramo == "madrugada" && hh == 12 -> hh = 0
                            tramo == "manana" && hh == 12 -> hh = 0
                            meridiano == "pm" && hh < 12 -> hh += 12
                            meridiano == "am" && hh == 12 -> hh = 0
                            // "a las cinco" en boca de cualquiera es la tarde;
                            // "a las nueve", la mañana. El corte está donde lo
                            // pone el habla, no el reloj.
                            tramo.isEmpty() && meridiano.isEmpty() && hh in 1..7 -> hh += 12
                        }
                        hora = (hh % 24) to min
                    }
                }
            }
        }

        // 5. Fecha explícita: "el 25 de octubre", "el 3/11", "el día 8".
        if (fecha == null) {
            buscar("\\b(?:el\\s+)?(?:dia\\s+)?($N)\\s+de\\s+(${MESES.keys.joinToString("|")})" +
                    "(?:\\s+(?:del?\\s+)?(\\d{4}))?\\b")?.let { m ->
                val d = numero(m.groupValues[1])
                val mes = MESES[m.groupValues[2]]!!
                val anoDicho = m.groupValues[3].toIntOrNull()
                var ano = anoDicho ?: hoy.year
                // Sin año, se entiende el próximo que aún no ha pasado.
                if (anoDicho == null && (mes < hoy.monthValue ||
                        (mes == hoy.monthValue && (d ?: 1) < hoy.dayOfMonth))) ano++
                if (d != null && d in 1..31) fecha = fechaSegura(ano, mes, d)
            }
        }
        if (fecha == null) {
            buscar("\\b(?:el\\s+)?(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")?.let { m ->
                val d = m.groupValues[1].toInt()
                val mes = m.groupValues[2].toInt()
                val anoDicho = m.groupValues[3].toIntOrNull()
                var ano = anoDicho?.let { if (it < 100) 2000 + it else it } ?: hoy.year
                if (anoDicho == null && (mes < hoy.monthValue ||
                        (mes == hoy.monthValue && d < hoy.dayOfMonth))) ano++
                if (d in 1..31 && mes in 1..12) fecha = fechaSegura(ano, mes, d)
            }
        }
        if (fecha == null) {
            buscar("\\bel\\s+dia\\s+(\\d{1,2})\\b")?.let { m ->
                val d = m.groupValues[1].toInt()
                var base = hoy.withDayOfMonth(1)
                if (d < hoy.dayOfMonth) base = base.plusMonths(1)
                fecha = fechaSegura(base.year, base.monthValue, d)
            }
        }

        // 6. Día de la semana: "el martes", "el viernes que viene".
        if (fecha == null) {
            buscar("\\b(?:el\\s+|este\\s+|proximo\\s+|el\\s+proximo\\s+)?" +
                    "(${DIAS.keys.joinToString("|")})(\\s+que\\s+viene|\\s+proximo)?\\b")?.let { m ->
                val objetivo = DIAS[m.groupValues[1]]!!
                var saltos = (objetivo.value - hoy.dayOfWeek.value + 7) % 7
                // "el martes" dicho un martes es el de dentro de una semana.
                if (saltos == 0) saltos = 7
                // "que viene" empuja otra semana, salvo que ya estuviera empujando.
                if (m.groupValues[2].isNotEmpty() && saltos != 7) saltos += 7
                fecha = hoy.plusDays(saltos.toLong())
            }
        }

        // 6b. El fin de semana. Se toma el sábado como referencia; al buscar,
        //     un día de margen hace que el domingo también valga.
        if (fecha == null) {
            val mFinde = buscar("""\b(?:este\s+|el\s+|los\s+)?""" +
                """(?:finde|fin\s+de\s+semana)(?:\s+que\s+viene|\s+proximo)?\b""")
            if (mFinde != null) {
                var saltos = (DayOfWeek.SATURDAY.value - hoy.dayOfWeek.value + 7) % 7
                if (saltos == 0) saltos = 7
                if (mFinde.value.contains("viene") || mFinde.value.contains("proximo")) {
                    if (saltos != 7) saltos += 7
                }
                fecha = hoy.plusDays(saltos.toLong())
            }
        }

        // 7. Relativos sueltos. "mañana" ya no puede confundirse: si formaba
        //    parte de "de la mañana", la regla 4 se lo comió.
        if (fecha == null) {
            buscar("\\b(pasado\\s+manana|manana|hoy|esta\\s+noche|esta\\s+tarde)\\b")?.let { m ->
                val clave = m.groupValues[1].replace(Regex("\\s+"), " ")
                fecha = when (clave) {
                    "pasado manana" -> hoy.plusDays(2)
                    "manana" -> hoy.plusDays(1)
                    else -> hoy
                }
                if (hora == null && clave == "esta noche") hora = 21 to 0
                if (hora == null && clave == "esta tarde") hora = 17 to 0
            }
        }

        // --- Resolución --------------------------------------------------
        val todoElDia = hora == null
        val fechaDicha = fecha != null

        if (fecha == null) {
            fecha = if (hora != null) {
                // Hora sin día: hoy si aún llega, mañana si ya pasó.
                val candidato = hoy.atTime(hora!!.first, hora!!.second)
                if (!candidato.isAfter(ahora)) hoy.plusDays(1) else hoy
            } else hoy
        }

        val inicio = fecha!!.atTime(hora?.first ?: 0, hora?.second ?: 0)

        // --- Título --------------------------------------------------------
        // Se recorta del texto ORIGINAL (con tildes) usando los tramos que las
        // reglas fueron marcando, de atrás hacia delante para no mover índices.
        // Los tramos se unen antes de recortar: dos que se toquen tienen que
        // salir de una sola pasada, porque al quitar el primero los índices
        // del segundo ya no valdrían.
        val tramos = mutableListOf<IntRange>()
        for (rango in consumido.sortedBy { it.first }) {
            val ultimo = tramos.lastOrNull()
            if (ultimo != null && rango.first <= ultimo.last + 1) {
                tramos[tramos.size - 1] = ultimo.first..maxOf(ultimo.last, rango.last)
            } else {
                tramos.add(rango)
            }
        }

        var titulo = original
        for (rango in tramos.sortedByDescending { it.first }) {
            val desde = rango.first.coerceIn(0, titulo.length)
            val hasta = (rango.last + 1).coerceIn(desde, titulo.length)
            titulo = titulo.substring(0, desde) + " " + titulo.substring(hasta)
        }
        titulo = titulo.replace(Regex("\\s+"), " ").trim()

        val arranques = Regex("^(?:${ARRANQUES.joinToString("|")})\\b[\\s,]*")
        var previo: String
        do {
            previo = titulo
            arranques.find(normalizar(titulo))?.let {
                titulo = titulo.substring(it.value.length).trim()
            }
        } while (titulo != previo && titulo.isNotEmpty())

        // Preposiciones huérfanas que quedan al arrancar la parte temporal.
        titulo = titulo
            .replace(Regex("^(?:de|del|el|la|a|al|en|que|para|por|un|una)\\s+", RegexOption.IGNORE_CASE), "")
            .trim(' ', ',', ';', '.')

        if (titulo.isEmpty() && accion == Accion.CREAR) titulo = "Recordatorio"
        titulo = titulo.replaceFirstChar { it.uppercase() }

        // La confianza existe para que la app pregunte en vez de dar por bueno
        // algo que no ha entendido.
        val confianza = when {
            titulo.isEmpty() || titulo == "Recordatorio" -> Confianza.BAJA
            // Al cancelar no hace falta hora: basta con saber qué se quita.
            accion == Accion.BORRAR -> Confianza.ALTA
            todoElDia -> Confianza.MEDIA
            else -> Confianza.ALTA
        }

        return Interpretacion(
            accion = accion,
            titulo = titulo,
            inicio = inicio,
            todoElDia = todoElDia,
            avisos = avisos ?: if (todoElDia) listOf(1440, 0) else listOf(1440, 60, 0),
            duracionMin = duracionMin,
            fechaDicha = fechaDicha,
            dictado = original,
            confianza = confianza,
        )
    }

    /** "el 31 de febrero" no existe: se recorta al último día real del mes. */
    private fun fechaSegura(ano: Int, mes: Int, dia: Int): LocalDate {
        val primero = LocalDate.of(ano, mes, 1)
        return primero.withDayOfMonth(minOf(dia, primero.lengthOfMonth()))
    }
}
