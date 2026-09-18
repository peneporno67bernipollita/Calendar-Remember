package com.calendarremember.voz

import com.calendarremember.datos.Evento
import java.time.LocalDateTime

/**
 * Los planes que llegan por WhatsApp: "¿quedamos el sábado a las 9 para
 * cenar?", "el viernes cumple de Ana en su casa a las 8".
 *
 * Nébula no puede ver lo que se contesta (eso se queda dentro de WhatsApp),
 * pero sí los mensajes que llegan, por sus notificaciones. Cuando uno propone
 * algo con día u hora, se ofrece apuntarlo con un toque. Mejor callar ante la
 * duda que llenar el móvil de avisos: solo cuenta un mensaje que suene a plan
 * y diga cuándo, en el futuro.
 */
object Planes {

    /** Algo que suene a quedar, o a un sitio o una actividad a la que ir. */
    private val PLAN = Regex(
        """\b(?:quedamos|quedar|quedais|nos\s+vemos|os\s+veo|te\s+veo|vienes|venis|vendras|te\s+vienes|os\s+venis|""" +
            """te\s+apuntas|os\s+apuntais|te\s+apetece|os\s+apetece|te\s+parece|os\s+parece|te\s+va\s+bien|""" +
            """os\s+va\s+bien|te\s+viene\s+bien|os\s+viene\s+bien|podemos|hacemos|cenamos|comemos|desayunamos|""" +
            """merendamos|tomamos|salimos|jugamos|vamos|plan|planazo|cena|comida|comer|cenar|desayuno|cafe|""" +
            """cervezas?|cerve|birras?|copas?|fiesta|cumple|cumpleanos|partido|pachanga|padel|futbol|reunion|""" +
            """cita|boda|concierto|cine|peli|teatro|viaje|escapada|excursion|barbacoa|quedada|entreno|clase)\b"""
    )

    /** Lo que ya pasó, o lo que se cae: no es un plan que apuntar. */
    private val NO_ES = Regex(
        """\b(?:ayer|anoche|anteayer|antes\s+de\s+ayer|el\s+otro\s+dia|la\s+semana\s+pasada|el\s+mes\s+pasado|""" +
            """no\s+(?:puedo|podemos|puede|pueden|voy|vamos|va|van|me\s+va|nos\s+va|me\s+viene|quedamos)|""" +
            """se\s+cancela|cancelad[oa]|suspendid[oa]|al\s+final\s+no)\b"""
    )

    /** Cómo se proponen los planes. Sobra en el título: "¿Quedamos para cenar?" es "Cenar". */
    private val PROPUESTA = Regex(
        """^(?:(?:oye|ey|eh|hey|tio|tia|bro|chicos|chicas|gente|equipo|pues|entonces|bueno|vale|venga|y\s+si|""" +
            """que\s+tal\s+si|que\s+tal|te\s+parece\s+si|os\s+parece\s+si|te\s+parece|os\s+parece|""" +
            """quedamos|nos\s+vemos|te\s+vienes|os\s+venis|vienes|venis|te\s+apuntas|os\s+apuntais|""" +
            """te\s+apetece|os\s+apetece|vamos|podemos|hacemos|y|a|para|al|de)\b[\s,:.!]*)+""",
        RegexOption.IGNORE_CASE,
    )

    private const val MAX_DIAS = 120L

    /**
     * El evento que propone un mensaje, o null si no propone nada con
     * fecha. [deQuien]: quien lo escribe; [grupo]: el nombre del grupo, si
     * es un grupo.
     */
    fun detectar(
        mensaje: String,
        deQuien: String?,
        grupo: String?,
        ahora: LocalDateTime = LocalDateTime.now(),
    ): Evento? {
        val limpio = sinEmojis(mensaje).trim()
        if (limpio.length < 6 || limpio.length > 400) return null
        val texto = normalizar(limpio)
        if (!PLAN.containsMatchIn(texto) || NO_ES.containsMatchIn(texto)) return null

        val leido = Interprete.interpretar(limpio, ahora, soloCrear = true)
        if (!leido.fechaDicha && !leido.horaDicha) return null
        // "Mañana te digo algo" no es un plan: sin hora, hace falta algo más
        // que un día y un verbo cualquiera.
        if (!leido.horaDicha && !Regex("""\b(?:quedamos|quedar|nos\s+vemos|cumple|cumpleanos|boda|fiesta|cena|comida|""" +
                """viaje|escapada|concierto|partido|excursion|barbacoa|quedada|plan)\b""").containsMatchIn(texto)) return null
        val inicio = leido.inicio
        val finDia = leido.hasta ?: inicio.toLocalDate()
        if (finDia.isBefore(ahora.toLocalDate())) return null
        if (!leido.todoElDia && !inicio.isAfter(ahora)) return null
        if (inicio.toLocalDate().isAfter(ahora.toLocalDate().plusDays(MAX_DIAS))) return null

        return evento(leido, titulo(leido.titulo, deQuien, grupo), limpio, deQuien)
    }

    /**
     * Un texto compartido a Nébula a propósito ("compartir" en WhatsApp).
     * Aquí no se duda: se prepara el evento siempre, y el usuario lo revisa
     * antes de guardarlo.
     */
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

    /** "Quedamos para cenar" de Marta es "Cenar con Marta". */
    private fun titulo(leido: String, deQuien: String?, grupo: String?): String {
        var t = leido.replace(Regex("""[¿?¡!]"""), " ").replace(Regex("""\s+"""), " ").trim()
        var previo: String
        do {
            previo = t
            PROPUESTA.find(normalizar(t))?.let { m -> t = t.substring(m.value.length).trim() }
        } while (t != previo && t.isNotEmpty())
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
