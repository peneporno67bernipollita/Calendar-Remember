package com.calendarremember

import com.calendarremember.datos.Evento
import com.calendarremember.voz.Agenda
import com.calendarremember.voz.Ejecutor
import com.calendarremember.voz.Interprete
import com.calendarremember.voz.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Lo que hace cada orden de principio a fin: frase dictada, agenda antes y
 * agenda después. Reloj fijado en jueves 17/09/2026 a las 12:00.
 */
class EjecutorTest {

    private val ahora = LocalDateTime.of(2026, 9, 17, 12, 0)

    private class Memoria(vararg iniciales: Evento) : Agenda {
        val lista = iniciales.toMutableList()
        override val eventos: List<Evento> get() = lista.toList()
        override fun guardar(evento: Evento) {
            val i = lista.indexOfFirst { it.id == evento.id }
            if (i >= 0) lista[i] = evento else lista.add(evento)
        }
        override fun borrar(id: String) { lista.removeAll { it.id == id } }
        fun porTitulo(t: String) = lista.first { it.titulo == t }
    }

    private fun agenda() = Memoria(
        Evento(titulo = "Gimnasio", inicio = LocalDateTime.of(2026, 9, 17, 19, 0)),
        Evento(titulo = "Dentista", inicio = LocalDateTime.of(2026, 9, 18, 10, 0)),
        Evento(titulo = "Cena con Marta", inicio = LocalDateTime.of(2026, 9, 18, 21, 30)),
        Evento(titulo = "Cumpleaños de Laura", inicio = LocalDateTime.of(2026, 10, 3, 0, 0), todoElDia = true),
    )

    private fun decir(frase: String, a: Memoria): Respuesta =
        Ejecutor.ejecutar(Interprete.interpretar(frase, ahora), a, ahora)

    private fun mensaje(r: Respuesta) = r.mensaje

    // --- Apuntar ------------------------------------------------------------

    @Test fun apuntaYLoDice() {
        val a = agenda()
        val r = decir("Apúntame comprar pan mañana a las 9", a)
        assertTrue(mensaje(r), r is Respuesta.Hecha && r.bien)
        assertTrue(mensaje(r), mensaje(r).startsWith("Apuntado: Comprar pan, mañana a las 09:00"))
        assertEquals(LocalDateTime.of(2026, 9, 18, 9, 0), a.porTitulo("Comprar pan").inicio)
    }

    @Test fun avisaDeQueNoRepite() {
        val r = decir("Clase de yoga todos los martes a las 7", agenda())
        assertTrue(mensaje(r), mensaje(r).contains("Solo esta vez"))
    }

    // --- Cancelar -----------------------------------------------------------

    @Test fun borraSinPreguntar() {
        val a = agenda()
        val r = decir("Borra el dentista", a)
        assertTrue(mensaje(r), mensaje(r).startsWith("Borrado: Dentista"))
        assertTrue(a.lista.none { it.titulo == "Dentista" })
    }

    @Test fun borraTodoLoDeUnDia() {
        val a = agenda()
        val r = decir("Cancela todo lo de mañana", a)
        assertTrue(mensaje(r), mensaje(r).contains("Borrados 2 eventos"))
        assertEquals(listOf("Gimnasio", "Cumpleaños de Laura"), a.lista.map { it.titulo })
    }

    @Test fun conEmpatePreguntaCual() {
        val a = agenda().apply { guardar(Evento(titulo = "Cena con Luis", inicio = LocalDateTime.of(2026, 9, 25, 21, 0))) }
        val r = decir("Cancela la cena", a)
        assertTrue(mensaje(r), r is Respuesta.Elegir && r.candidatos.size == 2)
        // Al elegir, se borra el elegido y solo ese.
        val elegido = (r as Respuesta.Elegir).candidatos.first { it.titulo == "Cena con Luis" }
        Ejecutor.aplicar(r.leido, elegido, a, ahora)
        assertTrue(a.lista.any { it.titulo == "Cena con Marta" } && a.lista.none { it.titulo == "Cena con Luis" })
    }

    @Test fun sinNadaParecidoLoDiceYNoToca() {
        val a = agenda()
        val r = decir("Cancela la reunión con el jefe", a)
        assertTrue(mensaje(r), r is Respuesta.NoEncontrada)
        assertEquals(4, a.lista.size)
    }

    // --- Cambiar ------------------------------------------------------------

    @Test fun cambiaDeDiaConservandoLaHora() {
        val a = agenda()
        val r = decir("Cambia la cena al sábado", a)
        assertTrue(mensaje(r), mensaje(r).startsWith("Cambiado: Cena con Marta"))
        assertEquals(LocalDateTime.of(2026, 9, 19, 21, 30), a.porTitulo("Cena con Marta").inicio)
    }

    @Test fun cambiaDeHoraConservandoElDia() {
        val a = agenda()
        decir("Mueve el dentista a las 12", a)
        assertEquals(LocalDateTime.of(2026, 9, 18, 12, 0), a.porTitulo("Dentista").inicio)
    }

    @Test fun retrasaUnaHora() {
        val a = agenda()
        decir("Retrasa la cena una hora", a)
        assertEquals(LocalDateTime.of(2026, 9, 18, 22, 30), a.porTitulo("Cena con Marta").inicio)
    }

    @Test fun unoDeTodoElDiaPasaATenerHora() {
        val a = agenda()
        decir("Pasa el cumpleaños de Laura a las 8 de la tarde", a)
        val e = a.porTitulo("Cumpleaños de Laura")
        assertEquals(LocalDateTime.of(2026, 10, 3, 20, 0), e.inicio)
        assertEquals(false, e.todoElDia)
    }

    @Test fun laCenaALasDiezSonLasDiezDeLaNoche() {
        val a = agenda()
        decir("Pasa la cena a las 10", a)
        assertEquals(LocalDateTime.of(2026, 9, 18, 22, 0), a.porTitulo("Cena con Marta").inicio)
    }

    // --- Preguntar ----------------------------------------------------------

    @Test fun agendaDeManana() {
        val r = decir("¿Qué tengo mañana?", agenda())
        assertEquals(
            "Mañana tienes dos cosas: a las 10:00, Dentista; a las 21:30, Cena con Marta.",
            mensaje(r),
        )
    }

    @Test fun diaVacio() {
        val r = decir("¿Tengo algo el domingo?", agenda())
        assertEquals("El domingo 20 no tienes nada.", mensaje(r))
    }

    @Test fun hoySoloLoQueQueda() {
        val a = agenda().apply { guardar(Evento(titulo = "Café", inicio = LocalDateTime.of(2026, 9, 17, 9, 0))) }
        val r = decir("¿Qué tengo hoy?", a)
        assertEquals("Hoy tienes una cosa: a las 19:00, Gimnasio.", mensaje(r))
    }

    @Test fun loProximo() {
        val r = decir("¿Cuál es mi próximo evento?", agenda())
        assertEquals("Lo próximo es Gimnasio, hoy a las 19:00.", mensaje(r))
    }

    @Test fun cuandoEsAlgo() {
        val r = decir("¿Cuándo es el cumpleaños de Laura?", agenda())
        assertEquals("Cumpleaños de Laura es el sábado 3 de octubre.", mensaje(r))
    }

    @Test fun agendaDeLaSemana() {
        val r = decir("¿Qué tengo esta semana?", agenda())
        assertTrue(mensaje(r), mensaje(r).startsWith("Esta semana tienes tres cosas:"))
    }
}
