package com.calendarremember.voz

import com.calendarremember.datos.Evento
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Encuentra el evento del que habla una frase como "cancela la cena del
 * viernes".
 *
 * Nunca decide sola que algo se borra: devuelve los candidatos ordenados y
 * quien llama se encarga de preguntar. Equivocarse al crear un evento se
 * arregla borrándolo; equivocarse al borrar pierde algo que no vuelve.
 */
object Buscador {

    /** Palabras que no distinguen nada: aparecen en cualquier frase. */
    private val VACIAS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del",
        "al", "a", "que", "lo", "mi", "mis", "tu", "tus", "su", "sus", "con",
        "para", "por", "en", "y", "o", "se", "ya", "no", "me", "te", "le",
        "voy", "vas", "va", "ir", "tengo", "tenia", "tiene", "hay", "es",
        "eso", "esa", "ese", "esto", "esta", "este", "cosa", "plan",
    )

    data class Candidato(val evento: Evento, val puntos: Int)

    /**
     * Ordena los eventos por lo bien que encajan con lo dicho. Solo devuelve
     * los que encajan en algo: si la lista vuelve vacía, es que no se ha
     * reconocido nada y hay que decirlo, no elegir al azar.
     */
    fun candidatos(
        criterio: String,
        fecha: LocalDate?,
        eventos: List<Evento>,
        ahora: LocalDateTime = LocalDateTime.now(),
    ): List<Candidato> {
        val palabras = palabrasUtiles(criterio)

        return eventos.mapNotNull { evento ->
            var puntos = 0

            // Coincidencia por texto: cada palabra con peso que aparezca en el
            // título suma. Es lo que más manda, porque es lo que distingue un
            // evento de otro.
            val titulo = normalizar(evento.titulo)
            val acertadas = palabras.count { titulo.contains(it) }
            puntos += acertadas * 3

            // Coincidencia por fecha. Un día de margen cubre el "finde", que
            // se resuelve al sábado pero puede ser el domingo.
            if (fecha != null) {
                val dias = abs(java.time.temporal.ChronoUnit.DAYS.between(
                    fecha, evento.inicio.toLocalDate()
                ))
                puntos += when {
                    dias == 0L -> 4
                    dias == 1L -> 3
                    dias <= 6L -> 1
                    else -> 0
                }
            }

            // Lo que ya pasó rara vez es lo que se quiere cancelar.
            if (evento.inicio.isBefore(ahora)) puntos -= 2

            if (puntos > 0) Candidato(evento, puntos) else null
        }.sortedByDescending { it.puntos }
    }

    /**
     * El candidato que se puede dar por bueno sin dudar: tiene que haber
     * acertado algo de peso y destacar sobre el siguiente. Si dos eventos
     * empatan, no hay un ganador claro y toca preguntar.
     */
    fun unico(candidatos: List<Candidato>): Evento? {
        val mejor = candidatos.firstOrNull() ?: return null
        if (mejor.puntos < 3) return null
        val segundo = candidatos.getOrNull(1) ?: return mejor.evento
        return if (mejor.puntos > segundo.puntos) mejor.evento else null
    }

    private fun palabrasUtiles(criterio: String): List<String> =
        normalizar(criterio)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in VACIAS }

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
