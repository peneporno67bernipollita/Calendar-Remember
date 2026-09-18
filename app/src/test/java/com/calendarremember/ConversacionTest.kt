package com.calendarremember

import com.calendarremember.datos.ColorEvento
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
 * Hablar con Nébula como con una persona: lo que se acaba de decir da
 * contexto a lo siguiente ("cámbiale el nombre", "cancélalo"), y cuando falta
 * algo, pregunta y se le contesta hablando.
 * Reloj fijado en sábado 19/09/2026 a las 12:00.
 */
class ConversacionTest {

    private val ahora = LocalDateTime.of(2026, 9, 19, 12, 0)

    /** Una agenda en memoria que recuerda de qué se está hablando. */
    private class Memoria(vararg iniciales: Evento) : Agenda {
        val lista = iniciales.toMutableList()
        var contexto: String? = null
        override val eventos: List<Evento> get() = lista.toList()
        override fun guardar(evento: Evento) {
            val i = lista.indexOfFirst { it.id == evento.id }
            if (i >= 0) lista[i] = evento else lista.add(evento)
        }
        override fun borrar(id: String) { lista.removeAll { it.id == id } }
        override val enContexto: Evento? get() = lista.firstOrNull { it.id == contexto }
        override fun ponerEnContexto(evento: Evento?) { contexto = evento?.id }
        fun porTitulo(t: String) = lista.first { it.titulo == t }
    }

    private fun agenda() = Memoria(
        Evento(titulo = "Dentista", inicio = LocalDateTime.of(2026, 9, 21, 10, 0), creado = 0),
        Evento(titulo = "Cena con Marta", inicio = LocalDateTime.of(2026, 9, 25, 21, 30), creado = 0),
        Evento(titulo = "Visita a la abuela", inicio = LocalDateTime.of(2026, 9, 27, 0, 0), todoElDia = true, creado = 0),
    )

    private fun decir(frase: String, a: Memoria): Respuesta =
        Ejecutor.ejecutar(Interprete.interpretar(frase, ahora), a, ahora)

    private fun contestar(pregunta: Respuesta, frase: String, a: Memoria): Respuesta =
        Ejecutor.responder(pregunta, frase, a, ahora)

    // --- Lo que pasó de verdad -------------------------------------------------

    @Test fun unEventoSinNombrePreguntaQueApunta() {
        val a = agenda()
        val r = decir("Ponme un evento para mañana", a)
        assertTrue(r.toString(), r is Respuesta.Preguntar)
        assertEquals("¿Qué apunto para mañana?", r.mensaje)
        assertEquals(3, a.lista.size)
        val fin = contestar(r, "Cena con Ana", a)
        assertEquals("Apuntado: Cena con Ana, mañana.", fin.mensaje)
        assertEquals(LocalDateTime.of(2026, 9, 20, 0, 0), a.porTitulo("Cena con Ana").inicio)
    }

    @Test fun laRespuestaPuedeTraerLaHora() {
        val a = agenda()
        val r = decir("Recuérdame algo mañana", a)
        val fin = contestar(r, "llamar a Pedro a las 5", a)
        assertEquals("Apuntado: Llamar a Pedro, mañana a las 17:00.", fin.mensaje)
    }

    @Test fun sinRespuestaSeApuntaIgualYSeDiceComoCambiarlo() {
        val a = agenda()
        val r = decir("Ponme un evento para mañana", a)
        val fin = contestar(r, "", a)
        assertTrue(fin.mensaje, fin.mensaje.startsWith("Apuntado: Recordatorio, mañana"))
        assertTrue(fin.mensaje, fin.mensaje.contains("cámbiale el nombre"))
        // Y luego, lo que se dijo aquel día: ya no apunta otra cosa.
        val cambio = decir("Oye, el evento de mañana, cámbiale el nombre a cena con Ana", a)
        assertTrue(cambio.mensaje, cambio.mensaje.startsWith("Cambiado: Recordatorio"))
        assertTrue(cambio.mensaje, cambio.mensaje.contains("ahora se llama «Cena con Ana»"))
        assertEquals(4, a.lista.size)
        assertEquals(LocalDateTime.of(2026, 9, 20, 0, 0), a.porTitulo("Cena con Ana").inicio)
    }

    @Test fun decirQueNoEsNoApuntarNada() {
        val a = agenda()
        val r = decir("Apunta algo para el lunes", a)
        assertEquals("Vale, no apunto nada.", contestar(r, "nada, déjalo", a).mensaje)
        assertEquals(3, a.lista.size)
    }

    // --- El contexto ------------------------------------------------------

    @Test fun cambialeElNombreAloQueSeAcabaDeApuntar() {
        val a = agenda()
        decir("Mañana a las 10 reunión", a)
        val r = decir("Cámbiale el nombre a reunión con el jefe", a)
        assertTrue(r.mensaje, r.mensaje.contains("ahora se llama «Reunión con el jefe»"))
        assertTrue(a.lista.any { it.titulo == "Reunión con el jefe" })
        assertTrue(a.lista.none { it.titulo == "Reunión" })
    }

    @Test fun cancelaloDespuesDePreguntar() {
        val a = agenda()
        assertEquals("Dentista es pasado mañana a las 10:00.", decir("¿Cuándo es el dentista?", a).mensaje)
        val r = decir("Cancélalo", a)
        assertEquals("Borrado: Dentista, pasado mañana a las 10:00.", r.mensaje)
        assertTrue(a.lista.none { it.titulo == "Dentista" })
    }

    @Test fun retrasaloUnaHora() {
        val a = agenda()
        decir("¿Cuándo es la cena con Marta?", a)
        decir("Retrásala una hora", a)
        assertEquals(LocalDateTime.of(2026, 9, 25, 22, 30), a.porTitulo("Cena con Marta").inicio)
    }

    @Test fun sinContextoPregunta() {
        val a = agenda()
        assertEquals("Dime qué cancelo.", decir("Cancélalo", a).mensaje)
    }

    @Test fun unaCorreccion() {
        val a = agenda()
        decir("¿Cuándo es la cena con Marta?", a)
        val r = decir("No es con Marta, es con Ana", a)
        assertTrue(r.mensaje, r.mensaje.contains("ahora se llama «Cena con Ana»"))
        assertTrue(a.lista.any { it.titulo == "Cena con Ana" })
    }

    @Test fun cambiarUnaPalabraPorOtra() {
        val a = agenda()
        decir("Cambia Marta por Laura", a)
        assertTrue(a.lista.any { it.titulo == "Cena con Laura" })
    }

    @Test fun elNombreDeUnoConcreto() {
        val a = agenda()
        decir("Cambia el nombre de la visita a la abuela a comida familiar", a)
        assertTrue(a.lista.any { it.titulo == "Comida familiar" && it.todoElDia })
        assertTrue(a.lista.none { it.titulo == "Visita a la abuela" })
    }

    @Test fun unaNotaUnColorYUnAviso() {
        val a = agenda()
        decir("Cena del viernes con los del trabajo a las 9", a)
        decir("Ponle una nota que diga reservar mesa", a)
        decir("Ponlo en verde", a)
        decir("Avísame dos días antes", a)
        val e = a.porTitulo("Cena con los del trabajo")
        assertEquals("Reservar mesa", e.notas)
        assertEquals(ColorEvento.VERDE, e.color)
        assertEquals(listOf(2880, 0), e.avisos)
    }

    @Test fun avisarAntesDeUnoQueYaExiste() {
        val a = agenda()
        val r = decir("Avísame una hora antes del dentista", a)
        assertTrue(r.mensaje, r.mensaje.contains("te aviso una hora antes"))
        assertEquals(listOf(60, 0), a.porTitulo("Dentista").avisos)
        assertEquals(3, a.lista.size)
    }

    @Test fun noLoApuntaDosVeces() {
        val a = agenda()
        val r = decir("Apunta dentista el lunes a las 10", a)
        assertEquals("Ya lo tenías apuntado: Dentista, pasado mañana a las 10:00.", r.mensaje)
        assertEquals(3, a.lista.size)
    }

    // --- Elegir hablando ---------------------------------------------------

    private fun conDosCenas() = agenda().apply {
        guardar(Evento(titulo = "Cena con Luis", inicio = LocalDateTime.of(2026, 9, 26, 21, 0), creado = 0))
    }

    @Test fun cualBorroSeContestaHablando() {
        val a = conDosCenas()
        val r = decir("Cancela la cena", a)
        assertTrue(r is Respuesta.Elegir)
        val fin = contestar(r, "la de Luis", a)
        assertTrue(fin.mensaje, fin.mensaje.startsWith("Borrado: Cena con Luis"))
        assertTrue(a.lista.any { it.titulo == "Cena con Marta" })
    }

    @Test fun porOrden() {
        val a = conDosCenas()
        val r = decir("Cancela la cena", a) as Respuesta.Elegir
        val primero = r.candidatos.first().titulo
        contestar(r, "el primero", a)
        assertTrue(a.lista.none { it.titulo == primero })
    }

    @Test fun porElDia() {
        val a = conDosCenas()
        val r = decir("Cancela la cena", a)
        contestar(r, "la del viernes", a)
        assertTrue(a.lista.none { it.titulo == "Cena con Marta" })
        assertTrue(a.lista.any { it.titulo == "Cena con Luis" })
    }

    @Test fun ningunoNoTocaNada() {
        val a = conDosCenas()
        val r = decir("Cancela la cena", a)
        assertEquals("Vale, no toco nada.", contestar(r, "ninguno", a).mensaje)
        assertEquals(4, a.lista.size)
    }

    @Test fun siAlNoEncontrado() {
        val a = agenda()
        val r = decir("Cancela la reunión con el jefe", a)
        assertTrue(r is Respuesta.NoEncontrada)
        val fin = contestar(r, "sí", a)
        assertTrue(fin.mensaje, fin.mensaje.startsWith("Apuntado:"))
    }
}
