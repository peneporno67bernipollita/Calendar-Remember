package com.calendarremember

import com.calendarremember.voz.Accion
import com.calendarremember.voz.Consulta
import com.calendarremember.voz.Interprete
import com.calendarremember.voz.Repeticion
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Frases tal como las escribe el reconocedor de voz de Google cuando se le
 * habla con naturalidad. Reloj fijado en jueves 17/09/2026 a las 12:00.
 *
 * Cada bloque junta todos sus fallos en un solo mensaje: con cien frases, ir
 * arreglando de una en una no tiene sentido.
 */
class CorpusTest {

    private val ahora = LocalDateTime.of(2026, 9, 17, 12, 0)

    private fun fecha(r: com.calendarremember.voz.Interpretacion): String {
        val i = r.inicio
        val dia = if (i.year == 2026) "%02d/%02d".format(i.dayOfMonth, i.monthValue)
            else "%02d/%02d/%d".format(i.dayOfMonth, i.monthValue, i.year)
        return if (r.todoElDia) "$dia todo" else "$dia %02d:%02d".format(i.hour, i.minute)
    }

    private fun revisar(fallos: List<String>, total: Int) {
        assertTrue(
            "\n${fallos.size} de $total frases mal:\n" + fallos.joinToString("\n") { "  - $it" },
            fallos.isEmpty(),
        )
    }

    // --- Apuntar ----------------------------------------------------------

    private val CREAR = listOf(
        Triple("Mañana a las 5 cena con Marta", "Cena con Marta", "18/09 17:00"),
        Triple("Cena con Marta mañana a las 17:30", "Cena con Marta", "18/09 17:30"),
        Triple("Apúntame el dentista el martes a las 10:15", "Dentista", "22/09 10:15"),
        Triple("Recuérdame llamar a mi madre a las 8 de la tarde", "Llamar a mi madre", "17/09 20:00"),
        Triple("Recuérdame que tengo que llamar a Juan mañana", "Llamar a Juan", "18/09 todo"),
        Triple("Tengo médico el lunes por la mañana", "Médico", "21/09 09:00"),
        Triple("Reunión el viernes por la tarde", "Reunión", "18/09 17:00"),
        Triple("El sábado por la noche fiesta en casa de Pablo", "Fiesta en casa de Pablo", "19/09 21:00"),
        Triple("Comida con los abuelos el domingo a mediodía", "Comida con los abuelos", "20/09 14:00"),
        Triple("Dentro de media hora sacar la ropa de la lavadora", "Sacar la ropa de la lavadora", "17/09 12:30"),
        Triple("En 10 minutos apagar el horno", "Apagar el horno", "17/09 12:10"),
        Triple("En una hora llamar al fontanero", "Llamar al fontanero", "17/09 13:00"),
        Triple("Cumpleaños de Laura el 3 de octubre", "Cumpleaños de Laura", "03/10 todo"),
        Triple("El 1 de noviembre comida familiar", "Comida familiar", "01/11 todo"),
        Triple("El primero de diciembre pagar el seguro", "Pagar el seguro", "01/12 todo"),
        Triple("Pagar la luz el día 5", "Pagar la luz", "05/10 todo"),
        Triple("El 22 a las 9 entrevista de trabajo", "Entrevista de trabajo", "22/09 09:00"),
        Triple("El martes 29 revisión del coche", "Revisión del coche", "29/09 todo"),
        Triple("Gimnasio a las 7 de la mañana", "Gimnasio", "18/09 07:00"),
        Triple("A las 9 de la noche ver el partido", "Ver el partido", "17/09 21:00"),
        Triple("Clase de inglés a las 6 y cuarto", "Clase de inglés", "17/09 18:15"),
        Triple("Llamar a Pedro a las cinco menos diez", "Llamar a Pedro", "17/09 16:50"),
        Triple("A las doce y media comer con Ana", "Comer con Ana", "17/09 12:30"),
        Triple("A la una comer con Ana", "Comer con Ana", "17/09 13:00"),
        Triple("A la 1 y media comida de empresa", "Comida de empresa", "17/09 13:30"),
        Triple("Sobre las 6 pasar por el taller", "Pasar por el taller", "17/09 18:00"),
        Triple("Reunión de 5 a 7", "Reunión", "17/09 17:00"),
        Triple("Partido de pádel el jueves que viene a las 20:00", "Partido de pádel", "24/09 20:00"),
        Triple("La semana que viene revisar las facturas", "Revisar las facturas", "21/09 todo"),
        Triple("Pasado mañana a las 11 recoger el paquete", "Recoger el paquete", "19/09 11:00"),
        Triple("Esta tarde ir a la farmacia", "Ir a la farmacia", "17/09 17:00"),
        Triple("Hoy a las 8 y media cena", "Cena", "17/09 20:30"),
        Triple("Mañana a las 8 y media desayuno con Luis", "Desayuno con Luis", "18/09 08:30"),
        Triple("Recoger a los niños a las 5 de la tarde", "Recoger a los niños", "17/09 17:00"),
        Triple("Examen de conducir el 15 de octubre a las 9:30", "Examen de conducir", "15/10 09:30"),
        Triple("Boda de Ana el 6 de junio del año que viene", "Boda de Ana", "06/06/2027 todo"),
        Triple("Cena de Nochebuena en casa de los abuelos", "Cena de Nochebuena en casa de los abuelos", "24/12 todo"),
        Triple("Nochevieja en Madrid", "Nochevieja en Madrid", "31/12 todo"),
        Triple("Apunta que el lunes viene el fontanero a las 10", "Viene el fontanero", "21/09 10:00"),
        Triple("Que no se me olvide comprar leche mañana", "Comprar leche", "18/09 todo"),
        Triple("Avísame mañana a las 9 para llamar al banco", "Llamar al banco", "18/09 09:00"),
        Triple("Ponme una alarma a las 7 de la mañana", "Alarma", "18/09 07:00"),
        Triple("Me toca ir al dentista el jueves que viene", "Ir al dentista", "24/09 todo"),
        Triple("Quedada con los amigos el sábado a las diez de la noche", "Quedada con los amigos", "19/09 22:00"),
        Triple("Cita con el médico el 30 de septiembre a las 4 y media", "Cita con el médico", "30/09 16:30"),
        Triple("Mañana a primera hora llamar a la gestoría", "Llamar a la gestoría", "18/09 08:00"),
        Triple("Vacaciones del 1 al 15 de agosto", "Vacaciones", "01/08/2027 todo"),
        Triple("A las 17:45 recoger a Lucía", "Recoger a Lucía", "17/09 17:45"),
        Triple("A las 5 p. m. llamada con el cliente", "Llamada con el cliente", "17/09 17:00"),
        Triple("Mañana 10:30 dentista", "Dentista", "18/09 10:30"),
        Triple("El viernes 18 cena", "Cena", "18/09 todo"),
        Triple("Recuérdame dentro de 2 días devolver el libro", "Devolver el libro", "19/09 todo"),
        Triple("Dentro de una semana llamar a Carlos", "Llamar a Carlos", "24/09 todo"),
        Triple("Reunión con el equipo a las 10 en la oficina", "Reunión con el equipo en la oficina", "18/09 10:00"),
        Triple("Hay reunión mañana a las 10", "Reunión", "18/09 10:00"),
        Triple("Hay que llamar al casero el lunes", "Llamar al casero", "21/09 todo"),
        Triple("Cena el viernes a las 21 horas", "Cena", "18/09 21:00"),
        Triple("Recoger el coche en el taller el miércoles a las 19", "Recoger el coche en el taller", "23/09 19:00"),
        Triple("El lunes que viene dentista", "Dentista", "21/09 todo"),
        Triple("Este sábado cumpleaños de Pablo", "Cumpleaños de Pablo", "19/09 todo"),
        Triple("Mañana por la tarde partido", "Partido", "18/09 17:00"),
        Triple("Cine el domingo por la noche", "Cine", "20/09 21:00"),
        Triple("Clase de yoga todos los martes a las 7", "Clase de yoga", "22/09 19:00"),
        Triple("Cada día a las 9 tomar la pastilla", "Tomar la pastilla", "18/09 09:00"),
        Triple("Llamar a la abuela el día 1 de octubre", "Llamar a la abuela", "01/10 todo"),
        Triple("El 25/12 comida de Navidad", "Comida de Navidad", "25/12 todo"),
        Triple("Recoger a Pablo a las 13:00", "Recoger a Pablo", "17/09 13:00"),
        Triple("Enviar el informe el martes antes de las 12", "Enviar el informe", "22/09 12:00"),
        Triple("Recuérdame mañana llamar a Marta a las 11 y veinte", "Llamar a Marta", "18/09 11:20"),
        Triple("Viaje a Londres el 12 de noviembre a las 6 de la mañana", "Viaje a Londres", "12/11 06:00"),
        Triple("Esta noche a las 11 sacar al perro", "Sacar al perro", "17/09 23:00"),
        Triple("Cena a las 10", "Cena", "17/09 22:00"),
        Triple("A las 12 de la noche tomar la medicación", "Tomar la medicación", "18/09 00:00"),
        Triple("Recordatorio: comprar pan", "Comprar pan", "17/09 todo"),
        Triple("Recoge las entradas del concierto el jueves 1 de octubre", "Recoge las entradas del concierto", "01/10 todo"),
        Triple("El viernes que viene cena de empresa", "Cena de empresa", "25/09 todo"),
        Triple("Nébula, apúntame el dentista mañana a las 4", "Dentista", "18/09 16:00"),
        Triple("Oye apunta cumpleaños de mamá el 3 de mayo", "Cumpleaños de mamá", "03/05/2027 todo"),
        Triple("Mañana a las 12 del mediodía comida con Luis", "Comida con Luis", "18/09 12:00"),
        Triple("Recoger a Lucas del colegio a las 2", "Recoger a Lucas del colegio", "17/09 14:00"),
        Triple("Reunión desde las 4 hasta las 6", "Reunión", "17/09 16:00"),
        Triple("Llamar al banco el lunes a las 9 en punto", "Llamar al banco", "21/09 09:00"),
        Triple("A eso de las 7 pasear al perro", "Pasear al perro", "17/09 19:00"),
        Triple("San Valentín cena romántica", "San Valentín cena romántica", "14/02/2027 todo"),
        // Segunda tanda: frases que fallaban la primera vez que se probaron.
        Triple("Dentro de 3 horas y media recoger a Pablo", "Recoger a Pablo", "17/09 15:30"),
        Triple("Recuérdame a las 6 menos veinte llamar al taller", "Llamar al taller", "17/09 17:40"),
        Triple("El jueves 24 a las 18:30 fisio", "Fisio", "24/09 18:30"),
        Triple("Fisioterapia el 24 a las seis y media", "Fisioterapia", "24/09 18:30"),
        Triple("Tengo que ir a recoger el DNI el lunes 28 a las 9 y cuarto", "Ir a recoger el DNI", "28/09 09:15"),
        Triple("Apúntame para el viernes la cena de empresa", "Cena de empresa", "18/09 todo"),
        Triple("Recuerda que el domingo es el cumpleaños de mi hermano", "Cumpleaños de mi hermano", "20/09 todo"),
        Triple("Pon en el calendario que mañana tengo examen", "Examen", "18/09 todo"),
        Triple("Añade un evento: reunión con Pedro el martes a las 11", "Reunión con Pedro", "22/09 11:00"),
        Triple("Crea un recordatorio para comprar pan a las 8 de la tarde", "Comprar pan", "17/09 20:00"),
        Triple("Mañana a las nueve menos cuarto clase", "Clase", "18/09 08:45"),
        Triple("A las 9 de la mañana del sábado ir al mercado", "Ir al mercado", "19/09 09:00"),
        Triple("Ir al mercado el sábado por la mañana temprano", "Ir al mercado", "19/09 08:00"),
        Triple("Pagar la hipoteca el último día del mes", "Pagar la hipoteca", "30/09 todo"),
        Triple("Cena en casa de Luis a las nueve", "Cena en casa de Luis", "17/09 21:00"),
        Triple("Llamar a la tía Carmen esta semana", "Llamar a la tía Carmen", "17/09 todo"),
        Triple("Recoger el traje el martes o el miércoles", "Recoger el traje", "22/09 todo"),
        Triple("El 15 a las 10 de la mañana revisión médica", "Revisión médica", "15/10 10:00"),
        Triple("Quedar con Luis a las 7 de la tarde en el centro", "Quedar con Luis en el centro", "17/09 19:00"),
        Triple("Recuérdame que el martes que viene a las 10 tengo reunión con el jefe", "Reunión con el jefe", "22/09 10:00"),
        // Lo que devuelve el reconocedor sin conexión: sin tildes y con los
        // pronombres separados.
        Triple("apunta me cena con marta el viernes", "Cena con marta", "18/09 todo"),
        Triple("recuerda me llamar a juan manana a las cinco", "Llamar a juan", "18/09 17:00"),
    )

    @Test fun apuntar() {
        val fallos = CREAR.mapNotNull { (frase, titulo, cuando) ->
            val r = Interprete.interpretar(frase, ahora)
            val real = fecha(r)
            when {
                r.accion != Accion.CREAR -> "«$frase» → ${r.accion}, debía apuntar"
                r.titulo != titulo || real != cuando ->
                    "«$frase» → «${r.titulo}» $real (esperado «$titulo» $cuando)"
                else -> null
            }
        }
        revisar(fallos, CREAR.size)
    }

    @Test fun duraciones() {
        val casos = listOf(
            "Reunión de 5 a 7" to 120,
            "Reunión desde las 4 hasta las 6" to 120,
            "Clase de 10 a 11 y media" to 90,
            "Examen mañana a las 9 durante dos horas" to 120,
            "Partido entre las 6 y las 8" to 120,
        )
        val fallos = casos.mapNotNull { (frase, min) ->
            val r = Interprete.interpretar(frase, ahora)
            if (r.duracionMin != min) "«$frase» → ${r.duracionMin} min (esperado $min)" else null
        }
        revisar(fallos, casos.size)
    }

    @Test fun repeticiones() {
        val casos = listOf(
            "Clase de yoga todos los martes a las 7" to Repeticion.SEMANAL,
            "Cada día a las 9 tomar la pastilla" to Repeticion.DIARIA,
            "Todos los días a las 8 sacar al perro" to Repeticion.DIARIA,
            "Pagar el alquiler todos los meses el día 1" to Repeticion.MENSUAL,
            "Cumpleaños de mamá el 3 de mayo todos los años" to Repeticion.ANUAL,
            "Cena con Marta el viernes" to Repeticion.NINGUNA,
        )
        val fallos = casos.mapNotNull { (frase, rep) ->
            val r = Interprete.interpretar(frase, ahora)
            if (r.repeticion != rep) "«$frase» → ${r.repeticion} (esperado $rep)" else null
        }
        revisar(fallos, casos.size)
    }

    // --- Cancelar ---------------------------------------------------------

    @Test fun cancelar() {
        data class C(val frase: String, val criterio: String, val fecha: LocalDate?, val hora: LocalTime?)
        val casos = listOf(
            C("Borra el cumpleaños del sábado", "Cumpleaños", LocalDate.of(2026, 9, 19), null),
            C("Elimina la cita del dentista", "Cita del dentista", null, null),
            C("Quita la alarma de mañana", "Alarma", LocalDate.of(2026, 9, 18), null),
            C("Anula la reunión de las 10", "Reunión", null, LocalTime.of(10, 0)),
            C("Ya no voy al gimnasio mañana", "Gimnasio", LocalDate.of(2026, 9, 18), null),
            C("Oye, cancela la cena con Marta", "Cena con Marta", null, null),
        )
        val fallos = casos.mapNotNull { c ->
            val r = Interprete.interpretar(c.frase, ahora)
            val f = if (r.fechaDicha) r.inicio.toLocalDate() else null
            val h = if (r.horaDicha) r.inicio.toLocalTime() else null
            if (r.accion != Accion.BORRAR || r.titulo != c.criterio || f != c.fecha || h != c.hora)
                "«${c.frase}» → ${r.accion} «${r.titulo}» $f $h (esperado «${c.criterio}» ${c.fecha} ${c.hora})"
            else null
        }
        revisar(fallos, casos.size)
    }

    // --- Cambiar ----------------------------------------------------------

    @Test fun cambiar() {
        data class M(
            val frase: String, val criterio: String, val deFecha: LocalDate?,
            val aFecha: LocalDate?, val aHora: LocalTime?, val desplazamiento: Long?,
        )
        val v18 = LocalDate.of(2026, 9, 18)
        val casos = listOf(
            M("Cambia la cena del viernes al sábado", "Cena", v18, LocalDate.of(2026, 9, 19), null, null),
            M("Mueve el dentista a las 6", "Dentista", null, null, LocalTime.of(18, 0), null),
            M("Pasa la reunión de mañana al jueves a las 10", "Reunión", v18, LocalDate.of(2026, 9, 24), LocalTime.of(10, 0), null),
            M("Retrasa la cena una hora", "Cena", null, null, null, 60),
            M("Adelanta la reunión media hora", "Reunión", null, null, null, -30),
            M("Aplaza el médico una semana", "Médico", null, null, null, 10080),
            M("Cámbiame la cita del dentista al martes que viene a las 5 y media", "Cita del dentista", null, LocalDate.of(2026, 9, 22), LocalTime.of(17, 30), null),
            M("Quiero cambiar la cena al domingo", "Cena", null, LocalDate.of(2026, 9, 20), null, null),
            M("La cena de mañana pásala a las 10 de la noche", "Cena", v18, null, LocalTime.of(22, 0), null),
            M("Retrasa 15 minutos la llamada", "Llamada", null, null, null, 15),
            M("Mueve la visita a la abuela al sábado", "Visita a la abuela", null, LocalDate.of(2026, 9, 19), null, null),
            M("Pasa el partido a las 8 de la tarde", "Partido", null, null, LocalTime.of(20, 0), null),
            M("Me han cambiado la reunión al jueves", "Reunión", null, LocalDate.of(2026, 9, 24), null, null),
            M("Me han retrasado la cena una hora", "Cena", null, null, null, 60),
            // Una cena a las 10 son las 22:00 aunque el "a las 10" vaya solo.
            M("Pasa la cena de las 9 a las 10", "Cena", null, null, LocalTime.of(22, 0), null),
            M("Retrasa el dentista al viernes", "Dentista", null, v18, null, null),
            M("Cambia el cumpleaños de Laura al 4 de octubre", "Cumpleaños de Laura", null, LocalDate.of(2026, 10, 4), null, null),
            M("Pásame la reunión del lunes al martes", "Reunión", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22), null, null),
        )
        val fallos = casos.mapNotNull { c ->
            val r = Interprete.interpretar(c.frase, ahora)
            val de = if (r.fechaDicha) r.inicio.toLocalDate() else null
            if (r.accion != Accion.MOVER || r.titulo != c.criterio || de != c.deFecha ||
                r.nuevaFecha != c.aFecha || r.nuevaHora != c.aHora || r.desplazamientoMin != c.desplazamiento)
                "«${c.frase}» → ${r.accion} «${r.titulo}» de=$de a=${r.nuevaFecha} ${r.nuevaHora} " +
                    "desp=${r.desplazamientoMin} (esperado «${c.criterio}» de=${c.deFecha} " +
                    "a=${c.aFecha} ${c.aHora} desp=${c.desplazamiento})"
            else null
        }
        revisar(fallos, casos.size)
    }

    /** Tareas que llevan el verbo pero son para apuntar, no para cambiar nada. */
    @Test fun cambiarNoSeConfundeConTareas() {
        val casos = listOf(
            "Cambiar el aceite del coche el lunes",
            "Recuérdame cambiar la rueda a las 5",
            "Pasar por el taller mañana",
            "Cambia el aceite del coche el lunes",
            "Pasa por casa de mamá mañana",
        )
        val fallos = casos.mapNotNull { frase ->
            val r = Interprete.interpretar(frase, ahora)
            if (r.accion != Accion.CREAR) "«$frase» → ${r.accion}, debía apuntar" else null
        }
        revisar(fallos, casos.size)
    }

    // --- Preguntar --------------------------------------------------------

    @Test fun preguntar() {
        data class Q(val frase: String, val tipo: Consulta, val desde: LocalDate?, val hasta: LocalDate?, val criterio: String = "")
        val d = { dia: Int, mes: Int -> LocalDate.of(2026, mes, dia) }
        val casos = listOf(
            Q("¿Qué tengo mañana?", Consulta.AGENDA, d(18, 9), d(18, 9)),
            Q("Qué tengo hoy", Consulta.AGENDA, d(17, 9), d(17, 9)),
            Q("¿Tengo algo el viernes?", Consulta.AGENDA, d(18, 9), d(18, 9)),
            Q("¿Qué hay para esta semana?", Consulta.AGENDA, d(17, 9), d(20, 9)),
            Q("¿Qué tengo la semana que viene?", Consulta.AGENDA, d(21, 9), d(27, 9)),
            Q("¿Qué tengo este finde?", Consulta.AGENDA, d(19, 9), d(20, 9)),
            Q("¿Cuál es mi próximo evento?", Consulta.PROXIMO, null, null),
            Q("¿Qué es lo próximo que tengo?", Consulta.PROXIMO, null, null),
            Q("¿Cuándo es el cumpleaños de Laura?", Consulta.CUANDO, null, null, "Cumpleaños de Laura"),
            Q("¿A qué hora es la cena?", Consulta.CUANDO, null, null, "Cena"),
            Q("Dime qué tengo pasado mañana", Consulta.AGENDA, d(19, 9), d(19, 9)),
            Q("Léeme la agenda de mañana", Consulta.AGENDA, d(18, 9), d(18, 9)),
            Q("Qué tengo", Consulta.AGENDA, d(17, 9), d(17, 9)),
            Q("Nébula, ¿qué tengo el lunes?", Consulta.AGENDA, d(21, 9), d(21, 9)),
            Q("¿Qué tengo que hacer mañana?", Consulta.AGENDA, d(18, 9), d(18, 9)),
            Q("¿Tengo planes el sábado?", Consulta.AGENDA, d(19, 9), d(19, 9)),
            Q("¿Qué hay el 3 de octubre?", Consulta.AGENDA, d(3, 10), d(3, 10)),
            Q("que tengo manana", Consulta.AGENDA, d(18, 9), d(18, 9)),
            Q("¿Qué planes tengo el finde?", Consulta.AGENDA, d(19, 9), d(20, 9)),
            Q("¿Cuándo tengo el dentista?", Consulta.CUANDO, null, null, "Dentista"),
            Q("¿Tengo algo hoy?", Consulta.AGENDA, d(17, 9), d(17, 9)),
            Q("Lo próximo que tengo", Consulta.PROXIMO, null, null),
            Q("¿Qué tengo esta tarde?", Consulta.AGENDA, d(17, 9), d(17, 9)),
        )
        val fallos = casos.mapNotNull { q ->
            val r = Interprete.interpretar(q.frase, ahora)
            if (r.accion != Accion.CONSULTAR || r.consulta != q.tipo || r.desde != q.desde ||
                r.hasta != q.hasta || r.titulo != q.criterio)
                "«${q.frase}» → ${r.accion} ${r.consulta} ${r.desde}..${r.hasta} «${r.titulo}» " +
                    "(esperado ${q.tipo} ${q.desde}..${q.hasta} «${q.criterio}»)"
            else null
        }
        revisar(fallos, casos.size)
    }

    /** Lo que se parece a una pregunta pero es algo que apuntar. */
    @Test fun preguntarNoSeConfundeConApuntar() {
        val casos = listOf(
            "Recuérdame que tengo que llamar a Juan mañana",
            "Hay reunión mañana a las 10",
            "Tengo dentista el lunes",
            "Tengo que ir al médico el martes",
        )
        val fallos = casos.mapNotNull { frase ->
            val r = Interprete.interpretar(frase, ahora)
            if (r.accion != Accion.CREAR) "«$frase» → ${r.accion}, debía apuntar" else null
        }
        revisar(fallos, casos.size)
    }
}
