package com.calendarremember

import com.calendarremember.voz.Interprete
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * Casos reales de dictado. El reloj se fija en jueves 17/09/2026 a las 12:00
 * para que los relativos ("mañana", "el lunes que viene") sean deterministas.
 *
 * Estas pruebas corren en cada compilación: si una regla del intérprete se
 * rompe, el APK no llega a generarse.
 */
class InterpreteTest {

    private val ahora: LocalDateTime = LocalDateTime.of(2026, 9, 17, 12, 0)

    private fun comprobar(frase: String, titulo: String, cuando: String) {
        val r = Interprete.interpretar(frase, ahora)
        val fecha = "%02d/%02d".format(r.inicio.dayOfMonth, r.inicio.monthValue)
        val real = if (r.todoElDia) "$fecha todo el día"
            else "$fecha %02d:%02d".format(r.inicio.hour, r.inicio.minute)
        assertEquals("título de «$frase»", titulo, r.titulo)
        assertEquals("fecha de «$frase»", cuando, real)
    }

    @Test fun citaConDiaDeLaSemanaYMediaHora() =
        comprobar("añádeme cita con el dentista el martes a las cinco y media",
            "Cita con el dentista", "22/09 17:30")

    @Test fun recordatorioParaManana() =
        comprobar("recuérdame comprar pan mañana a las nueve",
            "Comprar pan", "18/09 09:00")

    @Test fun fechaConMesYSinHora() =
        comprobar("cumpleaños de mi madre el 25 de octubre",
            "Cumpleaños de mi madre", "25/10 todo el día")

    @Test fun pasadoMananaConDuracion() =
        comprobar("reunión de equipo pasado mañana a las 10 de la mañana durante dos horas",
            "Reunión de equipo", "19/09 10:00")

    @Test fun dentroDeDosHoras() =
        comprobar("dentro de dos horas llamar a Juan",
            "Llamar a Juan", "17/09 14:00")

    @Test fun diaDelMesQueYaPasoSaltaAlSiguiente() =
        comprobar("apunta pagar el alquiler el día 1",
            "Pagar el alquiler", "01/10 todo el día")

    @Test fun horaConMinutosExplicitos() =
        comprobar("cena con Marta el viernes a las 21:30",
            "Cena con Marta", "18/09 21:30")

    /** El "3" de "3/11" no debe robarle el sitio a la hora real. */
    @Test fun fechaNumericaConHoraYAviso() =
        comprobar("médico el 3/11 a las 8 de la mañana avísame dos días antes",
            "Médico", "03/11 08:00")

    /** Un número suelto es parte del título, no una hora. */
    @Test fun numeroSueltoNoEsHora() =
        comprobar("comprar 2 barras de pan",
            "Comprar 2 barras de pan", "17/09 todo el día")

    @Test fun estaNoche() =
        comprobar("esta noche sacar la basura",
            "Sacar la basura", "17/09 21:00")

    /**
     * Dicho un jueves, "el lunes que viene" es el lunes siguiente (21), que
     * es como se entiende en España. Una versión anterior lo mandaba al 28, y
     * esta prueba daba ese error por bueno.
     */
    @Test fun lunesQueViene() =
        comprobar("tengo que llevar el coche al taller el lunes que viene",
            "Llevar el coche al taller", "21/09 todo el día")

    /** "una" es número: no puede confundirse con la hora de la frase. */
    @Test fun menosCuartoDeLaManana() =
        comprobar("pon una alarma a las siete menos cuarto de la mañana",
            "Alarma", "18/09 06:45")

    /**
     * "de la mañana" es el tramo del día, no el día siguiente. Dicho a las
     * 7:00, esto es para dentro de dos horas, no para mañana.
     */
    @Test fun deLaMananaNoEsElDiaSiguiente() {
        val aLasSiete = LocalDateTime.of(2026, 9, 17, 7, 0)
        val r = Interprete.interpretar("reunión a las nueve de la mañana", aLasSiete)
        assertEquals("Reunión", r.titulo)
        assertEquals(LocalDateTime.of(2026, 9, 17, 9, 0), r.inicio)
    }

    /** La misma frase, cuando esa hora ya pasó, sí salta al día siguiente. */
    @Test fun horaQueYaPasoSaltaAlDiaSiguiente() {
        val r = Interprete.interpretar("reunión a las nueve de la mañana", ahora)
        assertEquals(LocalDateTime.of(2026, 9, 18, 9, 0), r.inicio)
    }

    @Test fun avisoExplicitoSeRespeta() {
        val r = Interprete.interpretar("médico el 3/11 a las 8 avísame dos días antes", ahora)
        assertEquals(listOf(2880, 0), r.avisos)
    }

    @Test fun duracionSeRecoge() {
        val r = Interprete.interpretar("reunión mañana a las 10 durante dos horas", ahora)
        assertEquals(120, r.duracionMin)
    }

    @Test fun sinFechaNiHoraEsHoyYDudoso() {
        val r = Interprete.interpretar("comprar leche", ahora)
        assertEquals("Comprar leche", r.titulo)
        assertEquals(true, r.todoElDia)
    }
}
