package com.calendarremember.datos

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Los cinco colores de neón de la app. El nombre viaja al JSON, no el hex. */
enum class ColorEvento { CIAN, MAGENTA, VIOLETA, VERDE, AMBAR }

data class Evento(
    val id: String = UUID.randomUUID().toString(),
    val titulo: String,
    val notas: String? = null,
    /** Momento del evento en hora local. Se guarda como epoch para ordenar. */
    val inicio: LocalDateTime,
    val todoElDia: Boolean = false,
    val color: ColorEvento = ColorEvento.CIAN,
    /** Minutos de antelación de cada aviso. 0 = a la hora en punto. */
    val avisos: List<Int> = listOf(1440, 60, 0),
    val duracionMin: Int? = null,
    /** Lo que se dictó, si vino por voz. Sirve para afinar el intérprete. */
    val dictado: String? = null,
    val creado: Long = System.currentTimeMillis(),
    /**
     * Las repeticiones de "todos los martes" son eventos normales que
     * comparten serie: así el calendario, las alarmas y el widget no tienen
     * que saber nada de repeticiones. La serie permite borrarlas todas a la
     * vez, y seguir alargándolas según pasa el tiempo.
     */
    val serie: String? = null,
    val repeticion: com.calendarremember.voz.Repeticion = com.calendarremember.voz.Repeticion.NINGUNA,
) {
    val inicioMillis: Long
        get() = inicio.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Instante en que toca lanzar un aviso concreto.
     *
     * En un evento con hora es "la hora menos la antelación". En uno de todo
     * el día se toma como referencia las 9:00: avisar de un cumpleaños a las
     * 00:00 no le sirve a nadie.
     */
    fun momentoAviso(minutosAntes: Int): Long {
        val base = if (todoElDia) {
            inicio.toLocalDate().atTime(9, 0)
        } else inicio
        return base.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                minutosAntes * 60_000L
    }

    fun aJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("titulo", titulo)
        put("notas", notas ?: JSONObject.NULL)
        put("inicio", inicioMillis)
        put("todoElDia", todoElDia)
        put("color", color.name)
        put("avisos", JSONArray(avisos))
        put("duracionMin", duracionMin ?: JSONObject.NULL)
        put("dictado", dictado ?: JSONObject.NULL)
        put("creado", creado)
        put("serie", serie ?: JSONObject.NULL)
        put("repeticion", repeticion.name)
    }

    companion object {
        fun deJson(o: JSONObject): Evento {
            val avisos = o.optJSONArray("avisos")?.let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }
            } ?: listOf(1440, 60, 0)

            return Evento(
                id = o.optString("id", UUID.randomUUID().toString()),
                titulo = o.optString("titulo", "Sin título"),
                notas = o.optString("notas").takeIf { it.isNotBlank() && it != "null" },
                inicio = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(o.getLong("inicio")), ZoneId.systemDefault()
                ),
                todoElDia = o.optBoolean("todoElDia", false),
                color = runCatching { ColorEvento.valueOf(o.optString("color")) }
                    .getOrDefault(ColorEvento.CIAN),
                avisos = avisos,
                duracionMin = if (o.isNull("duracionMin")) null else o.optInt("duracionMin"),
                dictado = o.optString("dictado").takeIf { it.isNotBlank() && it != "null" },
                creado = o.optLong("creado", System.currentTimeMillis()),
                // Los eventos guardados antes de que hubiera series no traen
                // estos campos: se leen como eventos sueltos.
                serie = o.optString("serie").takeIf { it.isNotBlank() && it != "null" },
                repeticion = runCatching {
                    com.calendarremember.voz.Repeticion.valueOf(o.optString("repeticion"))
                }.getOrDefault(com.calendarremember.voz.Repeticion.NINGUNA),
            )
        }
    }
}
