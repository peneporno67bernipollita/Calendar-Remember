package com.calendarremember.voz

import com.calendarremember.datos.Evento
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
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

    /**
     * Palabras que no distinguen un evento de otro: aparecen en cualquier
     * frase, o son el propio nombre de lo que se busca ("el evento de...").
     */
    private val VACIAS = setOf(
        "el", "la", "los", "las", "lo", "un", "una", "unos", "unas", "de",
        "del", "al", "a", "que", "mi", "mis", "tu", "tus", "su", "sus", "con",
        "para", "por", "en", "y", "o", "se", "ya", "no", "me", "te", "le",
        "voy", "vas", "va", "ir", "tengo", "tenia", "tiene", "hay", "es",
        "eso", "esa", "ese", "esto", "esta", "este", "cosa", "plan", "nada",
        "todo", "evento", "eventos", "recordatorio", "recordatorios", "aviso",
        "avisos", "nebula", "hora", "dia", "fecha", "todos", "todas", "siempre",
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
        hora: LocalTime? = null,
    ): List<Candidato> {
        val buscadas = palabras(criterio)

        return eventos.mapNotNull { evento ->
            var puntos = 0

            // Por texto: cada palabra con peso que aparezca en el título suma.
            // Es lo que más distingue un evento de otro.
            val delTitulo = palabras(evento.titulo)
            puntos += buscadas.count { b -> delTitulo.any { t -> parecidas(b, t) } } * 3

            // Por fecha. Un día de margen cubre el "finde", que se resuelve al
            // sábado pero puede ser el domingo.
            if (fecha != null) {
                val dias = abs(ChronoUnit.DAYS.between(fecha, evento.inicio.toLocalDate()))
                puntos += when {
                    dias == 0L -> 4
                    dias == 1L -> 3
                    dias <= 6L -> 1
                    else -> 0
                }
            }

            // Por hora: "cancela lo de las diez" no dice ni qué ni qué día.
            if (hora != null && !evento.todoElDia) {
                val minutos = abs(ChronoUnit.MINUTES.between(hora, evento.inicio.toLocalTime()))
                puntos += when {
                    minutos == 0L -> 4
                    minutos <= 30L -> 2
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

    /**
     * Dos palabras cuentan como la misma si comparten raíz: "reunión" y
     * "reuniones", "cumple" y "cumpleaños", "dentista" y "dentistas". El
     * reconocedor de voz no siempre escribe la palabra igual que el título.
     */
    private fun parecidas(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        val raiz = minOf(5, a.length, b.length)
        return a.take(raiz) == b.take(raiz)
    }

    private fun palabras(texto: String): List<String> =
        normalizar(texto)
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
