package com.calendarremember

import com.calendarremember.voz.Accion
import com.calendarremember.voz.Interpretacion
import com.calendarremember.voz.Interprete
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Las frases de los barridos grandes, cada una con lo que tiene que salir.
 * Están en test/resources/frases.txt, revisadas a mano: si
 * una regla nueva cambia lo que se entiende de cualquiera de ellas, esta
 * prueba lo dice, con la frase, lo que salía y lo que sale ahora.
 *
 * Reloj fijado en viernes 18/09/2026 a las 12:00.
 */
class FrasesTest {

    private val ahora = LocalDateTime.of(2026, 9, 18, 12, 0)

    private fun lineas(recurso: String): List<String> =
        javaClass.getResourceAsStream(recurso)!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }

    /** Lo que se entendió, en una línea: el mismo formato que el fichero. */
    private fun describir(r: Interpretacion): String {
        val i = r.inicio
        val cuando = if (r.todoElDia) "%02d/%02d todo".format(i.dayOfMonth, i.monthValue)
            else "%02d/%02d %02d:%02d".format(i.dayOfMonth, i.monthValue, i.hour, i.minute)
        val extra = buildList {
            if (i.year != 2026) add("año ${i.year}")
            r.hasta?.let { add("hasta ${"%02d/%02d".format(it.dayOfMonth, it.monthValue)}" + if (it.year != 2026) "/${it.year}" else "") }
            r.duracionMin?.let { add("dura $it") }
            if (r.repeticion.name != "NINGUNA") add(r.repeticion.name + (if (r.intervalo > 1) " x${r.intervalo}" else "") +
                (if (r.diasSemana.isNotEmpty()) " " + r.diasSemana.joinToString("/") { it.name.take(3) } else ""))
            if (r.elUltimo) add("EL ULTIMO")
            if (r.avisosDichos) add("avisos ${r.avisos}")
            r.nuevaFecha?.let { add("→fecha $it") }
            r.nuevaHora?.let { add("→hora $it") }
            r.desplazamientoMin?.let { add("desplaza $it") }
            r.nuevoHasta?.let { add("→hasta $it") }
            r.alargarMin?.let { add("alarga $it") }
            r.consulta?.let { add("$it ${r.desde}..${r.hasta}") }
            r.nuevoTitulo?.let { add("→nombre «$it»") }
            if (r.particiones.size > 1) add("partes ${r.particiones.size}")
            r.nuevaNota?.let { add("→nota «$it»") }
            r.nuevoColor?.let { add("→color $it") }
            r.reemplazo?.let { add("→«${it.first}» por «${it.second}»") }
            r.nuevosAvisos?.let { add("→avisos $it") }
            if (!r.fechaDicha) add("sin fecha")
        }
        val accion = when (r.accion) { Accion.CREAR -> "+"; Accion.BORRAR -> "X"; Accion.MOVER -> ">"; Accion.CONSULTAR -> "?"; Accion.EDITAR -> "E" }
        return "$accion [${r.titulo}] $cuando ${extra.joinToString(", ")}".trim()
    }

    @Test fun lasFrasesDeLosBarridos() {
        val fallos = mutableListOf<String>()
        val todas = lineas("/frases.txt")
        for (linea in todas) {
            val (esperado, frase) = linea.split("   <= ", limit = 2)
            val sale = describir(Interprete.interpretar(frase, ahora))
            if (sale != esperado.trim()) fallos += "«$frase»\n      esperado: ${esperado.trim()}\n      sale:     $sale"
        }
        assertTrue(
            "\n${fallos.size} de ${todas.size} frases cambian:\n" + fallos.joinToString("\n") { "  - $it" },
            fallos.isEmpty(),
        )
        assertTrue("${todas.size} frases", todas.size >= 300)
    }
}
