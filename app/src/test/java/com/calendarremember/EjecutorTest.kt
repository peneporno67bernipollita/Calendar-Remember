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

    // --- Lo que se repite ---------------------------------------------------

    @Test fun apuntaUnaSerie() {
        val a = agenda()
        val r = decir("Clase de yoga todos los martes a las 7", a)
        assertEquals(
            "Apuntado: Clase de yoga, todos los martes a las 19:00, empezando el martes 22.",
            mensaje(r),
        )
        val yoga = a.lista.filter { it.titulo == "Clase de yoga" }.sortedBy { it.inicio }
        // Medio año de martes, todos a la misma hora y de la misma serie.
        assertTrue("${yoga.size} clases", yoga.size in 25..27)
        assertEquals(LocalDateTime.of(2026, 9, 22, 19, 0), yoga.first().inicio)
        assertEquals(LocalDateTime.of(2026, 9, 29, 19, 0), yoga[1].inicio)
        assertTrue(yoga.all { it.serie == yoga.first().serie && it.inicio.dayOfWeek == java.time.DayOfWeek.TUESDAY })
    }

    @Test fun cadaDiaSuenaSoloASuHora() {
        val a = agenda()
        decir("Cada día a las 9 tomar la pastilla", a)
        val pastillas = a.lista.filter { it.titulo == "Tomar la pastilla" }
        assertTrue("${pastillas.size} días", pastillas.size in 59..61)
        // Un aviso el día antes para algo de cada día sería ruido.
        assertTrue(pastillas.all { it.avisos == listOf(0) })
    }

    @Test fun cancelarUnaDeLaSerieEsLaProxima() {
        val a = agenda()
        decir("Clase de yoga todos los martes a las 7", a)
        val antes = a.lista.count { it.titulo == "Clase de yoga" }
        val r = decir("Cancela la clase de yoga", a)
        assertTrue(mensaje(r), mensaje(r).startsWith("Borrado: Clase de yoga, el martes 22"))
        assertTrue(mensaje(r), mensaje(r).contains("Las demás siguen"))
        assertEquals(antes - 1, a.lista.count { it.titulo == "Clase de yoga" })
        assertTrue(a.lista.none { it.titulo == "Clase de yoga" && it.inicio.dayOfMonth == 22 && it.monthValue() == 9 })
    }

    private fun Evento.monthValue() = inicio.monthValue

    @Test fun cancelarTodaLaSerie() {
        val a = agenda()
        decir("Clase de yoga todos los martes a las 7", a)
        val r = decir("Borra todas las clases de yoga", a)
        assertEquals("Borrado: Clase de yoga, todos los martes a las 19:00.", mensaje(r))
        assertTrue(a.lista.none { it.titulo == "Clase de yoga" })
        assertEquals(4, a.lista.size)
    }

    @Test fun cuandoEsAlgoQueSeRepite() {
        val a = agenda()
        decir("Clase de yoga todos los martes a las 7", a)
        val r = decir("¿Cuándo es la clase de yoga?", a)
        assertEquals(
            "Clase de yoga es todos los martes a las 19:00. La próxima, el martes 22 a las 19:00.",
            mensaje(r),
        )
    }

    @Test fun cadaMesSinArrastrarElDia() {
        // Una serie del 31: en los meses de 30 cae el 30, pero el mes
        // siguiente vuelve al 31.
        val base = Evento(titulo = "Nómina", inicio = LocalDateTime.of(2026, 10, 31, 0, 0), todoElDia = true)
        val serie = com.calendarremember.voz.Series.crear(
            base, com.calendarremember.voz.Repeticion.MENSUAL, LocalDateTime.of(2026, 9, 17, 12, 0).toLocalDate(),
        )
        assertEquals(listOf(31, 30, 31, 31), serie.take(4).map { it.inicio.dayOfMonth })
    }

    @Test fun lasSeriesSeAlargan() {
        val a = agenda()
        decir("Cada día a las 9 tomar la pastilla", a)
        val ultimaAntes = a.lista.filter { it.titulo == "Tomar la pastilla" }.maxOf { it.inicio }
        // Cuarenta días después, le queda menos de la mitad: se alarga.
        val dentroDe40 = LocalDateTime.of(2026, 10, 27, 12, 0).toLocalDate()
        val nuevas = com.calendarremember.voz.Series.alargar(a.lista, dentroDe40)
        assertTrue("${nuevas.size} nuevas", nuevas.isNotEmpty())
        assertTrue(nuevas.all { it.inicio.isAfter(ultimaAntes) && it.inicio.hour == 9 })
        assertEquals(dentroDe40.plusDays(60), nuevas.maxOf { it.inicio }.toLocalDate())
        // Y si aún llega de sobra, no se toca.
        assertTrue(com.calendarremember.voz.Series.alargar(a.lista, ahora.toLocalDate()).isEmpty())
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
