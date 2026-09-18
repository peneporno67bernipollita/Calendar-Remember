package com.calendarremember

import com.calendarremember.datos.Etiquetas
import com.calendarremember.datos.Evento
import com.calendarremember.voz.Agenda
import com.calendarremember.voz.Ejecutor
import com.calendarremember.voz.Interprete
import com.calendarremember.voz.Respuesta
import com.calendarremember.voz.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Lo que dura varios días, lo que se repite varios días por semana o cada
 * varias semanas, y "borra lo último": de la frase a la agenda.
 * Reloj fijado en viernes 18/09/2026 a las 12:00.
 */
class VariosDiasTest {

    private val ahora = LocalDateTime.of(2026, 9, 18, 12, 0)

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

    private fun viaje() = Evento(
        titulo = "Viaje con Bernie", inicio = LocalDateTime.of(2026, 9, 27, 0, 0), todoElDia = true,
        hasta = LocalDate.of(2026, 10, 25), creado = 0,
    )

    private fun agenda() = Memoria(
        viaje(),
        Evento(titulo = "Reunión", inicio = LocalDateTime.of(2026, 9, 21, 10, 0), creado = 0),
        Evento(titulo = "Dentista", inicio = LocalDateTime.of(2026, 10, 3, 17, 0), creado = 0),
    )

    private fun decir(frase: String, a: Memoria): Respuesta =
        Ejecutor.ejecutar(Interprete.interpretar(frase, ahora), a, ahora)

    // --- Varios días ------------------------------------------------------

    @Test fun apuntaElViajeDelDiaVeintisieteAlVeinticincoDelMesSiguiente() {
        val a = Memoria()
        val r = decir("Añade viaje con Bernie del 27 al 25 del mes siguiente", a)
        assertEquals(
            "Apuntado: Viaje con Bernie, del domingo 27 de septiembre al domingo 25 de octubre.",
            r.mensaje,
        )
        val e = a.porTitulo("Viaje con Bernie")
        assertEquals(LocalDate.of(2026, 9, 27), e.inicio.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 25), e.hasta)
        assertTrue(e.variosDias && e.todoElDia)
        assertTrue(e.ocupa(LocalDate.of(2026, 10, 10)) && !e.ocupa(LocalDate.of(2026, 10, 26)))
    }

    @Test fun unDiaDelViajeLoIncluye() {
        val r = decir("¿Qué tengo el 10 de octubre?", agenda())
        assertEquals(
            "El sábado 10 de octubre tienes una cosa: todo el día, Viaje con Bernie, hasta el domingo 25 de octubre.",
            r.mensaje,
        )
    }

    @Test fun elDiaQueEmpiezaTambien() {
        val r = decir("¿Qué tengo el 3 de octubre?", agenda())
        assertTrue(r.mensaje, r.mensaje.startsWith("El sábado 3 de octubre tienes dos cosas:"))
        assertTrue(r.mensaje, r.mensaje.contains("Viaje con Bernie, hasta el domingo 25 de octubre"))
        assertTrue(r.mensaje, r.mensaje.contains("a las 17:00, Dentista"))
    }

    @Test fun cancelaElViaje() {
        val a = agenda()
        val r = decir("Cancela el viaje con Bernie", a)
        assertEquals("Borrado: Viaje con Bernie, del domingo 27 de septiembre al domingo 25 de octubre.", r.mensaje)
        assertTrue(a.lista.none { it.titulo == "Viaje con Bernie" })
    }

    @Test fun loEncuentraPorUnDiaDeEnMedio() {
        val a = agenda()
        decir("Cancela el viaje del 10 de octubre", a)
        assertTrue(a.lista.none { it.titulo == "Viaje con Bernie" })
    }

    @Test fun moverloLoMueveEntero() {
        val a = agenda()
        decir("Pasa el viaje con Bernie al 28", a)
        val e = a.porTitulo("Viaje con Bernie")
        assertEquals(LocalDate.of(2026, 9, 28), e.inicio.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 26), e.hasta)
    }

    @Test fun alargarHastaUnDia() {
        val a = agenda()
        val r = decir("Alarga el viaje hasta el 30 de octubre", a)
        assertEquals(LocalDate.of(2026, 10, 30), a.porTitulo("Viaje con Bernie").hasta)
        assertTrue(r.mensaje, r.mensaje.startsWith("Cambiado: Viaje con Bernie, ahora del domingo 27"))
    }

    @Test fun alargarYAcortarPorDias() {
        val a = agenda()
        decir("Alarga el viaje dos días", a)
        assertEquals(LocalDate.of(2026, 10, 27), a.porTitulo("Viaje con Bernie").hasta)
        decir("Acorta el viaje una semana", a)
        assertEquals(LocalDate.of(2026, 10, 20), a.porTitulo("Viaje con Bernie").hasta)
    }

    @Test fun elViajeDuraHasta() {
        val a = agenda()
        decir("El viaje con Bernie dura hasta el 2 de noviembre", a)
        assertEquals(LocalDate.of(2026, 11, 2), a.porTitulo("Viaje con Bernie").hasta)
    }

    @Test fun alargarUnaReunionEsAlargarSuDuracion() {
        val a = agenda()
        val r = decir("Alarga la reunión media hora", a)
        assertEquals("Cambiado: Reunión, ahora hasta las 11:30.", r.mensaje)
        assertEquals(90, a.porTitulo("Reunión").duracionMin)
    }

    @Test fun etiquetasCortas() {
        val hoy = ahora.toLocalDate()
        val corta = Etiquetas.corta(viaje(), hoy)
        assertTrue(corta, corta.startsWith("Dom 27 → 25"))
        val enCurso = viaje().copy(inicio = LocalDateTime.of(2026, 9, 17, 0, 0), hasta = LocalDate.of(2026, 9, 20))
        val fin = Etiquetas.dia(LocalDate.of(2026, 9, 20), hoy).replaceFirstChar { it.lowercase() }
        assertEquals("Hasta $fin", Etiquetas.corta(enCurso, hoy))
    }

    @Test fun seGuardaYSeLee() {
        val e = viaje()
        val leido = Evento.deJson(e.aJson())
        assertEquals(e.hasta, leido.hasta)
        // Un evento viejo, sin el campo, se lee como de un solo día.
        val viejo = e.aJson().apply { remove("hasta"); remove("intervalo") }
        assertEquals(null, Evento.deJson(viejo).hasta)
        assertEquals(1, Evento.deJson(viejo).intervalo)
    }

    // --- Repeticiones de varios días -----------------------------------------

    @Test fun losMartesYJueves() {
        val a = Memoria()
        val r = decir("Gimnasio los martes y jueves a las 7", a)
        assertEquals("Apuntado: Gimnasio, todos los martes y jueves a las 19:00, empezando el martes 22.", r.mensaje)
        val dias = a.lista.map { it.inicio.dayOfWeek }.toSet()
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), dias)
        assertTrue(a.lista.all { it.serie == a.lista.first().serie && it.inicio.hour == 19 })
        assertTrue("${a.lista.size}", a.lista.size in 50..54)
        assertEquals(LocalDateTime.of(2026, 9, 24, 19, 0), a.lista.sortedBy { it.inicio }[1].inicio)
    }

    @Test fun cuandoEsAlgoDeVariosDias() {
        val a = Memoria()
        decir("Gimnasio los martes y jueves a las 7", a)
        assertEquals(
            "Gimnasio es todos los martes y jueves a las 19:00. La próxima, el martes 22 a las 19:00.",
            decir("¿Cuándo es el gimnasio?", a).mensaje,
        )
    }

    @Test fun seBorraEntera() {
        val a = Memoria()
        decir("Gimnasio los martes y jueves a las 7", a)
        val r = decir("Borra todos los gimnasios", a)
        assertEquals("Borrado: Gimnasio, todos los martes y jueves a las 19:00.", r.mensaje)
        assertTrue(a.lista.isEmpty())
    }

    @Test fun seAlarganLasDosCadenas() {
        val a = Memoria()
        decir("Gimnasio los martes y jueves a las 7", a)
        val nuevas = Series.alargar(a.lista, LocalDate.of(2027, 1, 20))
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), nuevas.map { it.inicio.dayOfWeek }.toSet())
    }

    @Test fun deLunesAViernesParaDespertar() {
        val a = Memoria()
        val r = decir("Alarma de lunes a viernes a las 7", a)
        assertEquals("Apuntado: Alarma, de lunes a viernes a las 07:00, empezando el lunes 21.", r.mensaje)
        assertEquals(5, a.lista.map { it.inicio.dayOfWeek }.toSet().size)
    }

    @Test fun cadaDosSemanas() {
        val a = Memoria()
        val r = decir("Limpiar el coche cada dos semanas el sábado", a)
        assertEquals("Apuntado: Limpiar el coche, cada dos semanas, los sábados, empezando mañana.", r.mensaje)
        val fechas = a.lista.map { it.inicio.toLocalDate() }.sorted()
        assertEquals(LocalDate.of(2026, 9, 19), fechas[0])
        assertEquals(LocalDate.of(2026, 10, 3), fechas[1])
        assertTrue(a.lista.all { it.intervalo == 2 })
        // Al alargarla sigue saltándose una semana.
        val nuevas = Series.alargar(a.lista, LocalDate.of(2027, 1, 20)).map { it.inicio.toLocalDate() }.sorted()
        assertTrue(nuevas.zipWithNext().all { (x, y) -> y == x.plusWeeks(2) })
    }

    @Test fun cadaQuinceDias() {
        val a = Memoria()
        val r = decir("Regar las plantas cada 15 días", a)
        assertTrue(r.mensaje, r.mensaje.startsWith("Apuntado: Regar las plantas, cada 15 días"))
        val fechas = a.lista.map { it.inicio.toLocalDate() }.sorted()
        assertEquals(fechas[0].plusDays(15), fechas[1])
    }

    // --- Lo último ---------------------------------------------------------

    @Test fun borraLoUltimoQueSeApunto() {
        val a = agenda()
        decir("Apúntame comprar pan mañana", a)
        val r = decir("Borra lo último que he apuntado", a)
        assertTrue(r.mensaje, r.mensaje.startsWith("Borrado: Comprar pan"))
        assertEquals(3, a.lista.size)
    }

    @Test fun borrarLoUltimoSiEraUnaSerieLaBorraEntera() {
        val a = agenda()
        decir("Gimnasio los martes y jueves a las 7", a)
        decir("Borra lo que acabo de apuntar", a)
        assertEquals(3, a.lista.size)
    }

    @Test fun cambiaLoUltimo() {
        val a = agenda()
        decir("Apúntame comprar pan mañana", a)
        decir("Cambia lo último al lunes", a)
        assertEquals(LocalDate.of(2026, 9, 21), a.porTitulo("Comprar pan").inicio.toLocalDate())
    }
}
