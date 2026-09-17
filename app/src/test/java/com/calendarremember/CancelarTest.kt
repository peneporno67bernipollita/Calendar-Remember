package com.calendarremember

import com.calendarremember.datos.Evento
import com.calendarremember.voz.Accion
import com.calendarremember.voz.Buscador
import com.calendarremember.voz.Interprete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Cancelar eventos por voz. El reloj se fija en jueves 17/09/2026 a las 12:00.
 *
 * Importa tanto lo que tiene que borrar como lo que NO: una frase que solo
 * menciona la palabra "cancelar" no es una orden de borrado, y un criterio
 * que encaja con dos eventos no puede resolverse solo.
 */
class CancelarTest {

    private val ahora: LocalDateTime = LocalDateTime.of(2026, 9, 17, 12, 0)

    // --- Reconocer la intención ------------------------------------------

    @Test fun cancelarConElVerboDelante() {
        val r = Interprete.interpretar("cancela la cena del viernes", ahora)
        assertEquals(Accion.BORRAR, r.accion)
        assertEquals("Cena", r.titulo)
        assertEquals(true, r.fechaDicha)
    }

    /** Al hablar, la orden suele salir al final. */
    @Test fun cancelarConElVerboDetras() {
        val r = Interprete.interpretar(
            "nébula, el finde que viene que tenía la boda, cancélalo que ya no voy", ahora
        )
        assertEquals(Accion.BORRAR, r.accion)
        assertTrue("debería quedar la boda como criterio", r.titulo.contains("boda", true))
    }

    @Test fun borrarTambienVale() {
        val r = Interprete.interpretar("borra el médico del 3 de noviembre", ahora)
        assertEquals(Accion.BORRAR, r.accion)
        assertEquals("Médico", r.titulo)
    }

    /** "cancelar" dentro de un título no es una orden de cancelar. */
    @Test fun mencionarCancelarNoBorra() {
        val r = Interprete.interpretar("reunión para cancelar el contrato el lunes", ahora)
        assertEquals(Accion.CREAR, r.accion)
    }

    @Test fun apuntarSigueSiendoLoNormal() {
        val r = Interprete.interpretar("cena con Marta el viernes a las 21:30", ahora)
        assertEquals(Accion.CREAR, r.accion)
    }

    /** El fin de semana se resuelve al sábado siguiente. */
    @Test fun elFindeEsElSabado() {
        val r = Interprete.interpretar("comida con los primos el finde", ahora)
        assertEquals(19, r.inicio.dayOfMonth)
        assertEquals(true, r.fechaDicha)
    }

    // --- Encontrar el evento ---------------------------------------------

    private val cena = Evento(
        titulo = "Cena con Marta",
        inicio = LocalDateTime.of(2026, 9, 18, 21, 30),
    )
    private val medico = Evento(
        titulo = "Médico",
        inicio = LocalDateTime.of(2026, 11, 3, 8, 0),
    )
    private val boda = Evento(
        titulo = "Boda de Ana",
        inicio = LocalDateTime.of(2026, 9, 26, 13, 0),
    )
    private val agenda = listOf(cena, medico, boda)

    @Test fun encuentraPorTexto() {
        val c = Buscador.candidatos("Cena", null, agenda, ahora)
        assertEquals(cena.id, Buscador.unico(c)?.id)
    }

    @Test fun encuentraPorFechaAunqueElTextoSeaPobre() {
        // "el finde que viene" cae en sábado 26; la boda es ese día.
        val r = Interprete.interpretar("lo del finde que viene, cancélalo", ahora)
        val c = Buscador.candidatos(
            r.titulo, if (r.fechaDicha) r.inicio.toLocalDate() else null, agenda, ahora
        )
        assertEquals(boda.id, Buscador.unico(c)?.id)
    }

    @Test fun sinNadaParecidoNoDevuelveNada() {
        val c = Buscador.candidatos("partido de fútbol", null, agenda, ahora)
        assertTrue("no debería encajar nada", c.isEmpty())
    }

    /** Con dos que encajan igual, no hay ganador: hay que preguntar. */
    @Test fun elEmpateNoSeResuelveSolo() {
        val otraCena = Evento(
            titulo = "Cena con Luis",
            inicio = LocalDateTime.of(2026, 9, 25, 21, 0),
        )
        val c = Buscador.candidatos("cena", null, listOf(cena, otraCena), ahora)
        assertEquals(2, c.size)
        assertNull("con empate no se elige solo", Buscador.unico(c))
    }
}
