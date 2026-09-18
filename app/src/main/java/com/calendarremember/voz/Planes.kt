package com.calendarremember.voz

import com.calendarremember.datos.Evento
import java.time.LocalDateTime

/**
 * Un texto que se comparte con Nébula desde otra app (un mensaje de
 * WhatsApp, un correo): "¿quedamos el sábado a las 9 para cenar?" se
 * convierte en un evento ya preparado, que se revisa antes de guardarlo.
 */
object Planes {

    /** Cómo se proponen los planes. Sobra en el título: "¿Quedamos para cenar?" es "Cenar". */
    private val PROPUESTA = Regex(
        """^(?:(?:oye|ey|eh|hey|tio|tia|bro|chicos|chicas|gente|equipo|pues|entonces|bueno|vale|venga|y\s+si|""" +
            """que\s+tal\s+si|que\s+tal|te\s+parece\s+si|os\s+parece\s+si|te\s+parece|os\s+parece|""" +
            """quedamos|nos\s+vemos|te\s+vienes|os\s+venis|vienes|venis|te\s+apuntas|os\s+apuntais|""" +
            """te\s+apetece|os\s+apetece|vamos|podemos|hacemos|recordad\s+que|recordad|acordaos\s+de\s+que|""" +
            """acordaos|no\s+olvideis\s+que|no\s+olvideis|ojo\s+que|ojo|y|a|para|al|de|el|la|los|las|un|una)\b[\s,:.!]*)+""",
        RegexOption.IGNORE_CASE,
    )

    /** Aquí no se duda: se prepara el evento siempre, y se revisa antes de guardarlo. */
    fun borrador(texto: String, ahora: LocalDateTime = LocalDateTime.now()): Evento {
        val limpio = sinEmojis(texto).trim()
        val leido = Interprete.interpretar(limpio, ahora, soloCrear = true)
        val titulo = titulo(leido.titulo.takeIf { it != "Recordatorio" } ?: "", null, null)
        return evento(leido, titulo.ifBlank { "Plan" }, limpio, null)
    }

    private fun evento(leido: Interpretacion, titulo: String, mensaje: String, deQuien: String?) = Evento(
        titulo = titulo,
        inicio = leido.inicio,
        todoElDia = leido.todoElDia,
        avisos = leido.avisos,
        duracionMin = leido.duracionMin,
        hasta = leido.hasta,
        notas = (if (deQuien != null) "$deQuien: " else "") + "«${mensaje.take(300)}»",
    )

    /**
     * "Quedamos para cenar" de Marta es "Cenar con Marta"; "quedamos
     * mañana en la plaza", "Quedada en la plaza con Marta"; "mi cumple" de
     * Ana, "Cumple de Ana".
     */
    private fun titulo(leido: String, deQuien: String?, grupo: String?, quedada: Boolean = false): String {
        var t = leido.replace(Regex("""[¿?¡!]"""), " ").replace(Regex("""\s+"""), " ").trim()
        var previo: String
        do {
            previo = t
            PROPUESTA.find(normalizar(t))?.let { m -> t = t.substring(m.value.length).trim() }
        } while (t != previo && t.isNotEmpty())
        // Lo que va detrás de una coma suele ser la pregunta o un
        // comentario: "Partido de pádel, ¿te apuntas?", "Concierto, empieza…".
        val coma = t.indexOf(',')
        if (coma > 0) {
            val cola = normalizar(t.substring(coma + 1)).trim()
            if (Regex("""^(?:te|os|vale|ok|empieza|empezamos|que|y\s+si|si|no|eh|va|venga|a\s+que|seguro|porfa|jaja)\b""")
                    .containsMatchIn(cola) || cola.split(' ').size <= 2) {
                t = t.substring(0, coma)
            }
        }
        if (deQuien != null) {
            t = t.replace(Regex("""^[Mm]i\s+(cumple|cumpleaños|cumpleanos|boda|fiesta|casa)\b""")) { m ->
                "${m.groupValues[1].replaceFirstChar { it.uppercase() }} de $deQuien"
            }.replace(Regex("""\ben\s+mi\s+casa\b""")) { "en casa de $deQuien" }
        }
        // "Quedamos en la plaza": la quedada es el título, el sitio lo acompaña.
        if (quedada && Regex("""^(?:en|a\s+la|al|a|delante|frente|junto)\b""").containsMatchIn(normalizar(t))) {
            t = "Quedada $t"
        }
        t = t.trim(' ', ',', '.', ';', ':').replaceFirstChar { it.uppercase() }
        if (t.length > 50) t = t.take(50).substringBeforeLast(' ') + "…"
        if (t.isBlank()) t = "Plan"
        val con = when {
            grupo != null -> " ($grupo)"
            deQuien != null && !normalizar(t).contains(normalizar(deQuien)) -> " con $deQuien"
            else -> ""
        }
        return t + con
    }

    /** Los emojis y símbolos no son parte de lo que se apunta. */
    private fun sinEmojis(texto: String): String = buildString {
        var i = 0
        while (i < texto.length) {
            val c = texto.codePointAt(i)
            val tipo = Character.getType(c)
            val simbolo = tipo == Character.OTHER_SYMBOL.toInt() || tipo == Character.SURROGATE.toInt() ||
                c == 0xFE0F || c == 0x200D || (c in 0x1F000..0x1FAFF)
            if (!simbolo) appendCodePoint(c) else append(' ')
            i += Character.charCount(c)
        }
    }.replace(Regex("""\s+"""), " ")

    private fun normalizar(texto: String): String {
        val con = "áéíóúüñàèìòùâêç"
        val sin = "aeiouunaeiouaec"
        return buildString {
            for (c in texto.lowercase()) {
                val i = con.indexOf(c)
                append(if (i >= 0) sin[i] else c)
            }
        }
    }
}
