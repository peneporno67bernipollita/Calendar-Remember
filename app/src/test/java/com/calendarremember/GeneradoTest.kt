package com.calendarremember

import com.calendarremember.voz.Accion
import com.calendarremember.voz.Consulta
import com.calendarremember.voz.Interpretacion
import com.calendarremember.voz.Interprete
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

/**
 * Miles de frases generadas combinando piezas: formas de pedirlo, nombres de
 * eventos, días y horas, en distintos órdenes, para apuntar, cancelar,
 * cambiar, preguntar y editar. Cada pieza sabe lo que significa, así que cada
 * frase sabe lo que tiene que salir.
 *
 * No sustituye a las frases escritas a mano (FrasesTest): esas cubren las
 * rarezas del habla. Esta cubre las combinaciones, que es donde una regla
 * pisa a otra sin que nadie lo vea venir.
 *
 * Siempre las mismas frases (semilla fija), para que un fallo se pueda repetir.
 * Reloj fijado en miércoles 16/09/2026 a las 09:30.
 */
class GeneradoTest {

    private val ahora = LocalDateTime.of(2026, 9, 16, 9, 30)
    private val hoy = ahora.toLocalDate()

    private fun d(dia: Int, mes: Int, ano: Int = 2026) = LocalDate.of(ano, mes, dia)

    /** Nombres de eventos, con el artículo con que se nombran al cancelarlos o cambiarlos. */
    private val TITULOS = listOf(
        "el|Dentista", "la|Reunión con el jefe", "lo de|Comprar pan", "lo de|Llamar a mamá", "la|Clase de inglés",
        "el|Partido de pádel", "la|Revisión del coche", "el|Cumpleaños de Laura", "lo de|Recoger a los niños",
        "la|Entrega del proyecto", "la|Cita con el médico", "lo de|Pagar el alquiler", "lo de|Ir al banco",
        "el|Vuelo a Roma", "el|Cine con Pablo", "el|Entrenamiento de fútbol", "la|Tutoría del colegio",
        "la|Peluquería", "la|Comida familiar", "el|Examen de conducir", "lo de|Llevar el coche al taller",
        "lo de|Sacar la basura", "lo de|Regar las plantas", "el|Fisioterapeuta", "el|Yoga", "la|Cena de empresa",
        "la|Boda de Juan", "el|Concierto de Coldplay", "lo de|Renovar el DNI", "lo de|Llamar al fontanero",
        "lo de|Tomar la pastilla", "lo de|Ver el partido", "lo de|Estudiar para el examen",
        "lo de|Enviar el informe", "la|Reunión de vecinos", "el|Cumple de Marcos", "la|Visita a la abuela",
        "lo de|Comprar regalo para Ana", "lo de|Devolver el libro a la biblioteca", "lo de|Pasear al perro",
        "la|Clase de guitarra", "lo de|Firmar el contrato", "lo de|Recoger el paquete", "lo de|Lavar el coche",
        "lo de|Hacer la compra", "lo de|Pagar la luz", "la|Cena con Marta", "la|Comida con los compañeros",
        "la|Entrevista de trabajo", "lo de|Reservar el restaurante", "lo de|Llamar al seguro", "el|Tren a Madrid",
        "el|Médico de cabecera", "la|Revisión dental", "lo de|Cortarme el pelo", "lo de|Ir al gimnasio",
        "la|Mudanza de Pedro", "la|Presentación del proyecto", "el|Taller de cerámica", "la|Charla de seguridad",
        "el|Curso de cocina", "la|Barbacoa en casa de Luis", "el|Seguro del coche", "la|Declaración de la renta",
        "la|Graduación de Sara", "el|Torneo de ajedrez", "lo de|Comprar las entradas", "la|Ecografía",
        "el|Análisis de sangre", "lo de|Recoger las llaves", "la|Junta de accionistas", "el|Pediatra",
        "la|Clase de natación", "lo de|Llamar a la abuela", "el|Bautizo de Hugo", "la|Cena de Navidad del trabajo",
    ).map { it.substringBefore('|') to it.substringAfter('|') }

    private val FECHAS = listOf(
        "hoy" to hoy, "mañana" to d(17, 9), "pasado mañana" to d(18, 9),
        "el lunes" to d(21, 9), "el martes" to d(22, 9), "el miércoles" to d(23, 9), "el jueves" to d(17, 9),
        "el viernes" to d(18, 9), "el sábado" to d(19, 9), "el domingo" to d(20, 9),
        "el lunes que viene" to d(21, 9), "el próximo jueves" to d(24, 9), "este viernes" to d(18, 9),
        "este sábado" to d(19, 9), "el 25 de octubre" to d(25, 10), "el 3 de noviembre" to d(3, 11),
        "el 1 de diciembre" to d(1, 12), "el 14 de febrero" to d(14, 2, 2027), "el 30 de septiembre" to d(30, 9),
        "el 25/10" to d(25, 10), "el día 28" to d(28, 9), "el 5" to d(5, 10), "el martes 29" to d(29, 9),
        "dentro de dos semanas" to d(30, 9), "en tres días" to d(19, 9), "la semana que viene" to d(21, 9),
        "el fin de semana" to d(19, 9), "el finde que viene" to d(26, 9), "a finales de mes" to d(30, 9),
        "a principios de octubre" to d(1, 10), "el jueves 1 de octubre" to d(1, 10), "el 12 de octubre" to d(12, 10),
        "el 2 de enero" to d(2, 1, 2027), "el día 20" to d(20, 9), "el sábado que viene" to d(26, 9),
    )

    private val HORAS = listOf(
        "a las 17:30" to LocalTime.of(17, 30), "a las 9 de la mañana" to LocalTime.of(9, 0),
        "a las 5 de la tarde" to LocalTime.of(17, 0), "a las 10 de la noche" to LocalTime.of(22, 0),
        "a las 20:00" to LocalTime.of(20, 0), "a mediodía" to LocalTime.of(14, 0),
        "a las 11 de la mañana" to LocalTime.of(11, 0), "a las 6 y media de la tarde" to LocalTime.of(18, 30),
        "a las 7 menos cuarto de la tarde" to LocalTime.of(18, 45), "a las 16 horas" to LocalTime.of(16, 0),
        "a las cuatro y cuarto de la tarde" to LocalTime.of(16, 15), "a las 21:30" to LocalTime.of(21, 30),
        "por la tarde" to LocalTime.of(17, 0), "a primera hora" to LocalTime.of(8, 0),
        "a las 9 y cuarto de la mañana" to LocalTime.of(9, 15), "a las 13:45" to LocalTime.of(13, 45),
        "a las 8 de la tarde" to LocalTime.of(20, 0), "sobre las 7 de la tarde" to LocalTime.of(19, 0),
        "a las diez y media de la mañana" to LocalTime.of(10, 30), "a las 18:15" to LocalTime.of(18, 15),
    )

    private val PIDO = listOf(
        "", "apunta", "apúntame", "recuérdame", "pon", "ponme", "añade", "añádeme", "anota", "anótame",
        "crea un recordatorio para", "tengo", "no se me olvide", "que no se me olvide", "avísame para",
        "Nébula apunta", "oye Nébula recuérdame", "necesito que me recuerdes", "me puedes apuntar",
        "puedes apuntar", "por favor apunta", "vale apunta", "mete en el calendario", "pon en el calendario",
        "añade al calendario", "no me dejes olvidar", "acuérdate de", "ok Nébula apunta", "hola Nébula ponme",
        "quiero que me apuntes", "me apuntas", "nuevo evento", "crea un evento que se llame", "agéndame",
        "a ver apúntame", "venga apunta", "eh apunta", "bueno apúntame", "Nébula", "oye apunta",
        "¿me apuntas", "apúntame en la agenda", "guárdame", "recuérdame que tengo", "tengo que acordarme de",
    )

    private data class Frase(val texto: String, val comprobar: (Interpretacion) -> String?)

    private fun esperar(accion: Accion, r: Interpretacion) =
        if (r.accion != accion) "acción ${r.accion}, esperaba $accion" else null

    private fun quitarSignos(t: String) = t.replace("¿", "").replace("?", "")

    // --- Apuntar ---------------------------------------------------------------

    private fun apuntar(azar: Random): Frase {
        val (_, titulo) = TITULOS.random(azar)
        val pido = PIDO.random(azar)
        val cual = azar.nextInt(10)
        val fecha = if (cual == 0) null else FECHAS.random(azar)
        val hora = if (cual == 1 || fecha == null) HORAS.random(azar) else if (azar.nextBoolean()) HORAS.random(azar) else null
        val t = titulo.replaceFirstChar { it.lowercase() }
        val f = fecha?.first ?: ""
        val h = hora?.first ?: ""
        val partes = when (azar.nextInt(7)) {
            0 -> listOf(pido, t, f, h)
            1 -> listOf(pido, f, h, t)
            2 -> listOf(pido, f, t, h)
            3 -> listOf(f, h, t)
            4 -> listOf(t, f, h)
            5 -> listOf(pido, t, h, f)
            else -> listOf(f, pido, t, h)
        }
        val texto = partes.filter { it.isNotBlank() }.joinToString(" ").let { if (pido.startsWith("¿")) "$it?" else it }
            .replaceFirstChar { it.uppercase() }
        val dia = fecha?.second ?: hora!!.second.let { if (hoy.atTime(it).isAfter(ahora)) hoy else hoy.plusDays(1) }
        return Frase(texto) { r ->
            esperar(Accion.CREAR, r)
                ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                ?: if (r.inicio.toLocalDate() != dia) "día ${r.inicio.toLocalDate()}, esperaba $dia" else null
                ?: if (hora == null && !r.todoElDia) "hora ${r.inicio.toLocalTime()}, esperaba todo el día" else null
                ?: if (hora != null && (r.todoElDia || r.inicio.toLocalTime() != hora.second))
                    "hora ${if (r.todoElDia) "todo el día" else r.inicio.toLocalTime()}, esperaba ${hora.second}" else null
        }
    }

    // --- Cancelar ------------------------------------------------------------------

    private val CANCELO = listOf(
        "cancela", "borra", "elimina", "quita", "anula", "cancélame", "bórrame", "quiero cancelar",
        "puedes borrar", "oye Nébula cancela", "Nébula borra", "quiero borrar", "necesito cancelar",
        "por favor cancela", "vale, cancela", "desapunta", "olvídate de", "cancela ya", "borra de la agenda",
    )

    private fun cancelar(azar: Random): Frase {
        val (art, titulo) = TITULOS.random(azar)
        val fecha = if (azar.nextInt(3) == 0) null else FECHAS.random(azar)
        val t = titulo.replaceFirstChar { it.lowercase() }
        val objeto = "$art $t" + (fecha?.let { " " + it.first.replace(Regex("^el "), "del ").replace(Regex("^la "), "de la ") } ?: "")
        val texto = when (azar.nextInt(6)) {
            0 -> "Se ha cancelado $objeto"
            1 -> "Me han cancelado $objeto"
            else -> "${CANCELO.random(azar)} $objeto".replaceFirstChar { it.uppercase() }
        }
        return Frase(texto) { r ->
            esperar(Accion.BORRAR, r)
                ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                ?: if (fecha != null && r.inicio.toLocalDate() != fecha.second) "día ${r.inicio.toLocalDate()}, esperaba ${fecha.second}" else null
        }
    }

    // --- Cambiar ---------------------------------------------------------------------

    private val MUEVO = listOf("cambia", "pasa", "mueve", "traslada", "cámbiame", "oye, cambia", "Nébula pasa",
        "me han cambiado", "puedes cambiar", "quiero cambiar", "pospón", "aplaza")

    private fun destinos(azar: Random): Triple<String, LocalDate?, LocalTime?> = when (azar.nextInt(4)) {
        0 -> FECHAS.filter { it.first.startsWith("el ") && !it.first.contains("que viene") && !it.first.contains("próximo") }
            .random(azar).let { Triple("al " + it.first.removePrefix("el "), it.second, null) }
        1 -> HORAS.filter { it.first.startsWith("a las") }.random(azar).let { Triple(it.first, null, it.second) }
        2 -> FECHAS.filter { it.first.startsWith("el ") }.random(azar).let { Triple("para " + it.first, it.second, null) }
        else -> listOf("a mañana" to d(17, 9), "a pasado mañana" to d(18, 9)).random(azar).let { Triple(it.first, it.second, null) }
    }

    private fun cambiar(azar: Random): Frase {
        val (art, titulo) = TITULOS.random(azar)
        val t = titulo.replaceFirstChar { it.lowercase() }
        return if (azar.nextInt(5) == 0) {
            val (cant, min) = listOf("una hora" to 60L, "media hora" to 30L, "dos horas" to 120L,
                "un día" to 1440L, "una semana" to 10080L, "15 minutos" to 15L).random(azar)
            val (verbo, signo) = listOf("retrasa" to 1, "adelanta" to -1, "aplaza" to 1, "atrasa" to 1).random(azar)
            Frase("${verbo.replaceFirstChar { it.uppercase() }} $art $t $cant") { r ->
                esperar(Accion.MOVER, r)
                    ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                    ?: if (r.desplazamientoMin != signo * min) "desplaza ${r.desplazamientoMin}, esperaba ${signo * min}" else null
            }
        } else {
            val (destino, fecha, hora) = destinos(azar)
            val texto = "${MUEVO.random(azar)} $art $t $destino".replaceFirstChar { it.uppercase() }
            Frase(texto) { r ->
                esperar(Accion.MOVER, r)
                    ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                    ?: if (fecha != null && r.nuevaFecha != fecha) "a ${r.nuevaFecha}, esperaba $fecha" else null
                    ?: if (hora != null && r.nuevaHora != hora) "a las ${r.nuevaHora}, esperaba $hora" else null
            }
        }
    }

    // --- Preguntar ---------------------------------------------------------------------

    private val PREGUNTO = listOf("¿Qué tengo %s?", "Qué tengo %s", "¿Tengo algo %s?", "Dime qué tengo %s",
        "¿Qué hay %s?", "¿Qué planes tengo %s?", "¿Estoy libre %s?", "¿Cuántas cosas tengo %s?",
        "Oye Nébula, ¿qué tengo %s?", "¿Qué me toca %s?", "Nébula qué tengo %s", "¿Tengo planes %s?")

    private fun preguntar(azar: Random): Frase {
        if (azar.nextInt(3) == 0) {
            val (art, titulo) = TITULOS.filter { it.first != "lo de" }.random(azar)
            val q = listOf("¿Cuándo es %s?", "¿Cuándo tengo %s?", "¿A qué hora es %s?", "¿Qué día es %s?",
                "¿Cuándo era %s?").random(azar)
            return Frase(q.format("$art ${titulo.replaceFirstChar { it.lowercase() }}")) { r ->
                esperar(Accion.CONSULTAR, r)
                    ?: if (r.consulta != Consulta.CUANDO) "consulta ${r.consulta}" else null
                    ?: if (r.titulo != titulo) "busca «${r.titulo}», esperaba «$titulo»" else null
            }
        }
        val (f, dia) = FECHAS.filter { !it.first.contains("semana") && !it.first.contains("finde") &&
            !it.first.contains("fin de") && !it.first.contains("finales") }.random(azar)
        return Frase(PREGUNTO.random(azar).format(f)) { r ->
            esperar(Accion.CONSULTAR, r)
                ?: if (r.consulta != Consulta.AGENDA) "consulta ${r.consulta}" else null
                ?: if (r.desde != dia) "del ${r.desde}, esperaba $dia" else null
        }
    }

    // --- Editar ------------------------------------------------------------------------

    private fun editar(azar: Random): Frase {
        val (art, titulo) = TITULOS.random(azar)
        val (_, nuevo) = TITULOS.random(azar)
        val t = titulo.replaceFirstChar { it.lowercase() }
        val n = nuevo.replaceFirstChar { it.lowercase() }
        val (texto, conObjeto) = when (azar.nextInt(6)) {
            0 -> "Cámbiale el nombre a $n" to false
            1 -> "Ponle de nombre $n" to false
            2 -> "Llámalo $n" to false
            3 -> "Cambia el nombre de $art $t a $n" to true
            4 -> "Oye, $art $t, cámbiale el nombre a $n" to true
            else -> "Renombra $art $t a $n" to true
        }
        return Frase(texto) { r ->
            esperar(Accion.EDITAR, r)
                ?: if (r.nuevoTitulo != nuevo && r.particiones.none { it.second == nuevo })
                    "nombre nuevo «${r.nuevoTitulo}», esperaba «$nuevo»" else null
                ?: if (conObjeto && r.titulo != titulo && r.particiones.none { (x, y) ->
                        y == nuevo && Interprete.interpretar(x, ahora, soloCrear = true).titulo == titulo })
                    "evento «${r.titulo}», esperaba «$titulo»" else null
        }
    }

    // --- En palabras, como escribe el reconocedor sin conexión -----------------------------

    private val FECHAS_PALABRAS = listOf(
        "el veinticinco de octubre" to d(25, 10), "el tres de noviembre" to d(3, 11), "el uno de diciembre" to d(1, 12),
        "el primero de diciembre" to d(1, 12), "el catorce de febrero" to d(14, 2, 2027), "el día veintiocho" to d(28, 9),
        "el treinta de septiembre" to d(30, 9), "el treinta y uno de octubre" to d(31, 10), "el doce de octubre" to d(12, 10),
        "el veintidós de noviembre" to d(22, 11), "el dos de enero" to d(2, 1, 2027), "el martes veintinueve" to d(29, 9),
        "dentro de dos semanas" to d(30, 9), "en tres días" to d(19, 9), "pasado mañana" to d(18, 9), "mañana" to d(17, 9),
        "el sábado" to d(19, 9), "el lunes" to d(21, 9), "el día veinte" to d(20, 9), "el día cinco" to d(5, 10),
    )

    private val HORAS_PALABRAS = listOf(
        "a las cinco de la tarde" to LocalTime.of(17, 0), "a las nueve de la mañana" to LocalTime.of(9, 0),
        "a las diez y media de la noche" to LocalTime.of(22, 30), "a las once menos cuarto de la mañana" to LocalTime.of(10, 45),
        "a las siete y veinte de la tarde" to LocalTime.of(19, 20), "a la una de la tarde" to LocalTime.of(13, 0),
        "a las ocho y cuarto de la mañana" to LocalTime.of(8, 15), "a las tres y media de la tarde" to LocalTime.of(15, 30),
        "a las seis de la tarde" to LocalTime.of(18, 0), "a las once de la noche" to LocalTime.of(23, 0),
        "a las doce y media del mediodía" to LocalTime.of(12, 30), "a las cuatro menos diez de la tarde" to LocalTime.of(15, 50),
    )

    private fun apuntarEnPalabras(azar: Random): Frase {
        val (_, titulo) = TITULOS.random(azar)
        val pido = listOf("", "apunta", "apúntame", "recuérdame", "ponme", "añade", "nébula apunta", "oye recuérdame",
            "tengo", "no se me olvide").random(azar)
        val fecha = FECHAS_PALABRAS.random(azar)
        val hora = if (azar.nextBoolean()) HORAS_PALABRAS.random(azar) else null
        val t = titulo.lowercase()
        val partes = when (azar.nextInt(4)) {
            0 -> listOf(pido, t, fecha.first, hora?.first ?: "")
            1 -> listOf(pido, fecha.first, hora?.first ?: "", t)
            2 -> listOf(fecha.first, hora?.first ?: "", t)
            else -> listOf(pido, t, hora?.first ?: "", fecha.first)
        }
        // Sin mayúsculas ni signos, que es como sale del reconocedor.
        val texto = partes.filter { it.isNotBlank() }.joinToString(" ")
        return Frase(texto) { r ->
            esperar(Accion.CREAR, r)
                ?: if (r.titulo.lowercase() != titulo.lowercase()) "título «${r.titulo}», esperaba «$titulo»" else null
                ?: if (r.inicio.toLocalDate() != fecha.second) "día ${r.inicio.toLocalDate()}, esperaba ${fecha.second}" else null
                ?: if (hora == null && !r.todoElDia) "hora ${r.inicio.toLocalTime()}, esperaba todo el día" else null
                ?: if (hora != null && (r.todoElDia || r.inicio.toLocalTime() != hora.second))
                    "hora ${if (r.todoElDia) "todo el día" else r.inicio.toLocalTime()}, esperaba ${hora.second}" else null
        }
    }

    // --- Como se habla -----------------------------------------------------------------------

    private fun apuntarColoquial(azar: Random): Frase {
        val (art, titulo) = TITULOS.filter { it.first != "lo de" }.random(azar)
        val fecha = FECHAS.filter { it.first != "hoy" }.random(azar)
        val hora = HORAS.random(azar)
        val t = titulo.replaceFirstChar { it.lowercase() }
        val f = fecha.first.replaceFirstChar { it.uppercase() }
        val texto = when (azar.nextInt(6)) {
            0 -> "$f tengo $t ${hora.first}"
            1 -> "$f ${hora.first} tengo $t"
            2 -> "${art.replaceFirstChar { it.uppercase() }} $t es ${fecha.first} ${hora.first}"
            3 -> "Oye, que $f tengo $t ${hora.first}"
            4 -> "Nébula, ${fecha.first} tengo $t ${hora.first}"
            else -> "Recuérdame que ${fecha.first} tengo $t ${hora.first}"
        }
        return Frase(texto) { r ->
            esperar(Accion.CREAR, r)
                ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                ?: if (r.inicio.toLocalDate() != fecha.second) "día ${r.inicio.toLocalDate()}, esperaba ${fecha.second}" else null
                ?: if (r.todoElDia || r.inicio.toLocalTime() != hora.second)
                    "hora ${if (r.todoElDia) "todo el día" else r.inicio.toLocalTime()}, esperaba ${hora.second}" else null
        }
    }

    private fun cancelarColoquial(azar: Random): Frase {
        val (art, titulo) = TITULOS.filter { it.first != "lo de" }.random(azar)
        val t = titulo.replaceFirstChar { it.lowercase() }
        val pronombre = if (art == "el") "lo" else "la"
        val fecha = FECHAS.filter { it.first.startsWith("el ") && !it.first.contains("día") }.random(azar)
        val del = fecha.first.replaceFirstChar { "d" + it }  // "el lunes" -> "del lunes"
        val texto = when (azar.nextInt(6)) {
            0 -> "${art.replaceFirstChar { it.uppercase() }} $t $del, cancéla$pronombre"
            1 -> "${art.replaceFirstChar { it.uppercase() }} $t $del bórra$pronombre"
            2 -> "Ya no hay $t ${fecha.first}"
            3 -> "Al final no hay $t ${fecha.first}"
            4 -> "Oye, quita $art $t $del"
            else -> "Nébula, elimina $art $t $del"
        }
        return Frase(texto) { r ->
            esperar(Accion.BORRAR, r)
                ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                ?: if (r.inicio.toLocalDate() != fecha.second) "día ${r.inicio.toLocalDate()}, esperaba ${fecha.second}" else null
        }
    }

    private fun cambiarColoquial(azar: Random): Frase {
        val (art, titulo) = TITULOS.filter { it.first != "lo de" }.random(azar)
        val t = titulo.replaceFirstChar { it.lowercase() }
        val pronombre = if (art == "el") "lo" else "la"
        val (destino, fecha, hora) = destinos(azar)
        val texto = when (azar.nextInt(5)) {
            0 -> "${art.replaceFirstChar { it.uppercase() }} $t pása$pronombre $destino"
            1 -> "${art.replaceFirstChar { it.uppercase() }} $t, cámbia$pronombre $destino"
            2 -> "Cambia $art $t $destino"
            3 -> "Oye Nébula, mueve $art $t $destino"
            else -> "Me han cambiado $art $t $destino"
        }
        return Frase(texto) { r ->
            esperar(Accion.MOVER, r)
                ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                ?: if (fecha != null && r.nuevaFecha != fecha) "a ${r.nuevaFecha}, esperaba $fecha" else null
                ?: if (hora != null && r.nuevaHora != hora) "a las ${r.nuevaHora}, esperaba $hora" else null
        }
    }

    private fun editarMas(azar: Random): Frase {
        val (art, titulo) = TITULOS.filter { it.first != "lo de" }.random(azar)
        val t = titulo.replaceFirstChar { it.lowercase() }
        return when (azar.nextInt(4)) {
            0 -> Frase("Ponle una nota a $art $t que diga llevar el DNI") { r ->
                esperar(Accion.EDITAR, r)
                    ?: if (r.nuevaNota != "Llevar el DNI") "nota «${r.nuevaNota}»" else null
                    ?: if (r.titulo != titulo) "evento «${r.titulo}», esperaba «$titulo»" else null
            }
            1 -> Frase("Pon $art $t en verde") { r ->
                esperar(Accion.EDITAR, r)
                    ?: if (r.nuevoColor != "VERDE") "color ${r.nuevoColor}" else null
                    ?: if (r.titulo != titulo) "evento «${r.titulo}», esperaba «$titulo»" else null
            }
            2 -> Frase("Avísame un día antes de $art $t") { r ->
                esperar(Accion.EDITAR, r)
                    ?: if (r.nuevosAvisos != listOf(1440, 0)) "avisos ${r.nuevosAvisos}" else null
                    ?: if (r.titulo != titulo) "evento «${r.titulo}», esperaba «$titulo»" else null
            }
            else -> Frase("Ponle una alarma una hora antes") { r ->
                esperar(Accion.EDITAR, r) ?: if (r.nuevosAvisos != listOf(60, 0)) "avisos ${r.nuevosAvisos}" else null
            }
        }
    }

    // --- Corregirse a mitad de frase ---------------------------------------------------------

    private val CORRIJO = listOf("no", "no, no", "digo", "perdón", "quiero decir", "mejor dicho", "no, mejor",
        "o sea", "no, no, no")

    private fun corregir(azar: Random): Frase {
        val (_, titulo) = TITULOS.random(azar)
        val t = titulo.replaceFirstChar { it.lowercase() }
        val pido = listOf("", "apunta", "ponme", "recuérdame", "Nébula apunta", "añade", "oye, ponme").random(azar)
        val c = CORRIJO.random(azar)
        val soloDias = FECHAS.filter { it.first.startsWith("el ") && !it.first.contains("/") && !it.first.contains("día") }
        return if (azar.nextBoolean()) {
            // El día: "ponme el martes la cena, no, el jueves".
            val primera = soloDias.random(azar)
            val buena = soloDias.filter { it.second != primera.second }.random(azar)
            val hora = if (azar.nextBoolean()) HORAS.random(azar) else null
            val h = hora?.first?.let { " $it" } ?: ""
            val texto = when (azar.nextInt(3)) {
                0 -> "$pido ${primera.first} $t$h, $c, ${buena.first}"
                1 -> "$pido $t ${primera.first}$h, $c, ${buena.first}"
                else -> "$pido ${primera.first}, $c, ${buena.first}, $t$h"
            }.trim().replaceFirstChar { it.uppercase() }
            // "El 3 de noviembre, digo, el 5": el 5 de noviembre, si no ha pasado.
            val esperado = if (buena.first == "el 5")
                primera.second.withDayOfMonth(5).takeIf { !it.isBefore(hoy) } ?: buena.second
            else buena.second
            Frase(texto) { r ->
                esperar(Accion.CREAR, r)
                    ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                    ?: if (r.inicio.toLocalDate() != esperado) "día ${r.inicio.toLocalDate()}, esperaba $esperado" else null
                    ?: if (hora != null && r.inicio.toLocalTime() != hora.second) "hora ${r.inicio.toLocalTime()}, esperaba ${hora.second}" else null
            }
        } else {
            // La hora: "la cena el sábado a las 9, digo, a las 10 de la noche".
            val fecha = soloDias.random(azar)
            val horasClaras = HORAS.filter { it.first.startsWith("a las") }
            val primera = horasClaras.random(azar)
            val buena = horasClaras.filter { it.second != primera.second }.random(azar)
            val texto = "$pido $t ${fecha.first} ${primera.first}, $c, ${buena.first}".trim().replaceFirstChar { it.uppercase() }
            Frase(texto) { r ->
                esperar(Accion.CREAR, r)
                    ?: if (r.titulo != titulo) "título «${r.titulo}», esperaba «$titulo»" else null
                    ?: if (r.inicio.toLocalDate() != fecha.second) "día ${r.inicio.toLocalDate()}, esperaba ${fecha.second}" else null
                    ?: if (r.todoElDia || r.inicio.toLocalTime() != buena.second)
                        "hora ${if (r.todoElDia) "todo el día" else r.inicio.toLocalTime()}, esperaba ${buena.second}" else null
            }
        }
    }

    // --- Todo junto -----------------------------------------------------------------------

    private fun generar(): List<Frase> {
        val azar = Random(20260916)
        return List(6000) { apuntar(azar) } + List(1500) { cancelar(azar) } + List(1500) { cambiar(azar) } +
            List(1000) { preguntar(azar) } + List(600) { editar(azar) } + List(2000) { apuntarEnPalabras(azar) } +
            List(1500) { apuntarColoquial(azar) } + List(800) { cancelarColoquial(azar) } +
            List(800) { cambiarColoquial(azar) } + List(400) { editarMas(azar) } + List(2000) { corregir(azar) }
    }

    @Test fun milesDeFrasesGeneradas() {
        val frases = generar()
        val fallos = mutableListOf<String>()
        for (f in frases) {
            val r = Interprete.interpretar(f.texto, ahora)
            f.comprobar(r)?.let { fallos += "«${f.texto}»: $it" }
        }
        val muestra = fallos.distinct().take(60)
        assertTrue(
            "\n${fallos.size} de ${frases.size} frases mal. Algunas:\n" + muestra.joinToString("\n") { "  - $it" },
            fallos.isEmpty(),
        )
    }
}
