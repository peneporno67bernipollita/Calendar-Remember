package com.calendarremember.voz

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Convierte una frase dictada en español en una orden para el calendario.
 *
 * No hay modelo de lenguaje detrás: son reglas. Es instantáneo, funciona sin
 * red y, sobre todo, falla de forma predecible: cuando no entiende algo lo
 * dice (confianza baja) en vez de inventárselo.
 *
 * Entiende cuatro cosas: apuntar, cancelar, cambiar de fecha u hora, y
 * preguntar qué hay en la agenda.
 */

enum class Confianza { ALTA, MEDIA, BAJA }

/** Lo que pide la frase. EDITAR: nombre, nota, color o avisos de uno que ya existe. */
enum class Accion { CREAR, BORRAR, MOVER, CONSULTAR, EDITAR }

/** Qué se pregunta: la agenda de unos días, lo próximo, o cuándo es algo. */
enum class Consulta { AGENDA, PROXIMO, CUANDO }

/** La repetición que se dijo: "todos los martes", "cada día"... */
enum class Repeticion { NINGUNA, DIARIA, SEMANAL, MENSUAL, ANUAL }

data class Interpretacion(
    val accion: Accion,
    /**
     * Para CREAR es el título del evento. Para BORRAR, MOVER y la consulta
     * CUANDO es lo que hay que buscar entre los eventos ya guardados.
     */
    val titulo: String,
    val inicio: LocalDateTime,
    val todoElDia: Boolean,
    val avisos: List<Int>,
    val duracionMin: Int?,
    /**
     * Si la frase decía una fecha. Al buscar un evento hay que distinguir
     * entre "el viernes" y el día de hoy puesto por defecto.
     */
    val fechaDicha: Boolean,
    /** Igual con la hora: "cancela lo de las diez" busca por la hora. */
    val horaDicha: Boolean,
    val dictado: String,
    val confianza: Confianza,
    val repeticion: Repeticion = Repeticion.NINGUNA,
    /** Si los avisos se dijeron ("avísame dos días antes") o son los de siempre. */
    val avisosDichos: Boolean = false,
    // Para MOVER: a dónde va el evento. Lo que no se dijo, no se toca.
    val nuevaFecha: LocalDate? = null,
    val nuevaHora: LocalTime? = null,
    /** "retrásala una hora": minutos de más (o de menos, si es negativo). */
    val desplazamientoMin: Long? = null,
    // Para CONSULTAR.
    val consulta: Consulta? = null,
    val desde: LocalDate? = null,
    /**
     * El último día. Al preguntar, el de los días por los que se pregunta; al
     * apuntar, el de un evento que dura varios ("viaje del 27 al 3").
     */
    val hasta: LocalDate? = null,
    // Para alargar o acortar algo que dura varios días.
    val nuevoHasta: LocalDate? = null,
    /** "alarga la reunión media hora", "acorta el viaje dos días": minutos de más o de menos. */
    val alargarMin: Long? = null,
    /** "Cada dos semanas": cada cuántas veces se repite. */
    val intervalo: Int = 1,
    /** "Los martes y jueves": los días de la semana, si son varios. */
    val diasSemana: List<DayOfWeek> = emptyList(),
    /** "Borra lo último que he apuntado": el evento creado más recientemente. */
    val elUltimo: Boolean = false,
    // Para EDITAR. Lo que no se dijo, no se toca.
    val nuevoTitulo: String? = null,
    /**
     * "Cambia el nombre de la visita a la abuela a comida": sin saber qué
     * eventos hay, no se sabe dónde acaba lo que se cambia y dónde empieza el
     * nombre nuevo. Van todas las maneras de partirlo; el Ejecutor se queda
     * con la que encaja con un evento de verdad.
     */
    val particiones: List<Pair<String, String>> = emptyList(),
    val nuevaNota: String? = null,
    /** El nombre de un ColorEvento: CIAN, MAGENTA, VIOLETA, VERDE, AMBAR. */
    val nuevoColor: String? = null,
    /** "No es cena, es comida": qué trozo del título se cambia por cuál. */
    val reemplazo: Pair<String, String>? = null,
    val nuevosAvisos: List<Int>? = null,
)

object Interprete {

    private val NUMEROS = mapOf(
        "cero" to 0, "un" to 1, "una" to 1, "uno" to 1, "primero" to 1, "dos" to 2,
        "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
        "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
        "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16,
        "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20,
        "veintiuno" to 21, "veintiun" to 21, "veintidos" to 22, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
        // Los compuestos antes que sus comienzos: "treinta y uno" antes que "treinta".
        "treinta y uno" to 31, "treinta y un" to 31, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
    )

    private val MESES = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5,
        "junio" to 6, "julio" to 7, "agosto" to 8, "septiembre" to 9,
        "setiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12,
    )

    private val DIAS = mapOf(
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY, "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY,
    )

    /**
     * Fiestas con fecha fija. No se borran del título: en "cena de
     * Nochebuena" la fiesta es parte de lo que se apunta, además de la fecha.
     */
    private val FIESTAS = listOf(
        "nochebuena" to (12 to 24), "navidad" to (12 to 25), "nochevieja" to (12 to 31),
        "ano nuevo" to (1 to 1), "reyes" to (1 to 6), "san valentin" to (2 to 14),
        "halloween" to (10 to 31), "noche de san juan" to (6 to 23), "san juan" to (6 to 24),
    )

    /**
     * Verbos y muletillas con los que arranca un dictado. Se quitan del título
     * porque "recuérdame comprar pan" es un evento llamado "Comprar pan".
     * "cita" no está en la lista: "cita con el dentista" es un buen título.
     * El orden importa: las formas largas van antes que sus prefijos.
     */
    private val ARRANQUES = listOf(
        // Al dictar se tiende a llamar a la app por su nombre antes de pedir
        // nada: "Nébula, apúntame...". No es parte de lo que se apunta.
        "ok nebula", "okey nebula", "ok", "okey", "okay", "nebula", "oye", "eh", "hola", "vale", "venga", "bueno",
        "por favor", "a ver", "pues nada", "pues", "ahora", "tio", "tia", "bro", "colega", "macho", "chaval",
        "casi mejor", "mejor",
        "acordarme de", "acordarme", "recordarme que", "recordarme", "que me acuerde de", "que me acuerde",
        "me acuerde de",
        "que no se me olvide", "no se me olvide", "no me dejes olvidar", "no olvides",
        "acuerdate de", "acuerdate",
        // Pedirlo con rodeos: "necesito que me recuerdes llamar al fontanero".
        "necesito que me recuerdes que", "necesito que me recuerdes", "necesito que me avises de que",
        "necesito que me avises", "quiero que me recuerdes que", "quiero que me recuerdes",
        "quiero que me avises de que", "quiero que me avises", "me tienes que recordar que",
        "me tienes que recordar", "me tienes que avisar de que", "me tienes que avisar",
        "tienes que recordarme que", "tienes que recordarme", "me puedes recordar que", "me puedes recordar",
        "puedes recordarme que", "puedes recordarme", "me podrias recordar que", "me podrias recordar",
        "me recuerdas que", "me recuerdas", "me puedes apuntar", "me podrias apuntar", "puedes apuntar",
        "me apuntas", "recuerdamelo", "avisame cuando", "sin falta",
        "pon en el calendario", "ponlo en el calendario", "apunta en el calendario",
        "anade al calendario", "anade en el calendario", "mete en el calendario",
        "recuerdame que tengo que", "recuerdame que", "recuerda que", "recuerda me", "recuerdame", "recuerda",
        "avisame para", "avisame de que", "avisame de", "avisame que", "avisame", "avisa",
        "anademe", "anade", "apunta me", "apuntame", "apunta que", "apunta",
        "anotame", "anota que", "anota", "agendame", "agenda", "pon me", "ponme", "pon",
        "creame", "crea", "programame", "programa", "meteme", "mete", "guardame", "guarda",
        "tengo que", "tengo", "hay que", "hay", "me toca", "toca",
        // "Estoy de vacaciones hasta el domingo" se apunta como "Vacaciones".
        "estoy de", "estamos de", "estare de", "estaremos de", "voy a estar de",
        // "Crea un evento que se llame cena de empresa".
        "un evento que se llame", "un recordatorio que se llame", "un evento llamado", "un recordatorio llamado",
        "una alarma para", "que se llame",
        "en el calendario", "al calendario", "para el calendario",
    )

    /** "Quiero que me apuntes", "necesito que me pongas", "¿me puedes recordar?"... */
    private const val PEDIR = """(?:quiero|necesito|me\s+gustaria|podrias|puedes|me\s+puedes|me\s+podrias|te\s+pido)""" +
        """\s+(?:que\s+me\s+|que\s+)?(?:apuntes|pongas|recuerdes|avises|anotes|anadas|guardes|agendes|crees|metas|""" +
        """apuntar(?:me)?|poner(?:me)?|recordar(?:me)?|avisar(?:me)?|anotar(?:me)?|anadir(?:me)?|guardar(?:me)?|""" +
        """agendar(?:me)?|crear(?:me)?|meter(?:me)?)(?:\s+(?:que|de\s+que|para))?"""

    private val REGEX_ARRANQUES by lazy {
        Regex("""^(?:$PEDIR|${ARRANQUES.joinToString("|") { it.replace(" ", """\s+""") }})\b[\s,:.]*""")
    }

    /**
     * Cómo se corrige uno al hablar: "no", "no, no", "digo", "perdón",
     * "quiero decir", "mejor dicho". Tras "no" no puede ir un verbo ("no hay",
     * "no voy", "no se me olvide"): eso es otra cosa.
     */
    private val CORRECCION = Regex(
        """\b(?:no\s*[,.]?\s*(?:no\s*[,.]?\s*)*(?!(?:es|era|sera|son|hay|habra|voy|vamos|va|puedo|puede|podemos|""" +
            """se|me|te|le|lo|la|nos|olvides|olvide|tengo|tienes)\b)|(?:perdon|digo|quiero\s+decir|o\s+sea|""" +
            """mejor\s+dicho|me\s+equivoco|me\s+he\s+equivocado|rectifico)\s*[,.]?\s*)(?:mejor\s+|en\s+realidad\s+)?"""
    )

    /** Lo que va delante de un número que es un día: "el 20", "el día 20". */
    private val DELANTE_DE_UN_DIA = Regex("""\b(?:el|del|al|dia|este|hasta|martes|lunes|miercoles|jueves|viernes|sabado|domingo)\s*$""")

    private val GENERICO = Regex("""^(?:(?:un|una|el|nuevo)\s+)?(?:evento|recordatorio)""" +
        """(?:\s+(?:que\s+se\s+llame|llamado|de\s+nombre|para|de|que\s+diga))?\b(?!\s+(?:en|con|a|al)\b)[\s,:.]*""")

    /** Con qué se empieza a hablar antes de pedir nada. */
    private const val MULETILLAS = """(?:ok|okey|okay|nebula|nevula|oye|eh|hola|vale|venga|bueno|por\s+favor|a\s+ver|pues|tio|tia|""" +
        """quiero|quisiera|necesito|puedes|podrias|me\s+puedes|me\s+podrias|""" +
        """haz\s+el\s+favor\s+de|que|y)"""

    /**
     * Pistas de que se habla de la tarde o la noche: con ellas, "a las diez"
     * son las 22:00 ("cena a las diez", "esta noche a las once").
     */
    private val DE_NOCHE = Regex("""\b(?:cena|cenar|cenamos|copas?|fiesta|discoteca|concierto|botellon|""" +
        """esta\s+noche|por\s+la\s+noche|de\s+noche|esta\s+tarde|por\s+la\s+tarde)\b""")

    /**
     * Frases de estar en algo más que de hacer algo: "estoy de vacaciones
     * hasta el jueves" empieza hoy; sin esto, "hasta el jueves" sería un plazo.
     */
    private val ESTADO = Regex("""\b(?:estoy|estamos|esta|estan|de\s+vacaciones|de\s+viaje|de\s+baja|de\s+permiso|""" +
        """de\s+puente|fuera|vacaciones|viaje)\b""")

    /**
     * Pistas de que "a las siete" es por la mañana: despertarse, madrugar,
     * una alarma. Sin ellas, de la una a las siete se entiende la tarde.
     */
    private val DE_MANANA = Regex("""\b(?:despiert\w*|despertar\w*|levantarme|levantame|levantarnos|desayun\w*|""" +
        """madrug\w*|alarma|despertador)\b""")

    /**
     * La primera palabra de un título tal como se dice al pedirlo, y cómo
     * queda mejor apuntada: "avísame que saque la pizza" es "Sacar la pizza";
     * "cenamos en casa de Pedro", "Cena en casa de Pedro".
     */
    private val MEJOR_DICHO = mapOf(
        "cenamos" to "Cena", "comemos" to "Comida", "desayunamos" to "Desayuno", "merendamos" to "Merienda",
        "despiertame" to "Despertar", "despertarme" to "Despertar", "levantarme" to "Despertar",
        "levantame" to "Despertar",
        "saque" to "Sacar", "llame" to "Llamar", "compre" to "Comprar", "vaya" to "Ir", "recoja" to "Recoger",
        "pague" to "Pagar", "lleve" to "Llevar", "haga" to "Hacer", "ponga" to "Poner", "envie" to "Enviar",
        "escriba" to "Escribir", "devuelva" to "Devolver", "apague" to "Apagar", "riegue" to "Regar",
        "tome" to "Tomar", "mire" to "Mirar", "tienda" to "Tender", "tire" to "Tirar", "baje" to "Bajar",
        "reserve" to "Reservar", "pida" to "Pedir", "prepare" to "Preparar", "limpie" to "Limpiar",
        "revise" to "Revisar", "renueve" to "Renovar", "cambie" to "Cambiar", "felicite" to "Felicitar",
        "conteste" to "Contestar", "entregue" to "Entregar", "estudie" to "Estudiar", "coja" to "Coger",
        "traiga" to "Traer", "busque" to "Buscar", "lave" to "Lavar", "termine" to "Terminar",
        "confirme" to "Confirmar", "descongele" to "Descongelar", "saquemos" to "Sacar",
    )

    /** Lo que no es un título por sí solo, aunque sea lo único que quede. */
    private val SOLO_ENLACE = setOf(
        "de", "del", "el", "la", "lo", "los", "las", "a", "al", "en", "que", "para",
        "por", "un", "una", "y", "con", "antes",
    )

    /**
     * Sustitución 1:1 para normalizar SIN que cambien las posiciones de los
     * caracteres. Es lo que permite buscar sobre el texto sin tildes y luego
     * recortar sobre el original conservando las tildes del título.
     */
    private const val CON_TILDE = "áéíóúüñàèìòùâêç"
    private const val SIN_TILDE = "aeiouunaeiouaec"

    private fun normalizar(texto: String): String = buildString {
        for (c in texto.lowercase()) {
            val i = CON_TILDE.indexOf(c)
            append(if (i >= 0) SIN_TILDE[i] else c)
        }
    }

    private fun numero(palabra: String?): Int? {
        val limpio = palabra?.trim() ?: return null
        return limpio.toIntOrNull() ?: NUMEROS[limpio]
    }

    private val N: String by lazy { """\d{1,2}|""" + NUMEROS.keys.joinToString("|") }
    private val MES: String by lazy { MESES.keys.joinToString("|") }
    private val DIA_SEMANA: String by lazy { DIAS.keys.joinToString("|") }

    // --- Un día dicho, antes de saber qué fecha es ------------------------

    /**
     * "el 27", "el 3 de octubre", "el lunes", "mañana", "el 25 del mes que
     * viene". Se guarda lo que se dijo y no la fecha, porque en un tramo la
     * fecha de una punta depende de la otra: en "del 27 al 3", el 3 es el del
     * mes siguiente al 27.
     */
    private data class DiaDicho(
        val dia: Int? = null,
        val mes: Int? = null,
        val ano: Int? = null,
        /** "del mes que viene": 1; "de este mes": 0. */
        val mesesMas: Int? = null,
        val semana: DayOfWeek? = null,
        val deLaQueViene: Boolean = false,
        val este: Boolean = false,
        /** hoy 0, mañana 1, pasado mañana 2. */
        val relativo: Int? = null,
    ) {
        /** Trae su propio mes: no depende de la otra punta del tramo. */
        val conMes: Boolean get() = mes != null || mesesMas != null
        /** Un número y nada más: "de 5 a 7" son horas, no días. */
        val soloNumero: Boolean get() = dia != null && mes == null && mesesMas == null && semana == null
    }

    private const val MES_RELATIVO =
        """del?\s+(?:mes\s+(?:que\s+viene|siguiente|proximo)|proximo\s+mes|siguiente\s+mes|este\s+mes)"""

    private val DIA_CON_MES by lazy {
        Regex("""(?:(?:el|la)\s+)?(?:($DIA_SEMANA)\s+)?(?:dia\s+)?($N)\s+de\s+($MES)""" +
            """(?:\s+(?:del?\s+)?(\d{4})|\s+(del\s+(?:ano\s+que\s+viene|proximo\s+ano)))?\b""")
    }
    private val DIA_CIFRAS by lazy {
        Regex("""(?:el\s+)?(?:($DIA_SEMANA)\s+)?(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")
    }
    private val DIA_NUMERO by lazy {
        Regex("""(?:el\s+)?(?:($DIA_SEMANA)\s+)?(?:dia\s+)?(\d{1,2}|$N)(\s+$MES_RELATIVO)?\b""" +
            """(?!\s*(?:[:.]\d|de\s+la|y\s+(?:media|cuarto)|menos\s|h\b|horas|minutos|dias|semanas|meses))""")
    }
    private val DIA_SEMANA_SOLO by lazy {
        Regex("""(?:el\s+|este\s+|(?:el\s+)?proximo\s+)?($DIA_SEMANA)""" +
            """(\s+que\s+viene|\s+proximo|\s+de\s+la\s+(?:semana\s+que\s+viene|proxima\s+semana))?\b""")
    }
    private val DIA_RELATIVO = Regex("""(pasado\s+manana|manana|hoy)\b""")

    /** El día que empieza justo en [i], y dónde acaba. */
    private fun diaEn(texto: String, i: Int, hoy: LocalDate): Pair<DiaDicho, Int>? {
        if (i >= texto.length) return null
        DIA_CON_MES.matchAt(texto, i)?.let { m ->
            val g = m.groupValues
            val d = numero(g[2])
            if (d != null && d in 1..31) {
                val ano = g[4].toIntOrNull() ?: if (g[5].isNotEmpty()) hoy.year + 1 else null
                return DiaDicho(dia = d, mes = MESES[g[3]], ano = ano) to m.range.last + 1
            }
        }
        DIA_CIFRAS.matchAt(texto, i)?.let { m ->
            val d = m.groupValues[2].toInt()
            val mes = m.groupValues[3].toInt()
            if (d in 1..31 && mes in 1..12) {
                val ano = m.groupValues[4].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
                return DiaDicho(dia = d, mes = mes, ano = ano) to m.range.last + 1
            }
        }
        DIA_NUMERO.matchAt(texto, i)?.let { m ->
            val d = numero(m.groupValues[2])
            if (d != null && d in 1..31) {
                val relativo = m.groupValues[3]
                val mas = when {
                    relativo.isEmpty() -> null
                    relativo.contains("este") -> 0
                    else -> 1
                }
                return DiaDicho(dia = d, mesesMas = mas, semana = DIAS[m.groupValues[1]]) to m.range.last + 1
            }
        }
        DIA_SEMANA_SOLO.matchAt(texto, i)?.let { m ->
            return DiaDicho(
                semana = DIAS[m.groupValues[1]],
                deLaQueViene = m.groupValues[2].isNotEmpty() || m.value.contains("proximo"),
                este = m.value.startsWith("este"),
            ) to m.range.last + 1
        }
        DIA_RELATIVO.matchAt(texto, i)?.let { m ->
            val r = when {
                m.value.startsWith("pasado") -> 2
                m.value.startsWith("manana") -> 1
                else -> 0
            }
            return DiaDicho(relativo = r) to m.range.last + 1
        }
        return null
    }

    /** La fecha de un día dicho suelto, como la entiende cualquiera. */
    private fun resolver(d: DiaDicho, hoy: LocalDate): LocalDate {
        val dia = d.dia
        val semana = d.semana
        return when {
            d.relativo != null -> hoy.plusDays(d.relativo.toLong())
            dia != null && d.mes != null ->
                if (d.ano != null) fechaSegura(d.ano, d.mes, dia) else proximaFecha(hoy, d.mes, dia)
            dia != null && d.mesesMas != null ->
                hoy.plusMonths(d.mesesMas.toLong()).let { fechaSegura(it.year, it.monthValue, dia) }
            dia != null -> {
                val base = if (dia < hoy.dayOfMonth) hoy.plusMonths(1) else hoy
                fechaSegura(base.year, base.monthValue, dia)
            }
            semana != null -> when {
                d.deLaQueViene -> hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    .with(TemporalAdjusters.nextOrSame(semana))
                d.este -> hoy.with(TemporalAdjusters.nextOrSame(semana))
                else -> hoy.with(TemporalAdjusters.next(semana))
            }
            else -> hoy
        }
    }

    /** El primer día a partir de [desde] que encaja con lo dicho: el final de un tramo. */
    private fun despuesDe(d: DiaDicho, desde: LocalDate, hoy: LocalDate): LocalDate {
        val dia = d.dia
        val semana = d.semana
        return when {
            d.relativo != null || d.mesesMas != null || d.deLaQueViene || d.ano != null -> resolver(d, hoy)
            dia != null && d.mes != null -> fechaSegura(desde.year, d.mes, dia)
                .let { if (it.isBefore(desde)) fechaSegura(desde.year + 1, d.mes, dia) else it }
            dia != null -> {
                val base = if (dia < desde.dayOfMonth) desde.plusMonths(1) else desde
                fechaSegura(base.year, base.monthValue, dia)
            }
            // "De lunes a lunes": el de la semana siguiente.
            semana != null -> desde.with(TemporalAdjusters.nextOrSame(semana))
                .let { if (it == desde) it.plusWeeks(1) else it }
            else -> desde
        }
    }

    /**
     * Las dos puntas de un tramo. Si el final trae mes ("del 27 al 3 de
     * octubre", "del 25 al 27 del mes que viene"), manda él y el principio se
     * pone detrás: el 27 de septiembre. Si no, manda el principio.
     */
    private fun resolverTramo(d1: DiaDicho, d2: DiaDicho, hoy: LocalDate): Pair<LocalDate, LocalDate>? {
        val dia1 = d1.dia
        val primero: LocalDate
        val ultimo: LocalDate
        if (d2.conMes && dia1 != null && d1.relativo == null) {
            ultimo = resolver(d2, hoy)
            primero = when {
                d1.mes != null && d1.ano == null -> fechaSegura(ultimo.year, d1.mes, dia1)
                    .let { if (it.isAfter(ultimo)) fechaSegura(ultimo.year - 1, d1.mes, dia1) else it }
                d1.conMes -> resolver(d1, hoy)
                dia1 <= ultimo.dayOfMonth -> fechaSegura(ultimo.year, ultimo.monthValue, dia1)
                else -> ultimo.minusMonths(1).let { fechaSegura(it.year, it.monthValue, dia1) }
            }
        } else {
            primero = resolver(d1, hoy)
            ultimo = despuesDe(d2, primero, hoy)
        }
        return if (ultimo.isBefore(primero)) null else primero to ultimo
    }

    // ======================================================================

    /**
     * [soloCrear] salta la detección de órdenes (cancelar, cambiar,
     * preguntar). Sirve para cuando una frase se tomó por una orden, no
     * encajó con nada y el usuario pide apuntarla tal cual.
     */
    fun interpretar(
        dictado: String,
        ahora: LocalDateTime = LocalDateTime.now(),
        soloCrear: Boolean = false,
    ): Interpretacion {
        val original = dictado.trim()
        if (!soloCrear) {
            leerEdicion(original, ahora)?.let { return it }
            leerConsulta(original, ahora)?.let { return it }
            leerCambio(original, ahora)?.let { return it }
        }
        val leido = leer(original, ahora, detectarBorrado = !soloCrear)
        if (leido.accion != Accion.CREAR) return leido

        val titulo = leido.titulo.ifEmpty { "Recordatorio" }
        // La confianza existe para que la app pregunte en vez de dar por
        // bueno algo que no ha entendido.
        val confianza = when {
            titulo == "Recordatorio" -> Confianza.BAJA
            leido.todoElDia -> Confianza.MEDIA
            else -> Confianza.ALTA
        }
        return leido.copy(titulo = titulo, confianza = confianza)
    }

    // --- Editar -----------------------------------------------------------

    private val COLORES = mapOf(
        "rojo" to "MAGENTA", "roja" to "MAGENTA", "rosa" to "MAGENTA", "magenta" to "MAGENTA", "fucsia" to "MAGENTA",
        "azul" to "CIAN", "cian" to "CIAN", "celeste" to "CIAN", "turquesa" to "CIAN",
        "morado" to "VIOLETA", "morada" to "VIOLETA", "lila" to "VIOLETA", "violeta" to "VIOLETA", "purpura" to "VIOLETA",
        "verde" to "VERDE",
        "naranja" to "AMBAR", "amarillo" to "AMBAR", "amarilla" to "AMBAR", "ambar" to "AMBAR", "dorado" to "AMBAR",
    )

    /**
     * Cambiar algo de un evento que ya existe, que no sea cuándo: "cámbiale el
     * nombre a cena con Ana", "cambia el nombre de la cena a comida", "no es
     * cena, es comida", "ponle una nota que diga traer vino", "ponlo en
     * verde", "avísame un día antes".
     *
     * El evento puede decirse delante ("el evento de mañana, cámbiale el
     * nombre"), detrás ("cambia el nombre de la cena a...") o no decirse: es
     * el del que se acaba de hablar, y eso lo resuelve el Ejecutor.
     */
    private fun leerEdicion(original: String, ahora: LocalDateTime): Interpretacion? {
        val texto = normalizar(original)
        val inicio = """^\s*(?:$MULETILLAS\s*[,.]?\s*)*"""

        /** Lo que identifica al evento: su nombre, su día, su hora. Puede quedar vacío. */
        fun objeto(trozo: String): Interpretacion =
            leer(trozo.trim(' ', ',', '.', ';', ':'), ahora, detectarBorrado = false)
                .copy(accion = Accion.EDITAR, dictado = original, confianza = Confianza.ALTA)

        fun limpio(t: String): String = t.trim(' ', ',', '.', ';', ':', '"', '«', '»')
            .replace(Regex("""^(?:el|la|los|las)\s+(?=\S)""", RegexOption.IGNORE_CASE), "")
            .replaceFirstChar { it.uppercase() }

        /** "X a Y": todas las maneras de partirlo por "a", "por" o "como". */
        fun particiones(resto: String): List<Pair<String, String>> {
            val n = normalizar(resto)
            val cortes = Regex("""\s+(por|como|a)\s+""").findAll(n).toList()
            return (cortes.filter { it.groupValues[1] != "a" } + cortes.filter { it.groupValues[1] == "a" })
                .map { c -> resto.substring(0, c.range.first) to resto.substring(c.range.last + 1) }
                .filter { (x, y) -> x.isNotBlank() && y.isNotBlank() }
                .map { (x, y) -> x.trim() to limpio(y) }
        }

        // 1. El nombre.
        Regex("""\b(?:cambia(?:le|lo|la)?|cambiar(?:le|lo|la)?|pon(?:le|lo|la)?|poner(?:le|lo|la)?|modifica(?:le)?|""" +
            """actualiza(?:le)?)\s+(?:el\s+|de\s+|otro\s+|como\s+)?(?:nombre|titulo)\b""").find(texto)?.let { v ->
            val antes = original.substring(0, v.range.first)
            val despues = original.substring(v.range.last + 1)
            val despuesN = normalizar(despues)
            Regex("""^\s*(?:de|del)\s+""").find(despuesN)?.let { d ->
                val partes = particiones(despues.substring(d.range.last + 1))
                if (partes.isNotEmpty()) {
                    return objeto(partes.first().first).copy(nuevoTitulo = partes.first().second, particiones = partes)
                }
            }
            val nuevo = Regex("""^\s*(?:(?:a|por|como|de|que\s+sea|para\s+que\s+se\s+llame)\b)?\s*""").find(despuesN)!!
            val titulo = limpio(despues.substring(nuevo.value.length))
            if (titulo.isNotBlank()) return objeto(antes).copy(nuevoTitulo = titulo)
        }
        Regex("""\b(?:llama(?:lo|la|le)|renombra(?:lo|la)?|bautiza(?:lo|la)?|que\s+se\s+llame\s+mejor)\b""").find(texto)?.let { v ->
            if (v.range.first == 0 && v.value.startsWith("que")) return@let
            val antes = original.substring(0, v.range.first)
            val despues = original.substring(v.range.last + 1)
            val despuesN = normalizar(despues)
            // "Renombra la cena a comida": sin pronombre, el evento va detrás.
            if (v.value == "renombra" || v.value == "bautiza") {
                val partes = particiones(despues)
                if (partes.isNotEmpty()) {
                    return objeto(partes.first().first).copy(nuevoTitulo = partes.first().second, particiones = partes)
                }
            }
            val nuevo = Regex("""^\s*(?:(?:a|como|de\s+nombre)\b)?\s*""").find(despuesN)!!
            val titulo = limpio(despues.substring(nuevo.value.length))
            if (titulo.isNotBlank()) return objeto(antes).copy(nuevoTitulo = titulo)
        }

        // 2. Una corrección: "no es cena, es comida", "en vez de Marta pon Ana",
        //    "cambia Marta por Ana". Sin fechas de por medio: "cambia la cena
        //    del martes por el jueves" es moverla.
        listOf(
            Regex(inicio + """(?:no\s+es|no\s+era)\s+(.+?)\s*,?\s+(?:es|era|sino|si\s+no)\s+(.+)$"""),
            Regex(inicio + """en\s+(?:vez|lugar)\s+de\s+(.+?)\s*,?\s+(?:pon|ponle|escribe|mejor|es)\s+(.+)$"""),
            Regex(inicio + """(?:cambia|sustituye|reemplaza|corrige)\s+(.+?)\s+por\s+(.+)$"""),
        ).firstNotNullOfOrNull { it.find(texto) }?.let { m ->
            val x = original.substring(m.groups[1]!!.range).trim()
            val y = original.substring(m.groups[2]!!.range).trim(' ', '.', ',')
            val lx = leer(x, ahora, detectarBorrado = false)
            val ly = leer(y, ahora, detectarBorrado = false)
            val sinTiempo = !lx.fechaDicha && !lx.horaDicha && !ly.fechaDicha && !ly.horaDicha
            if (sinTiempo && x.isNotBlank() && y.isNotBlank()) {
                return Interpretacion(
                    accion = Accion.EDITAR, titulo = "", inicio = ahora, todoElDia = true, avisos = emptyList(),
                    duracionMin = null, fechaDicha = false, horaDicha = false, dictado = original,
                    confianza = Confianza.ALTA, reemplazo = x to y,
                )
            }
        }

        // 3. Una nota: "ponle una nota que diga traer vino", "añade una nota a
        //    la cena del sábado: traer vino".
        Regex("""\b(?:anade(?:le)?|agrega(?:le)?|pon(?:le)?|mete(?:le)?|apunta(?:le)?|escribe(?:le)?)\s+""" +
            """(?:una\s+|la\s+|en\s+las\s+|en\s+la\s+)?notas?\b""").find(texto)?.let { v ->
            val antes = original.substring(0, v.range.first)
            val despues = original.substring(v.range.last + 1)
            val despuesN = normalizar(despues)
            val sep = Regex("""\s*(?:que\s+(?:diga|ponga)|diciendo|:|,)\s*""").find(despuesN)
            val (dondeTxt, notaTxt) = if (sep != null) {
                despues.substring(0, sep.range.first) to despues.substring(sep.range.last + 1)
            } else "" to despues
            val donde = dondeTxt.replace(Regex("""^\s*(?:a|al|en|de|del|para)\s+""", RegexOption.IGNORE_CASE), "")
            val nota = notaTxt.trim(' ', '.', ',', ':')
            // Sin "que diga" ni pronombre ("ponle"), "pon la nota del examen el
            // viernes" es algo que apuntar, no una nota.
            val conPronombre = v.value.substringBefore(' ').endsWith("le")
            if (nota.isNotBlank() && (sep != null || conPronombre)) {
                return objeto(if (donde.isNotBlank()) donde else antes).copy(nuevaNota = nota.replaceFirstChar { it.uppercase() })
            }
        }

        // 4. El color: "ponlo en verde", "pinta el dentista de rojo", "cambia el
        //    color de la cena a azul". El color va siempre al final.
        val color = """(${COLORES.keys.joinToString("|")})"""
        Regex("""\b(?:pon(?:lo|la|le)?|pinta(?:lo|la)?|colorea(?:lo|la)?|cambia(?:le|lo|la)?(?:\s+el\s+color)?)\b(.*?)""" +
            """\s+(?:en|de|a|al)\s+(?:color\s+)?$color\s*$""").find(texto)?.let { m ->
            val entre = original.substring(m.groups[1]!!.range)
                .replace(Regex("""^\s*(?:de|del)\s+""", RegexOption.IGNORE_CASE), "")
            val antes = original.substring(0, m.range.first)
            val nombre = COLORES[m.groupValues[2]]!!
            return objeto(if (entre.isNotBlank()) entre else antes).copy(nuevoColor = nombre)
        }

        // 5. Los avisos de uno que ya existe: "avísame un día antes", "ponle una
        //    alarma una hora antes del dentista".
        Regex(inicio + """(?:avisame|recuerdamelo|recuerdame|avisa|ponle\s+(?:un\s+|una\s+)?(?:aviso|alarma)|""" +
            """pon\s+(?:un\s+|una\s+)?(?:aviso|alarma))\s+(?:con\s+)?(?:($N)\s+(minutos?|horas?|dias?|semanas?)|""" +
            """(media\s+hora|hora\s+y\s+media|un\s+cuarto\s+de\s+hora))\s+antes""" +
            """(?:\s+(?:de|del|de\s+la|de\s+los|de\s+las)\s+(.+))?\s*$""").find(texto)?.let { m ->
            val fijo = m.groupValues[3]
            val minutos = when {
                fijo.startsWith("media") -> 30
                fijo.startsWith("hora") -> 90
                fijo.startsWith("un cuarto") -> 15
                else -> {
                    val n = numero(m.groupValues[1]) ?: return@let
                    val u = m.groupValues[2]
                    n * when {
                        u.startsWith("minuto") -> 1
                        u.startsWith("hora") -> 60
                        u.startsWith("dia") -> 1440
                        else -> 10080
                    }
                }
            }
            val cual = m.groups[4]?.let { original.substring(it.range) } ?: ""
            return objeto(cual).copy(nuevosAvisos = listOf(minutos, 0))
        }
        return null
    }

    // --- Preguntas --------------------------------------------------------

    /**
     * "¿Qué tengo mañana?", "¿cuál es mi próximo evento?", "¿cuándo es el
     * cumpleaños de Laura?". El reconocedor de Google escribe con tilde los
     * interrogativos ("qué", "cuándo"), y eso distingue una pregunta de un
     * "que" cualquiera. Sin tilde (el reconocedor sin conexión no las pone)
     * solo cuenta si lo que queda después es solo una fecha.
     */
    private fun leerConsulta(original: String, ahora: LocalDateTime): Interpretacion? {
        val texto = normalizar(original)
        val pregunta = Regex("""[¿?]|\b(?:qué|cuál|cuáles|cuándo|cuánto)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(original)
        val inicio = """^\s*(?:$MULETILLAS\s*[,.¿]?\s*)*¿?\s*"""

        fun consulta(tipo: Consulta, desde: LocalDate?, hasta: LocalDate?, criterio: String = "") =
            Interpretacion(
                accion = Accion.CONSULTAR, titulo = criterio,
                inicio = (desde ?: ahora.toLocalDate()).atStartOfDay(), todoElDia = true,
                avisos = emptyList(), duracionMin = null, fechaDicha = desde != null,
                horaDicha = false, dictado = original, confianza = Confianza.ALTA,
                consulta = tipo, desde = desde, hasta = hasta,
            )

        // Lo próximo. Al principio de la frase vale siempre; en medio, solo si
        // es una pregunta ("apunta que lo próximo es..." no pregunta nada).
        val proximo = """(?:(?:cual\s+es\s+)?(?:mi|el)\s+(?:proximo|siguiente)\s+(?:evento|plan|cita|compromiso|recordatorio)|""" +
            """(?:que\s+es\s+)?lo\s+(?:proximo|siguiente)|que\s+(?:me\s+)?toca\s+ahora)\b"""
        if (Regex(inicio + proximo).containsMatchIn(texto) ||
            (pregunta && Regex("""\b$proximo""").containsMatchIn(texto))) {
            return consulta(Consulta.PROXIMO, null, null)
        }

        // Cuándo es algo.
        Regex(inicio + """(?:cuando|que\s+dia|a\s+que\s+hora)\s+(?:es|son|era|sera|tengo|tenia|toca|me\s+toca|empieza)\b""")
            .find(texto)?.let { m ->
                val resto = original.substring(m.range.last + 1)
                val leido = leer(resto, ahora, detectarBorrado = false)
                // "¿A qué hora tengo la reunión mañana?": el día ayuda a encontrarla.
                val dia = if (leido.fechaDicha) leido.inicio.toLocalDate() else null
                if (leido.titulo.isNotBlank()) return consulta(Consulta.CUANDO, dia, dia, leido.titulo)
            }

        // "¿Y el sábado?", "¿y pasado mañana?": la misma pregunta, otro día.
        Regex(inicio + """y\s+(?:para\s+)?(.+?)\s*\??$""").find(texto)?.takeIf { pregunta }?.let { m ->
            val leido = leer(original.substring(m.groups[1]!!.range.first), ahora, detectarBorrado = false)
            if (leido.fechaDicha && leido.titulo.isBlank()) {
                val (desde, hasta) = tramoPreguntado(original.substring(m.groups[1]!!.range.first), ahora)
                return consulta(Consulta.AGENDA, desde, hasta)
            }
        }

        // "El lunes qué tengo", "y mañana qué hay": la pregunta va al final.
        Regex(inicio + """(?:y\s+)?(.+?)\s*,?\s+(?:que|cuantas\s+cosas)\s+(?:tengo|hay|tenemos|me\s+toca)\s*\??$""")
            .find(texto)?.let { m ->
                val leido = leer(original.substring(m.groups[1]!!.range), ahora, detectarBorrado = false)
                if (leido.fechaDicha && leido.titulo.isBlank()) {
                    val (desde, hasta) = tramoPreguntado(original.substring(m.groups[1]!!.range), ahora)
                    return consulta(Consulta.AGENDA, desde, hasta)
                }
            }

        // La agenda de un día o de unos días.
        val agenda = listOf(
            """(?:que|cual(?:es)?)\s+(?:tengo|tenemos|hay|me\s+toca|toca)\b""",
            """(?:que|cuales)\s+(?:planes|eventos|citas|cosas|compromisos)\s+(?:tengo|hay|tenemos)\b""",
            """(?:tengo|tenemos|hay)\s+(?:algo|planes?|algun\w*|eventos?|citas?|compromisos?|cosas?)\b""",
            """(?:dime|dame|leeme|lee|cuentame|ensename|muestrame)\s+(?:que\s+tengo|lo\s+que\s+tengo|""" +
                """mi\s+agenda|la\s+agenda|mis\s+(?:eventos|planes|citas)|el\s+calendario|que\s+hay)\b""",
            """agenda\s+(?:de|para|del)\b""",
            """(?:estoy|estamos)\s+(?:libres?|ocupad[oa]s?|disponibles?)\b""",
            """(?:tengo|tenemos)\s+(?:libre|hueco|tiempo|la\s+tarde\s+libre|la\s+manana\s+libre|el\s+dia\s+libre)\b""",
            """cuant[oa]s\s+(?:cosas|citas|eventos|reuniones|planes|recordatorios)\s+(?:tengo|hay|tenemos)\b""",
        ).firstNotNullOfOrNull { Regex(inicio + it).find(texto) } ?: return null

        val resto = original.substring(agenda.range.last + 1)
        val restoN = normalizar(resto)
        val hoy = ahora.toLocalDate()

        // Sin tilde ni signos, "que tengo..." puede ser el principio de algo
        // que apuntar. Solo es pregunta si detrás no queda más que la fecha.
        if (!pregunta) {
            val sobra = leer(resto, ahora, detectarBorrado = false).titulo
            val relleno = Regex("""^(?:que\s+hacer|pendientes?|para|hoy|en\s+la\s+agenda|en\s+el\s+calendario)?$""")
            if (sobra.isNotBlank() && !relleno.matches(normalizar(sobra))) return null
        }

        val (desde, hasta) = tramoPreguntado(resto, ahora)
        return consulta(Consulta.AGENDA, desde, hasta)
    }

    /** Los días por los que se pregunta: "esta semana", "el finde", "en octubre", "del 20 al 25", "mañana". */
    private fun tramoPreguntado(resto: String, ahora: LocalDateTime): Pair<LocalDate, LocalDate> {
        val hoy = ahora.toLocalDate()
        val restoN = normalizar(resto)
        val proximoLunes = hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        return when {
            Regex("""\besta\s+semana\b""").containsMatchIn(restoN) ->
                hoy to hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            Regex("""\b(?:semana\s+que\s+viene|proxima\s+semana|siguiente\s+semana)\b""").containsMatchIn(restoN) ->
                proximoLunes to proximoLunes.plusDays(6)
            Regex("""\b(?:finde|fin\s+de\s+semana)\s+(?:que\s+viene|proximo)\b""").containsMatchIn(restoN) ->
                proximoLunes.plusDays(5) to proximoLunes.plusDays(6)
            Regex("""\b(?:finde|fin\s+de\s+semana)\b""").containsMatchIn(restoN) -> {
                val sabado = hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                val domingo = hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                (if (hoy.dayOfWeek == DayOfWeek.SUNDAY) hoy else sabado) to domingo
            }
            Regex("""\beste\s+mes\b""").containsMatchIn(restoN) ->
                hoy to hoy.with(TemporalAdjusters.lastDayOfMonth())
            Regex("""\b(?:mes\s+que\s+viene|proximo\s+mes)\b""").containsMatchIn(restoN) -> {
                val primero = hoy.plusMonths(1).withDayOfMonth(1)
                primero to primero.with(TemporalAdjusters.lastDayOfMonth())
            }
            // "¿Qué tengo en octubre?": el mes entero (del de ahora, lo que queda).
            Regex("""\b(?:en|de|durante|para)\s+(?:el\s+mes\s+de\s+)?($MES)\b""").find(restoN) != null &&
                !Regex("""\d|\b(?:$N)\s+de\s+(?:$MES)""").containsMatchIn(restoN) -> {
                val mes = MESES[Regex("""\b(?:en|de|durante|para)\s+(?:el\s+mes\s+de\s+)?($MES)\b""")
                    .find(restoN)!!.groupValues[1]]!!
                val primero = if (mes == hoy.monthValue) hoy else proximaFecha(hoy, mes, 1)
                primero to primero.with(TemporalAdjusters.lastDayOfMonth())
            }
            else -> {
                val leido = leer(resto, ahora, detectarBorrado = false)
                val dia = if (leido.fechaDicha) leido.inicio.toLocalDate() else hoy
                // "¿Qué tengo del 20 al 25?": el tramo entero.
                dia to (leido.hasta ?: dia)
            }
        }
    }

    // --- Cambios ----------------------------------------------------------

    /**
     * "Cambia la cena del viernes al sábado", "pasa el dentista a las seis",
     * "retrasa la reunión una hora". La frase se parte en dos: lo que se
     * mueve y a dónde. Si no aparece un a dónde, no es un cambio sino algo
     * que apuntar ("cambia el aceite del coche el lunes").
     */
    private fun leerCambio(original: String, ahora: LocalDateTime): Interpretacion? {
        val texto = normalizar(original)
        val imperativo = """(?:cambia|cambie|mueve|mueva|pasa|pase|aplaza|aplace|retrasa|retrase|""" +
            """atrasa|atrase|adelanta|adelante|traslada|reprograma|pospon|posponga|""" +
            """alarga|alargue|amplia|amplie|extiende|extienda|prolonga|prolongue|acorta|acorte)(?:me)?(?:lo|la|los|las|le)?"""
        val infinitivo = """(?:cambiar|mover|pasar|aplazar|retrasar|atrasar|adelantar|trasladar|""" +
            """reprogramar|posponer|alargar|ampliar|extender|prolongar|acortar)(?:me)?(?:lo|la|los|las)?"""
        val pegado = """(?:cambia|mueve|pasa|aplaza|retrasa|atrasa|adelanta|traslada|reprograma|""" +
            """pospon|alarga|amplia|extiende|prolonga|acorta)(?:me)?(?:lo|la|los|las|le)|pon(?:lo|la)"""

        Regex("""^\s*(?:$MULETILLAS\s*[,.]?\s*)*(.+?)\s+no\s+(?:es|era|sera)\s+(.+?)\s*,?\s+(?:es|sino|si\s+no|que\s+es)\s+(.+?)\s*\.?$""")
            .find(texto)?.let { m ->
                val donde = leer(original.substring(m.groups[3]!!.range), ahora, detectarBorrado = false)
                val estaba = leer(original.substring(m.groups[2]!!.range), ahora, detectarBorrado = false)
                if ((donde.fechaDicha || donde.horaDicha) && Buscador.palabras(donde.titulo).isEmpty() &&
                    (estaba.fechaDicha || estaba.horaDicha)) {
                    val leido = leer(original.substring(m.groups[1]!!.range), ahora, detectarBorrado = false)
                    return leido.copy(
                        accion = Accion.MOVER, dictado = original, confianza = Confianza.ALTA,
                        // Lo que decía que era ayuda a encontrarlo.
                        inicio = if (leido.fechaDicha) leido.inicio else estaba.inicio,
                        fechaDicha = leido.fechaDicha || estaba.fechaDicha,
                        nuevaFecha = if (donde.fechaDicha) donde.inicio.toLocalDate() else null,
                        nuevaHora = if (donde.horaDicha) donde.inicio.toLocalTime() else null,
                    )
                }
            }

        val verbo = listOf(
            """^\s*(?:$MULETILLAS\s*[,.]?\s*)*(?:$imperativo)\b""",
            """^\s*(?:$MULETILLAS\s*[,.]?\s*)+(?:$infinitivo)\b""",
            """\b(?:$pegado)\b""",
            // La noticia, sin orden: "me han cambiado la reunión al jueves"
            // también quiere decir "cámbiala".
            """\b(?:me\s+|nos\s+|se\s+|lo\s+|la\s+|le\s+)*(?:ha|han|has)\s+(?:cambiado|movido|pasado|""" +
                """aplazado|retrasado|atrasado|adelantado|trasladado|pospuesto)\b""",
            // "La reunión es ahora a las 6", "la cena al final es a las 10".
            """\b(?:al\s+final\s+)?(?:es|sera)\s+(?:ahora|al\s+final|finalmente)\b""",
            """\b(?:ahora|al\s+final|finalmente)\s+(?:es|sera)\b""",
            // "El viaje dura hasta el 30": dónde acaba. Solo con "hasta": "acaba
            // el plazo el 30" es algo que apuntar.
            """\b(?:dura|durara)\b(?=\s+hasta\b)""",
            // Corregir lo que se acaba de decir: "no, a las 6", "mejor el
            // jueves", "que sea a las 5".
            """^\s*(?:$MULETILLAS\s*[,.]?\s*)*(?:no\s*[,.]?\s*)?(?:mejor|que\s+sea|mejor\s+que\s+sea|pues\s+mejor|""" +
                """casi\s+mejor|mejor\s+ponlo|mejor\s+ponla)\b""",
            """^\s*(?:$MULETILLAS\s*[,.]?\s*)*no\s*[,.]?\s+(?=(?:a\s+las?|el|para|al|manana|pasado|hoy|mejor|a\s+la)\b)""",
        ).firstNotNullOfOrNull { Regex(it).find(texto) } ?: return null
        val esCorreccion = Regex("""^\s*(?:$MULETILLAS\s*[,.]?\s*)*(?:no\b|mejor|que\s+sea|pues\s+mejor|casi\s+mejor)""")
            .containsMatchIn(texto.substring(0, verbo.range.last + 1)) &&
            !Regex("""(?:cambia|mueve|pasa|aplaza|retrasa|atrasa|adelanta|traslada|pospon)""").containsMatchIn(verbo.value)

        // Se quita el verbo tapándolo con espacios: así los índices del resto
        // siguen valiendo para el texto original.
        var resto = original.substring(0, verbo.range.first) +
            " ".repeat(verbo.range.last - verbo.range.first + 1) +
            original.substring(verbo.range.last + 1)
        // "A la misma hora" no dice nada nuevo: la hora ya se conserva sola.
        Regex("""\b(?:a\s+la\s+misma\s+hora(?:\s+de\s+siempre)?|manteniendo\s+la\s+hora|con\s+la\s+misma\s+hora)\b""")
            .find(normalizar(resto))?.let { m ->
                resto = resto.substring(0, m.range.first) + " ".repeat(m.value.length) + resto.substring(m.range.last + 1)
            }
        val restoN = normalizar(resto)
        val raiz = texto.substring(verbo.range).trim().substringAfterLast(' ')
        // Alargar o acortar no mueve el principio: cambia dónde acaba.
        val alarga = Regex("""^(?:alarg|ampli|extiend|exten|prolong|acort|dura)""").containsMatchIn(raiz)
        val acorta = raiz.startsWith("acort")

        if (esCorreccion) {
            val destino = leer(resto, ahora, detectarBorrado = false)
            if ((destino.fechaDicha || destino.horaDicha) && Buscador.palabras(destino.titulo).isEmpty()) {
                return destino.copy(
                    accion = Accion.MOVER, titulo = "", dictado = original, confianza = Confianza.ALTA,
                    fechaDicha = false, horaDicha = false,
                    nuevaFecha = if (destino.fechaDicha) destino.inicio.toLocalDate() else null,
                    nuevaHora = if (destino.horaDicha) destino.inicio.toLocalTime() else null,
                )
            }
            return null
        }

        // ¿Un desplazamiento? "una hora", "media hora", "15 minutos", "una semana".
        var cantidad = Regex(
            """\b(?:($N)\s+horas?\s+y\s+media|hora\s+y\s+media|media\s+hora|($N)\s+(minutos?|horas?|dias?|semanas?|mes(?:es)?))\b""" +
                """(?:\s+(mas\s+tarde|despues|antes))?"""
        ).find(restoN)
        // "A las 16 horas" es una hora, no cuánto se mueve.
        if (cantidad != null && Regex("""\b(?:a\s+)?las?\s*$""").containsMatchIn(restoN.substring(0, cantidad.range.first))) cantidad = null
        val mueveEnElTiempo = Regex("""^(?:retras|atras|adelant|aplaz|aplac|pospon|pospu|muev|mov|pas)""").containsMatchIn(raiz) ||
            (cantidad?.groupValues?.get(4)?.isNotEmpty() == true)
        if (cantidad != null && (mueveEnElTiempo || alarga)) {
            val g = cantidad.groupValues
            val minutos: Long = when {
                g[1].isNotEmpty() -> (numero(g[1]) ?: 0) * 60L + 30
                cantidad.value.startsWith("hora y media") -> 90
                cantidad.value.startsWith("media hora") -> 30
                else -> {
                    val n = (numero(g[2]) ?: 0).toLong()
                    when {
                        g[3].startsWith("minuto") -> n
                        g[3].startsWith("hora") -> n * 60
                        g[3].startsWith("dia") -> n * 1440
                        g[3].startsWith("semana") -> n * 10080
                        else -> n * 43200
                    }
                }
            }
            val atras = raiz.startsWith("adelant") || g[4] == "antes"
            val objetivo = resto.substring(0, cantidad.range.first) + " " + resto.substring(cantidad.range.last + 1)
            val leido = leer(objetivo, ahora, detectarBorrado = false)
            if (alarga) {
                return leido.copy(
                    accion = Accion.MOVER, dictado = original, confianza = Confianza.ALTA,
                    alargarMin = if (acorta) -minutos else minutos,
                )
            }
            return leido.copy(
                accion = Accion.MOVER, dictado = original, confianza = Confianza.ALTA,
                desplazamientoMin = if (atras) -minutos else minutos,
            )
        }

        // ¿A dónde? Se prueba cada "al", "a las", "para el"... de izquierda a
        // derecha, y vale el primero tras el que solo hay una fecha u hora. Así
        // "la visita a la abuela al sábado" no se parte por el primer "a".
        val marcas = Regex("""\b(?:al|a\s+las?|a\s+la|para\s+el|para\s+las?|para|a|hasta\s+el)\b""")
        // "Pasa la cena a las 10": el "a las 10" solo, sin la cena, serían las
        // diez de la mañana. El contexto de la noche viaja con el destino.
        val noche = DE_NOCHE.containsMatchIn(texto)
        for (marca in marcas.findAll(restoN)) {
            val destino = leer(resto.substring(marca.range.first), ahora, detectarBorrado = false, pistaNoche = noche)
            if (!(destino.fechaDicha || destino.horaDicha)) continue
            if (destino.titulo.isNotBlank()) continue
            val leido = leer(resto.substring(0, marca.range.first), ahora, detectarBorrado = false)
            if (alarga) {
                if (!destino.fechaDicha) continue
                return leido.copy(
                    accion = Accion.MOVER, dictado = original, confianza = Confianza.ALTA,
                    nuevoHasta = destino.inicio.toLocalDate(),
                )
            }
            return leido.copy(
                accion = Accion.MOVER, dictado = original, confianza = Confianza.ALTA,
                nuevaFecha = if (destino.fechaDicha) destino.inicio.toLocalDate() else null,
                nuevaHora = if (destino.horaDicha) destino.inicio.toLocalTime() else null,
            )
        }
        // "Ponla el viernes", "cámbialo mañana": con el pronombre, lo que va
        // detrás es el destino aunque no lleve "al" ni "a las".
        if (!alarga) {
            val antesDelVerbo = leer(resto.substring(0, verbo.range.first), ahora, detectarBorrado = false)
            val destino = leer(resto.substring(verbo.range.last + 1), ahora, detectarBorrado = false)
            if (Buscador.palabras(antesDelVerbo.titulo).isEmpty() && !antesDelVerbo.fechaDicha &&
                (destino.fechaDicha || destino.horaDicha) && Buscador.palabras(destino.titulo).isEmpty()) {
                return antesDelVerbo.copy(
                    accion = Accion.MOVER, dictado = original, confianza = Confianza.ALTA,
                    nuevaFecha = if (destino.fechaDicha) destino.inicio.toLocalDate() else null,
                    nuevaHora = if (destino.horaDicha) destino.inicio.toLocalTime() else null,
                )
            }
        }

        // "Alarga el viaje el domingo": el final va detrás, sin "al" ni "hasta".
        if (alarga) {
            val tras = verbo.range.last + 1
            val destino = leer(resto.substring(tras), ahora, detectarBorrado = false)
            if (destino.fechaDicha && destino.titulo.isBlank()) {
                val leido = leer(resto.substring(0, verbo.range.first), ahora, detectarBorrado = false)
                return leido.copy(
                    accion = Accion.MOVER, dictado = original, confianza = Confianza.ALTA,
                    nuevoHasta = destino.inicio.toLocalDate(),
                )
            }
        }
        return null
    }

    // --- Apuntar y cancelar -------------------------------------------------

    /**
     * Lee fecha, hora y título de una frase. Es el corazón del intérprete, y
     * lo usan también las preguntas y los cambios para leer sus trozos.
     * Devuelve el título tal cual quede, aunque sea vacío.
     */
    private fun leer(
        original: String,
        ahora: LocalDateTime,
        detectarBorrado: Boolean,
        /** El resto de la frase hablaba de la noche ("pasa la cena a las 10"). */
        pistaNoche: Boolean = false,
    ): Interpretacion {
        val texto = normalizar(original)

        // Tramos de la frase ya consumidos por una regla. Se borran del título.
        val consumido = mutableListOf<IntRange>()

        fun pisaLoYaLeido(rango: IntRange) =
            consumido.any { rango.first <= it.last && it.first <= rango.last }

        /**
         * Busca saltándose lo que otra regla ya se llevó. Sin esto, "a las
         * nueve de la mañana" acabaría en el día siguiente: la regla de la
         * hora consume "de la mañana" entera, pero la regla de los relativos
         * volvería a encontrar ese "mañana" suelto y lo leería como un día.
         */
        fun buscar(patron: String): MatchResult? {
            val m = Regex(patron).findAll(texto).firstOrNull { !pisaLoYaLeido(it.range) }
            if (m != null) consumido.add(m.range)
            return m
        }

        // Cancelar sale de muchas formas: "cancela la cena", "oye, quiero
        // cancelar la cena", "lo del finde, cancélalo", "ya no voy a la cena",
        // "me han cancelado la cena". Lo que no cuenta es el verbo metido en
        // una tarea —"recuérdame anular la tarjeta", "reunión para cancelar el
        // contrato"—: eso hay que apuntarlo.
        val raices = """(?:cancel|borr|elimin|quit|anul|desapunt|desagend)"""
        val imperativo = """$raices(?:a|ame|alo|ala|amelo|amela|e|es)|suprim(?:e|elo|ela)|cancelar|""" +
            """olvida|olvidate|olvidalo|olvidala|olvidate\s+de"""
        // El infinitivo solo cuenta detrás de una muletilla ("quiero borrar").
        // Suelto al principio suele ser una tarea ("borrar las fotos el
        // sábado"), salvo "cancelar", que va en la regla anterior.
        val infinitivo = """$raices(?:ar|arlo|arla|arme)|suprim(?:ir|irlo|irla)"""
        val reglasBorrar = listOf(
            """^\s*(?:$MULETILLAS\s*[,.]?\s*)*(?:$imperativo)\b""",
            """^\s*(?:$MULETILLAS\s*[,.]?\s*)+(?:$infinitivo)\b""",
            // Con pronombre pegado vale en cualquier sitio: "cancélalo".
            """\b$raices(?:alo|ala|amelo|amela|arlo|arla)\b|\bsuprim(?:elo|ela|irlo|irla)\b""",
            // La noticia, sin orden: también quiere decir "quítalo".
            """\bya\s+no\s+(?:voy|vamos|va|van|hay|tengo|tenemos|quedamos|quedo)\b""",
            """\b(?:me\s+|nos\s+|se\s+|lo\s+|la\s+)*(?:ha|han|has)\s+(?:cancelado|anulado|suspendido)\b""",
            """\bse\s+(?:cancela|anula|suspende)\b""",
            """\b(?:queda|quedan|esta|estan)\s+(?:cancelad|anulad|suspendid)[oa]s?\b""",
            """\bal\s+final\s+no\s+(?:hay|voy|vamos|va|van|quedamos|se\s+hace)\b""",
            """\bno\s+(?:voy|vamos|puedo|podemos)\s+(?:a\s+)?ir\b""",
            """^\s*(?:$MULETILLAS\s*[,.]?\s*)*(?:al\s+final\s+)?no\s+(?:voy|vamos|iremos|ire)\s+a\s+(?=(?:la|el|lo|los|las|al)\b)""",
            """^\s*(?:$MULETILLAS\s*[,.]?\s*)*no\s+hay\b""",
        )
        // Se pasan todas, no solo hasta la primera que acierte: así cada trozo
        // de la orden sale del texto y no se cuela en lo que hay que buscar.
        val aciertos = if (detectarBorrado) reglasBorrar.count { buscar(it) != null } else 0
        // "El último que he puesto", "lo que acabo de apuntar": el más nuevo.
        // Sin confundirlo con "el último día del mes".
        val elUltimo = buscar("""\blo\s+ultimo\b(?!\s+(?:dia|de\s|del\s))|""" +
            """\b(?:el|la)\s+ultim[oa](?:\s+(?:evento|recordatorio|cita|cosa))?\s+que\s+(?:he|has|hemos)\s+""" +
            """(?:puesto|apuntado|creado|anadido|metido|guardado)\b|""" +
            """\b(?:el|la)\s+ultim[oa]\s+(?:evento|recordatorio)\b|""" +
            """\blo\s+que\s+(?:acabo|acabamos)\s+de\s+(?:apuntar|poner|crear|anadir|decir|meter|guardar)\b""") != null
        val accion = if (aciertos > 0) Accion.BORRAR else Accion.CREAR

        val hoy = ahora.toLocalDate()
        var fecha: LocalDate? = null
        var hora: Pair<Int, Int>? = null      // hora, minuto
        var avisos: List<Int>? = null
        var duracionMin: Int? = null
        var repeticion = Repeticion.NINGUNA
        var diaRepetido: DayOfWeek? = null
        var intervalo = 1
        val diasSemana = mutableListOf<DayOfWeek>()
        // "De lunes a viernes": los días del tramo, si las dos puntas eran
        // días de la semana. Con "todos los días" es una repetición.
        var tramoDeSemana: List<DayOfWeek>? = null
        var horaDelTramo: Pair<Int, Int>? = null
        var hoyDicho = false
        val deNoche = pistaNoche || DE_NOCHE.containsMatchIn(texto)
        val deManana = DE_MANANA.containsMatchIn(texto)
        // "El sábado a medianoche": la noche del sábado, que ya es domingo.
        var medianoche = false
        // La hora dijo ella misma de qué parte del día es ("de la mañana",
        // "a primera hora"): no se corrige aunque ya haya pasado.
        var horaSegura = false
        // Lo que dura varios días: el último día, o cómo sacarlo del primero.
        var hasta: LocalDate? = null
        var hastaDicho: DiaDicho? = null
        var cuantoDura: ((LocalDate) -> LocalDate)? = null

        // 0. Corregirse al hablar: "ponme el martes una quedada con Bernie,
        //    no, el miércoles", "a las cinco, digo, a las seis". Vale lo último
        //    que se dice, y lo corregido no se queda en el título. Se aparta
        //    ahora, antes que nada, para que las reglas de siempre lean lo
        //    primero y luego se sustituya por la corrección.
        var correccion: Interpretacion? = null
        var correccionDicha = ""
        for (intro in CORRECCION.findAll(texto)) {
            if (texto.substring(0, intro.range.first).isBlank()) continue
            if (pisaLoYaLeido(intro.range)) continue
            val detras = original.substring(intro.range.last + 1)
            // Lo más largo que, detrás de la corrección, sea solo un cuándo.
            val finales = Regex("""\S+""").findAll(detras).map { it.range.last + 1 }.toList().asReversed()
            for (fin in finales) {
                val trozo = detras.substring(0, fin).trimEnd(',', '.', ';')
                val l = leer(trozo, ahora, detectarBorrado = false, pistaNoche = pistaNoche || DE_NOCHE.containsMatchIn(texto))
                if ((l.fechaDicha || l.horaDicha) && l.titulo.isBlank()) {
                    correccion = l
                    correccionDicha = normalizar(trozo)
                    consumido.add(intro.range.first until intro.range.last + 1 + fin)
                    break
                }
            }
            if (correccion != null) break
        }

        // 1. Repetición: "todos los martes", "cada día", "todos los años",
        //    "cada dos semanas", "los martes y jueves", "entre semana".
        val diaOPlural = """(?:$DIA_SEMANA|sabados|domingos)"""
        val listaDias = """$diaOPlural(?:(?:\s*,\s*|\s+y\s+|\s+e\s+|\s+)(?:los\s+|el\s+)?$diaOPlural)*"""
        // "lunes" ya acaba en ese: se prueba tal cual y luego sin la ese del
        // plural ("sábados", "domingos").
        fun diasEn(trozo: String) = Regex(diaOPlural).findAll(trozo)
            .mapNotNull { DIAS[it.value] ?: DIAS[it.value.removeSuffix("s")] }.distinct().toList()
        val laborables = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        when {
            buscar("""\b(?:(?:todos\s+los|cada)\s+dias?\s+|los\s+dias\s+)?(?:laborables|de\s+diario)\b|""" +
                """\b(?:todos\s+los\s+dias\s+)?entre\s+semana\b""") != null -> {
                repeticion = Repeticion.SEMANAL
                diasSemana += laborables
            }
            buscar("""\b(?:todos\s+los|cada|los)\s+(?:fines\s+de\s+semana|findes)\b""") != null -> {
                repeticion = Repeticion.SEMANAL
                diasSemana += listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            }
            else -> buscar("""\bcada\s+($N)\s+(dias|semanas|meses|anos)\b""")?.let { m ->
                intervalo = (numero(m.groupValues[1]) ?: 1).coerceIn(1, 365)
                repeticion = when {
                    m.groupValues[2].startsWith("dia") -> Repeticion.DIARIA
                    m.groupValues[2].startsWith("semana") -> Repeticion.SEMANAL
                    m.groupValues[2].startsWith("mes") -> Repeticion.MENSUAL
                    else -> Repeticion.ANUAL
                }
            } ?: buscar("""\b(?:todos\s+los|cada|los)\s+($listaDias)\b""")?.let { m ->
                repeticion = Repeticion.SEMANAL
                diasSemana += diasEn(m.groupValues[1])
            } ?: buscar("""\b(?:todos\s+los|todas\s+las|cada)\s+(dias?|semanas?|mes(?:es)?|anos?|mananas?|tardes?|noches?)\b""")?.let { m ->
                val que = m.groupValues[1]
                repeticion = when {
                    que.startsWith("dia") || que.startsWith("manana") || que.startsWith("tarde") ||
                        que.startsWith("noche") -> Repeticion.DIARIA
                    que.startsWith("semana") -> Repeticion.SEMANAL
                    que.startsWith("mes") -> Repeticion.MENSUAL
                    else -> Repeticion.ANUAL
                }
                // La hora de cada tramo, solo si luego no se dice otra:
                // "cada mañana a las 8" es a las 8.
                if (que.startsWith("tarde")) horaDelTramo = 17 to 0
                if (que.startsWith("noche")) horaDelTramo = 21 to 0
                if (que.startsWith("manana")) horaDelTramo = 9 to 0
            }
        }
        if (diasSemana.size == 1) diaRepetido = diasSemana.first()

        // 2. Avisos explícitos: "avísame dos días antes".
        //    Antes que el resto para que su "dos días" no lo capture otra regla.
        buscar("""\b(?:avisa(?:me)?|recuerda(?:melo)?|aviso)\s+(?:con\s+)?($N)\s+""" +
                """(minutos?|horas?|dias?|semanas?)\s+antes\b""")?.let { m ->
            val n = numero(m.groupValues[1])
            val u = m.groupValues[2]
            val factor = when {
                u.startsWith("minuto") -> 1
                u.startsWith("hora") -> 60
                u.startsWith("dia") -> 1440
                else -> 10080
            }
            if (n != null) avisos = listOf(n * factor, 0)
        }

        // 3. Duración: "durante dos horas", "durante hora y media".
        buscar("""\b(?:durante|que\s+dure|que\s+dura|dura)\s+(?:($N)\s+(horas?|minutos?)(\s+y\s+media)?|(hora\s+y\s+media|media\s+hora))\b""")?.let { m ->
            val g = m.groupValues
            duracionMin = when {
                g[4].startsWith("hora y media") -> 90
                g[4].startsWith("media hora") -> 30
                else -> numero(g[1])?.let { n ->
                    (if (g[2].startsWith("hora")) n * 60 else n) + (if (g[3].isNotEmpty()) 30 else 0)
                }
            }
        }

        // 4. "dentro de tres horas", "en 10 minutos", "en dos días". Con
        //    minutos u horas fija también la hora; con días, solo el día.
        buscar("""\b(?:dentro\s+de|en)\s+(?:(media\s+hora|hora\s+y\s+media)|($N)\s+(minutos?|horas?|dias?|semanas?|mes(?:es)?)(\s+y\s+media)?)\b""")?.let { m ->
            val g = m.groupValues
            val destino: LocalDateTime
            var conHora = true
            when {
                g[1].startsWith("media hora") -> destino = ahora.plusMinutes(30)
                g[1].isNotEmpty() -> destino = ahora.plusMinutes(90)
                else -> {
                    val n = (numero(g[2]) ?: 0).toLong()
                    val yMedia = g[4].isNotEmpty() && g[3].startsWith("hora")
                    destino = when {
                        g[3].startsWith("minuto") -> ahora.plusMinutes(n)
                        g[3].startsWith("hora") -> ahora.plusHours(n).plusMinutes(if (yMedia) 30 else 0)
                        g[3].startsWith("dia") -> ahora.plusDays(n).also { conHora = false }
                        g[3].startsWith("semana") -> ahora.plusWeeks(n).also { conHora = false }
                        else -> ahora.plusMonths(n).also { conHora = false }
                    }
                }
            }
            fecha = destino.toLocalDate()
            if (conHora) hora = destino.hour to destino.minute
        }

        // 4a. Lo que se dice sin reloj: "dentro de un rato", "en un momento",
        //     "en un cuarto de hora", "luego".
        if (fecha == null && hora == null) {
            buscar("""\b(?:dentro\s+de|en)\s+(un\s+cuarto\s+de\s+hora|tres\s+cuartos\s+de\s+hora|""" +
                """(?:un\s+)?(?:rato|ratito|momento|momentito|minuto|minutito|segundo))\b|^\s*(?:luego|mas\s+tarde)\b|""" +
                """\b(?:luego|mas\s+tarde)\s*$""")?.let { m ->
                val que = m.groupValues[1]
                val minutos = when {
                    que.startsWith("un cuarto") -> 15L
                    que.startsWith("tres cuartos") -> 45L
                    que.contains("rato") -> 30L
                    que.contains("momento") || que.contains("minut") || que.contains("segundo") -> 10L
                    else -> 120L
                }
                val destino = ahora.plusMinutes(minutos)
                fecha = destino.toLocalDate()
                hora = destino.hour to destino.minute
            }
        }

        // 4b. Cuántos días dura: "durante tres días", "dos semanas", "una
        //     semana entera". "En dos días" ya se lo ha llevado la regla de
        //     antes, y "dos días antes" no es una duración.
        buscar("""\b(?:(?:durante|por)\s+)?(?:toda\s+)?($N)\s+(dias?|semanas?|mes(?:es)?)""" +
            """(?:\s+(?:enter[oa]s?|seguid[oa]s|complet[oa]s))?\b(?!\s+(?:antes|despues|mas\s+tarde))""")?.let { m ->
            val n = (numero(m.groupValues[1]) ?: 1).toLong()
            val u = m.groupValues[2]
            cuantoDura = when {
                u.startsWith("dia") -> { d -> d.plusDays(n - 1) }
                u.startsWith("semana") -> { d -> d.plusWeeks(n).minusDays(1) }
                else -> { d -> d.plusMonths(n).minusDays(1) }
            }
        }

        // 5. Varios días: "del 27 al 3", "del 25 al 27 del mes que viene", "de
        //    lunes a viernes", "desde mañana hasta el domingo", "entre el 3 y
        //    el 5 de octubre". Entre las dos puntas puede ir la hora de
        //    salida ("del viernes a las 6 al domingo"): esa la lee la regla
        //    de la hora.
        if (fecha == null) {
            val anclas = Regex("""\b(del|de|desde\s+el|desde|entre\s+el|entre|a\s+partir\s+del?)\s+""")
            val horaEnMedio = Regex("""\s+a\s+las?\s+(?:$N)(?:\s*[:.]\s*\d{2}|\s+y\s+(?:media|cuarto))?""" +
                """(?:\s+de\s+la\s+(?:manana|tarde|noche))?""")
            for (a in anclas.findAll(texto)) {
                if (pisaLoYaLeido(a.range)) continue
                val palabra = a.groupValues[1]
                val (d1, fin1) = diaEn(texto, a.range.last + 1, hoy) ?: continue
                // "De 5 a 7" son horas; "del 5 al 7", días.
                if (palabra == "de" && d1.soloNumero) continue
                val entre = palabra.startsWith("entre")
                val hora = horaEnMedio.matchAt(texto, fin1)
                val tras = hora?.range?.last?.plus(1) ?: fin1
                val conector = Regex(if (entre) """\s+y\s+(?:el\s+)?""" else """\s+(?:al|a|hasta\s+el|hasta)\s+""")
                    .matchAt(texto, tras) ?: continue
                val (d2, fin2) = diaEn(texto, conector.range.last + 1, hoy) ?: continue
                val (primero, ultimo) = resolverTramo(d1, d2, hoy) ?: continue
                val puntas = listOf(a.range.first until fin1, tras until fin2)
                if (puntas.any { pisaLoYaLeido(it) }) continue
                consumido.addAll(puntas)
                fecha = primero
                if (ultimo.isAfter(primero)) hasta = ultimo
                hoyDicho = primero == hoy
                if (d1.semana != null && d1.dia == null && d2.semana != null && d2.dia == null) {
                    tramoDeSemana = generateSequence(primero) { it.plusDays(1) }
                        .takeWhile { !it.isAfter(ultimo) }.map { it.dayOfWeek }.toList()
                }
                break
            }
        }

        // 5b. "Hasta el jueves": el final de algo cuyo principio se dice en
        //     otra parte de la frase ("el lunes", o nada: hoy). Se resuelve
        //     al final, cuando ya se sabe el principio.
        if (hasta == null) {
            Regex("""\bhasta\s+(?:el\s+)?""").findAll(texto).firstOrNull { !pisaLoYaLeido(it.range) }?.let { h ->
                diaEn(texto, h.range.last + 1, hoy)?.let { (d, fin) ->
                    val rango = h.range.first until fin
                    if (!pisaLoYaLeido(rango)) {
                        consumido.add(rango)
                        hastaDicho = d
                    }
                }
            }
        }

        // 5c. Todo un mes, una semana o un fin de semana: "todo agosto",
        //     "toda la semana que viene", "todo el finde".
        if (fecha == null) {
            buscar("""\b(?:todo|durante)\s+(?:el\s+)?(?:mes\s+de\s+)?($MES)\b""")?.let { m ->
                val mes = MESES[m.groupValues[1]]!!
                val ano = if (mes < hoy.monthValue) hoy.year + 1 else hoy.year
                val primero = LocalDate.of(ano, mes, 1)
                fecha = if (primero.isBefore(hoy)) hoy else primero
                hasta = primero.with(TemporalAdjusters.lastDayOfMonth())
            } ?: buscar("""\b(?:todo|durante)\s+el\s+(?:mes\s+que\s+viene|proximo\s+mes)\b""")?.let {
                val primero = hoy.plusMonths(1).withDayOfMonth(1)
                fecha = primero
                hasta = primero.with(TemporalAdjusters.lastDayOfMonth())
            } ?: buscar("""\b(?:toda|durante)\s+la\s+(?:semana\s+que\s+viene|proxima\s+semana)\b""")?.let {
                val lunes = hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                fecha = lunes
                hasta = lunes.plusDays(6)
            } ?: buscar("""\b(?:toda|durante)\s+(?:la|esta)\s+semana\b""")?.let {
                fecha = hoy
                hasta = hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            } ?: buscar("""\b(?:todo|durante)\s+(?:el|este)\s+(?:finde|fin\s+de\s+semana)(\s+que\s+viene|\s+proximo)?\b""")?.let { m ->
                val sabado = if (m.groupValues[1].isNotEmpty())
                    hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(5)
                else hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                // Si ya es domingo, lo que queda del fin de semana es hoy.
                if (hoy.dayOfWeek == DayOfWeek.SUNDAY && m.groupValues[1].isEmpty()) {
                    fecha = hoy
                } else {
                    fecha = sabado
                    hasta = sabado.plusDays(1)
                }
            }
        }

        // 6. Un rango de horas: "de 5 a 7", "desde las 4 hasta las 6",
        //    "entre las 6 y las 8". Empieza en la primera y dura hasta la otra.
        if (hora == null) {
            val parte = """($N)(?:\s*[:.]\s*(\d{2})|\s+y\s+(media|cuarto))?"""
            buscar("""\b(?:de\s+(?:las?\s+)?|desde\s+las?\s+|entre\s+las?\s+)$parte\s+(?:a|hasta|y)\s+(?:las?\s+)?$parte""" +
                """(?:\s*(?:de\s+la|por\s+la)\s+(manana|tarde|noche))?\b""")?.let { m ->
                val g = m.groupValues
                val minutos = { mm: String, frac: String -> mm.toIntOrNull() ?: when (frac) { "media" -> 30; "cuarto" -> 15; else -> 0 } }
                val h1 = numero(g[1]); val h2 = numero(g[4])
                if (h1 != null && h2 != null && h1 <= 24 && h2 <= 24) {
                    val a = ajustarHora(h1, g[7], "", deNoche, deManana)
                    val b = ajustarHora(h2, g[7], "", deNoche, deManana)
                    val ini = a * 60 + minutos(g[2], g[3])
                    var fin = b * 60 + minutos(g[5], g[6])
                    if (fin <= ini) fin += 12 * 60
                    hora = a to minutos(g[2], g[3])
                    duracionMin = fin - ini
                }
            }
        }

        // 7. Horas con nombre.
        if (hora == null) {
            when {
                buscar("""\b(?:a\s+)?primera\s+hora(?:\s+de\s+la\s+manana)?\b""") != null -> hora = 8 to 0
                buscar("""\b(?:a\s+)?ultima\s+hora(?:\s+de\s+la\s+tarde)?\b""") != null -> hora = 19 to 0
                buscar("""\b(?:a|al)\s+(?:el\s+)?mediodia\b""") != null -> hora = 14 to 0
                buscar("""\b(?:a\s+)?(?:la\s+)?medianoche\b""") != null -> {
                    hora = 0 to 0
                    medianoche = true
                }
            }
        }

        if (hora != null) horaSegura = true

        // 8a. "A las ocho treinta", "a las diecisiete cuarenta y cinco": los
        //     minutos dichos sin "y".
        if (hora == null) {
            buscar("""\ba\s+las?\s+($N)\s+(cinco|diez|quince|veinte|veinticinco|treinta\s+y\s+cinco|treinta|""" +
                """cuarenta\s+y\s+cinco|cuarenta|cincuenta\s+y\s+cinco|cincuenta)\b(?!\s+(?:minutos|horas|dias))""")?.let { m ->
                val h = numero(m.groupValues[1])
                val min = when (val palabras = m.groupValues[2].replace(Regex("""\s+"""), " ")) {
                    "treinta y cinco" -> 35
                    "cuarenta y cinco" -> 45
                    "cincuenta y cinco" -> 55
                    else -> numero(palabras) ?: 0
                }
                if (h != null && h in 0..23) hora = ajustarHora(h, "", "", deNoche, deManana) to min
            }
        }

        // 8. La hora. Va antes que la fecha a propósito: "de la mañana" lleva
        //    dentro la palabra "mañana", y si la leyese la regla de fecha
        //    entendería "el día siguiente" cuando solo era el tramo del día.
        if (hora == null) {
            // "de las diez" también: "la reunión de las diez" habla de una hora.
            val prefijo = """(?:a\s+las?|a\s+la|sobre\s+las?|hacia\s+las?|a\s+eso\s+de\s+las?|""" +
                """antes\s+de\s+las?|para\s+las?|de\s+las?)\s+"""
            val patron = Regex(
                """\b($prefijo)?($N)""" +
                    """(?:\s*[:.]\s*(\d{2})|\s+y\s+(media|cuarto|$N)(?:\s+minutos)?|\s+menos\s+(cuarto|$N)(?:\s+minutos)?)?""" +
                    """\s*(?:(?:de\s+la|por\s+la|del)\s+(manana|tarde|noche|madrugada|mediodia)|""" +
                    """(a\.?\s?m\.?|p\.?\s?m\.?)|(h|horas|en\s+punto))?(?![\w/])"""
            )
            // Se recorren TODAS las coincidencias, no solo la primera: en
            // "el 3/11 a las 8" el "3" casa antes que el "8", y en "una
            // alarma a las siete" lo hace el "una". Vale la primera con alguna
            // marca de ser una hora.
            val m = patron.findAll(texto).firstOrNull { c ->
                val g = c.groupValues
                val fraccion = g[4] == "media" || g[4] == "cuarto" || g[5] == "cuarto"
                val numeroDicho = numero(g[2])
                // "El martes 29 por la tarde", "el día 20 por la tarde", "el
                // 25/10 por la tarde": ese número es el día, no la hora.
                val antes = texto.substring(0, c.range.first)
                val esDia = g[1].isEmpty() && (DELANTE_DE_UN_DIA.containsMatchIn(antes) ||
                    antes.endsWith("/") || antes.endsWith("-") ||
                    (g[6].isNotEmpty() && g[3].isEmpty() && numeroDicho != null && numeroDicho > 12))
                !pisaLoYaLeido(c.range) && !esDia && numeroDicho != null && numeroDicho <= 24 && (
                    g[1].isNotEmpty() || g[3].isNotEmpty() || fraccion ||
                        g[6].isNotEmpty() || g[7].isNotEmpty() || g[8].isNotEmpty()
                    )
            }
            if (m != null) {
                consumido.add(m.range)
                val g = m.groupValues
                var h = numero(g[2])
                var min = g[3].toIntOrNull() ?: 0
                when {
                    g[4] == "media" -> min = 30
                    g[4] == "cuarto" -> min = 15
                    g[4].isNotEmpty() -> min = numero(g[4]) ?: 0
                    g[5] == "cuarto" -> { min = 45; h = h?.let { it - 1 } }
                    g[5].isNotEmpty() -> { min = 60 - (numero(g[5]) ?: 0); h = h?.let { it - 1 } }
                }
                val meridiano = g[7].filter { it.isLetter() }
                if (h != null && h in 0..24 && min in 0..59) {
                    // "La una menos cuarto" deja la hora en cero: son las
                    // 12:45, no las 0:45.
                    val dicha = if (h == 0 && g[5].isNotEmpty()) 12 else h
                    hora = ajustarHora(dicha, g[6], meridiano, deNoche, deManana) to min
                    if (g[6].isNotEmpty() || meridiano.isNotEmpty()) horaSegura = true
                    // "A las doce de la noche" del sábado es ya el domingo.
                    if (dicha == 12 && min == 0 && hora?.first == 0) medianoche = true
                }
            }
        }

        // 8c. "A las cinco, no, a las seis": se corrige al hablar, y vale la
        //     última.
        if (hora != null) {
            buscar("""\bno\s*,?\s+(?:mejor\s+)?((?:a\s+las?|sobre\s+las?)\s+(?:$N)(?:\s*[:.]\s*\d{2}|\s+y\s+(?:media|cuarto))?""" +
                """(?:\s+de\s+la\s+(?:manana|tarde|noche))?)\b""")?.let { m ->
                val otra = leer(original.substring(m.groups[1]!!.range), ahora, detectarBorrado = false, pistaNoche = deNoche)
                if (otra.horaDicha) hora = otra.inicio.hour to otra.inicio.minute
            }
        }

        // 8b. "A las 5 o a las 6": vale la primera, y la otra no se queda en
        //     el título.
        if (hora != null) {
            buscar("""\bo\s+(?:a\s+las?|sobre\s+las?)\s+(?:$N)(?:\s*[:.]\s*\d{2}|\s+y\s+(?:media|cuarto))?\b""")
        }

        // 9. El tramo del día sin hora: "el lunes por la mañana". Con
        //    "temprano", a primera hora.
        buscar("""\b(?:(?:por|de)\s+la\s+manana\s+)?(?:muy\s+)?temprano\b""")?.let {
            if (hora == null) {
                hora = 8 to 0
                horaSegura = true
            }
        }
        if (hora == null) {
            // De madrugada no dice la hora; mejor avisar de más que de menos.
            buscar("""\b(?:de|por\s+la|en\s+la)\s+madrugada\b""")?.let { hora = 5 to 0 }
        }
        if (hora == null) {
            buscar("""\b(?:por|de|en)\s+la\s+(manana|tarde|noche)\b""")?.let { m ->
                horaSegura = true
                hora = when (m.groupValues[1]) {
                    "manana" -> 9 to 0
                    "tarde" -> 17 to 0
                    else -> 21 to 0
                }
            }
        }

        if (hora == null) hora = horaDelTramo

        // 10. Fechas con número. Un "el martes" pegado delante ("el martes 29",
        //     "el jueves 1 de octubre") se consume con la fecha: el número
        //     manda y el día de la semana solo lo acompaña.
        val antesDelDia = """\b(?:(?:el|del|este|para\s+el|antes\s+del|desde\s+el|desde|a\s+partir\s+del?)\s+)?""" +
            """(?:(?:$DIA_SEMANA)\s+)?(?:(?:el|del)\s+)?(?:dia\s+)?"""
        if (fecha == null) {
            buscar("""$antesDelDia($N)\s+de\s+($MES)(?:\s+(?:de[l]?\s+)?(\d{4})|\s+(del\s+(?:ano\s+que\s+viene|proximo\s+ano)))?\b""")?.let { m ->
                val d = numero(m.groupValues[1])
                val mes = MESES[m.groupValues[2]]!!
                val anoDicho = m.groupValues[3].toIntOrNull()
                if (d != null && d in 1..31) {
                    fecha = when {
                        anoDicho != null -> fechaSegura(anoDicho, mes, d)
                        m.groupValues[4].isNotEmpty() -> fechaSegura(hoy.year + 1, mes, d)
                        else -> proximaFecha(hoy, mes, d)
                    }
                }
            }
        }
        if (fecha == null) {
            // "El 3 del 11": día y mes con números, como en las fechas escritas.
            buscar("""\b(?:el\s+)?(?:dia\s+)?(\d{1,2})\s+del\s+(\d{1,2})\b(?!\s*(?:[:.]\d|de\s+la|y\s|h\b|horas))""")?.let { m ->
                val d = m.groupValues[1].toInt()
                val mes = m.groupValues[2].toInt()
                if (d in 1..31 && mes in 1..12) fecha = proximaFecha(hoy, mes, d)
            }
        }
        if (fecha == null) {
            buscar("""$antesDelDia(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")?.let { m ->
                val d = m.groupValues[1].toInt()
                val mes = m.groupValues[2].toInt()
                val anoDicho = m.groupValues[3].toIntOrNull()
                if (d in 1..31 && mes in 1..12) {
                    fecha = if (anoDicho != null) fechaSegura(if (anoDicho < 100) 2000 + anoDicho else anoDicho, mes, d)
                    else proximaFecha(hoy, mes, d)
                }
            }
        }
        if (fecha == null) {
            // "el día 5", "el 22", "el martes 29", "el 5 del mes que viene".
            // Solo con cifras y sin nada detrás que lo haga una hora.
            buscar("""\b(?:(?:el|del|al|este|para\s+el|antes\s+del|desde\s+el|a\s+partir\s+del)\s+)(?:(?:$DIA_SEMANA)\s+)?(?:dia\s+)?(\d{1,2})""" +
                """(\s+del\s+(?:mes\s+que\s+viene|proximo\s+mes))?""" +
                """(?!\s*(?:[:./-]\d|de\s+la|y\s|menos\s|h\b|horas|minutos|dias|semanas|meses))\b""")?.let { m ->
                val d = m.groupValues[1].toInt()
                // "El 24, cena de Nochebuena": el día es el de la fiesta.
                val fiesta = FIESTAS.firstOrNull { (nombre, md) ->
                    md.second == d && Regex("""\b$nombre\b""").containsMatchIn(texto)
                }
                if (fiesta != null) {
                    fecha = proximaFecha(hoy, fiesta.second.first, d)
                } else if (d in 1..31) {
                    var base = hoy.withDayOfMonth(1)
                    if (m.groupValues[2].isNotEmpty() || d < hoy.dayOfMonth) base = base.plusMonths(1)
                    fecha = fechaSegura(base.year, base.monthValue, d)
                }
            }
        }

        if (fecha == null) {
            // "El día veintiocho", "el martes veintinueve": el número en
            // palabras, que es como lo escribe el reconocedor sin conexión.
            // Solo con "día" o un día de la semana delante: "el cinco" suelto
            // puede ser muchas cosas.
            buscar("""\b(?:(?:el|del|al|este|para\s+el|antes\s+del|desde\s+el|a\s+partir\s+del)\s+)?""" +
                """(?:(?:$DIA_SEMANA)\s+(?:dia\s+)?|dia\s+)($N)(\s+del\s+(?:mes\s+que\s+viene|proximo\s+mes))?""" +
                """(?!\s*(?:[:./-]\d|de\s+la|y\s+(?:media|cuarto)|menos\s|h\b|horas|minutos|dias|semanas|meses|de\s+(?:$MES)))\b""")?.let { m ->
                val d = numero(m.groupValues[1])
                if (d != null && d in 1..31) {
                    var base = hoy.withDayOfMonth(1)
                    if (m.groupValues[2].isNotEmpty() || d < hoy.dayOfMonth) base = base.plusMonths(1)
                    fecha = fechaSegura(base.year, base.monthValue, d)
                }
            }
        }

        // 11. Día de la semana. "El martes" es el próximo martes; "el martes
        //     que viene" o "el próximo martes", el de la semana que viene, que
        //     es como se entiende en España: dicho un jueves, el lunes que
        //     viene es el lunes siguiente, no el de dentro de once días.
        if (fecha == null) {
            buscar("""\b(?:del\s+|el\s+|al\s+|este\s+|para\s+el\s+|antes\s+del\s+|desde\s+el\s+|a\s+partir\s+del\s+|(?:el\s+)?proximo\s+)?""" +
                """($DIA_SEMANA)(\s+que\s+viene|\s+proximo|\s+de\s+la\s+(?:semana\s+que\s+viene|proxima\s+semana))?\b""")?.let { m ->
                // "el martes o el miércoles": vale el primero, y el segundo no
                // se queda en el título.
                Regex("""\s+o\s+(?:el\s+)?(?:$DIA_SEMANA)\b""").find(texto, m.range.last + 1)
                    ?.takeIf { it.range.first == m.range.last + 1 }
                    ?.let { consumido.add(it.range) }
                val objetivo = DIAS[m.groupValues[1]]!!
                val deLaQueViene = m.groupValues[2].isNotEmpty() || m.value.contains("proximo")
                val esEste = m.value.startsWith("este")
                fecha = when {
                    deLaQueViene -> hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                        .with(TemporalAdjusters.nextOrSame(objetivo))
                    esEste -> hoy.with(TemporalAdjusters.nextOrSame(objetivo))
                    else -> hoy.with(TemporalAdjusters.next(objetivo))
                }
            }
        }

        // 12. El fin de semana. Se toma el sábado; al buscar, un día de margen
        //     hace que el domingo también valga.
        if (fecha == null) {
            buscar("""\b(?:este\s+|el\s+|del\s+|los\s+)?(?:finde|fin\s+de\s+semana|fin\s+de(?!\s+(?:mes|ano|curso|semana|""" +
                """temporada|carrera|trimestre|la|el|los|las|lo|un|una)\b))(\s+que\s+viene|\s+proximo)?\b""")?.let { m ->
                fecha = if (m.groupValues[1].isNotEmpty())
                    hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(5)
                else hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            }
        }

        // 13. Semanas y meses sin día: se toma su primer día.
        if (fecha == null) {
            buscar("""\b(?:la\s+)?(?:semana\s+que\s+viene|proxima\s+semana)\b""")?.let {
                fecha = hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            } ?: buscar("""\b(?:el\s+)?(?:mes\s+que\s+viene|proximo\s+mes)\b""")?.let {
                fecha = hoy.plusMonths(1).withDayOfMonth(1)
            } ?: buscar("""\b(?:el\s+)?ultimo\s+dia\s+(?:del|de\s+este)\s+mes(\s+que\s+viene)?\b""")?.let { m ->
                val mes = if (m.groupValues[1].isNotEmpty()) hoy.plusMonths(1) else hoy
                fecha = mes.with(TemporalAdjusters.lastDayOfMonth())
            } ?: buscar("""\b(?:a\s+|para\s+|hacia\s+)?(?:finales|final|fin)\s+del?\s+(?:este\s+)?mes\b""")?.let {
                fecha = hoy.with(TemporalAdjusters.lastDayOfMonth())
            } ?: buscar("""\b(?:a\s+|para\s+|hacia\s+)?(?:principios|primeros)\s+del?\s+(?:mes(?:\s+que\s+viene)?|proximo\s+mes)\b""")?.let {
                fecha = hoy.plusMonths(1).withDayOfMonth(1)
            } ?: buscar("""\b(?:a\s+|para\s+|hacia\s+)?mediados\s+del?\s+(?:este\s+)?mes\b""")?.let {
                fecha = if (hoy.dayOfMonth <= 15) hoy.withDayOfMonth(15) else hoy.plusMonths(1).withDayOfMonth(15)
            } ?: buscar("""\b(?:a\s+|para\s+|hacia\s+)?(principios|primeros|mediados|finales|final|fin)\s+de\s+($MES)\b""")?.let { m ->
                val mes = MESES[m.groupValues[2]]!!
                val primero = proximaFecha(hoy, mes, 1).let { if (it.monthValue == hoy.monthValue && it.year == hoy.year + 1) hoy.withDayOfMonth(1) else it }
                fecha = when (m.groupValues[1]) {
                    "principios", "primeros" -> primero
                    "mediados" -> primero.withDayOfMonth(15)
                    else -> primero.with(TemporalAdjusters.lastDayOfMonth())
                }.let { if (it.isBefore(hoy)) hoy else it }
            } ?: buscar("""\b(?:la\s+)?(primera|segunda|tercera|cuarta|ultima)\s+semana\s+(?:de\s+($MES)|del\s+(?:mes\s+que\s+viene|proximo\s+mes))\b""")?.let { m ->
                val base = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { proximaFecha(hoy, MESES[it]!!, 1) }
                    ?: hoy.plusMonths(1).withDayOfMonth(1)
                fecha = when (m.groupValues[1]) {
                    "primera" -> base
                    "segunda" -> base.withDayOfMonth(8)
                    "tercera" -> base.withDayOfMonth(15)
                    "cuarta" -> base.withDayOfMonth(22)
                    else -> base.with(TemporalAdjusters.lastDayOfMonth()).minusDays(6)
                }
            } ?: buscar("""\b(?:en|para|durante)\s+(?:el\s+mes\s+de\s+)?($MES)(?:\s+(del\s+(?:ano\s+que\s+viene|proximo\s+ano)))?\b""")?.let { m ->
                // "En agosto": su día 1; en el mes en curso, hoy.
                val mes = MESES[m.groupValues[1]]!!
                fecha = when {
                    m.groupValues[2].isNotEmpty() -> LocalDate.of(hoy.year + 1, mes, 1)
                    mes == hoy.monthValue -> hoy
                    else -> proximaFecha(hoy, mes, 1)
                }
            } ?: buscar("""\besta\s+semana\b""")?.let {
                // Sin día concreto: se apunta para hoy, y la confirmación lo
                // dice, así que se ve si no era eso.
                fecha = hoy
            }
        }

        // 14. Relativos sueltos. "mañana" ya no puede confundirse: si formaba
        //     parte de "de la mañana", la regla 8 se lo comió.
        if (fecha == null) {
            buscar("""\b(?:a\s+partir\s+de\s+|desde\s+)?(pasado\s+manana|manana|hoy|esta\s+noche|esta\s+tarde|esta\s+manana)\b""")?.let { m ->
                val clave = m.groupValues[1].replace(Regex("""\s+"""), " ")
                fecha = when (clave) {
                    "pasado manana" -> hoy.plusDays(2)
                    "manana" -> hoy.plusDays(1)
                    else -> hoy
                }
                hoyDicho = fecha == hoy
                if (hora == null && clave == "esta noche") hora = 21 to 0
                if (hora == null && clave == "esta tarde") hora = 17 to 0
                if (hora == null && clave == "esta manana") hora = 9 to 0
            }
        }

        // 15. Fiestas: dan la fecha pero se quedan en el título.
        if (fecha == null) {
            // "La cena de Navidad del trabajo" no es el día 25: "de Navidad"
            // dice de qué es, no cuándo. Nochebuena y Nochevieja sí son un
            // día aunque vayan con "de": la cena de Nochebuena es el 24.
            FIESTAS.firstOrNull { (nombre, _) ->
                Regex("""\b$nombre\b""").find(texto)?.let { f ->
                    val antes = texto.substring(0, f.range.first)
                    nombre.startsWith("noche") || !Regex("""\bde\s+$""").containsMatchIn(antes) ||
                        Regex("""\bdia\s+de\s+$""").containsMatchIn(antes)
                } == true
            }?.let { (nombre, md) ->
                fecha = proximaFecha(hoy, md.first, md.second)
                // Dicha como cuándo ("en Navidad", "el día de Reyes"), no es
                // parte del título; dicha como qué ("cena de Nochebuena"), sí.
                buscar("""\b(?:en|para|por|el\s+dia\s+de|el\s+dia|el)\s+$nombre\b""")
            }
        }

        // Un "hoy" o "mañana" que sobra porque otra regla ya dio ese mismo
        // día ("hoy dentro de un rato"): fuera del título.
        when (fecha) {
            hoy -> buscar("""\bhoy\b""")
            hoy.plusDays(1) -> buscar("""\bmanana\b""")
        }
        // Igual con el fin de semana: "el finde que viene nos vamos del
        // viernes al domingo" ya tiene sus días.
        if (fecha != null && fecha!!.dayOfWeek.value >= DayOfWeek.FRIDAY.value) {
            buscar("""\b(?:el\s+|este\s+)?(?:finde|fin\s+de\s+semana)(?:\s+que\s+viene|\s+proximo)?\b""")
        }

        // --- Resolución --------------------------------------------------

        // Lo corregido manda sobre lo primero que se dijo.
        correccion?.let { c ->
            // "El 14 de noviembre, no, el 15": el 15 de noviembre, no del
            // mes que toque.
            val soloDia = Regex("""^(?:el\s+)?(?:dia\s+)?(\d{1,2}|$N)$""").find(correccionDicha)
                ?.let { numero(it.groupValues[1]) }
            val antes = fecha
            val mismoMes = if (soloDia != null && antes != null && soloDia in 1..31)
                fechaSegura(antes.year, antes.monthValue, soloDia).takeIf { !it.isBefore(hoy) } else null
            if (c.fechaDicha && mismoMes != null) {
                fecha = mismoMes
                hoyDicho = fecha == hoy
            } else if (c.fechaDicha) {
                fecha = c.inicio.toLocalDate()
                hasta = c.hasta
                hoyDicho = fecha == hoy
            }
            if (c.horaDicha) {
                hora = c.inicio.hour to c.inicio.minute
                horaSegura = true
            }
        }

        // "Hasta el jueves" con el principio dicho: el final del tramo. Sin
        // principio, depende: "estoy de vacaciones hasta el jueves" empieza
        // hoy, pero "tengo hasta el jueves para pagar la multa" es un plazo, y
        // lo que se apunta es el jueves.
        hastaDicho?.let { d ->
            val desde = fecha
            when {
                desde != null -> despuesDe(d, desde, hoy).takeIf { it.isAfter(desde) }?.let { hasta = it }
                ESTADO.containsMatchIn(texto) -> {
                    fecha = hoy
                    despuesDe(d, hoy, hoy).takeIf { it.isAfter(hoy) }?.let { hasta = it }
                }
                else -> fecha = resolver(d, hoy)
            }
        }

        // La medianoche de un día dicho es el primer minuto del siguiente.
        if (medianoche && fecha != null && hora?.first == 0) fecha = fecha!!.plusDays(1)

        val todoElDia = hora == null
        val fechaDicha = fecha != null
        val horaDicha = hora != null

        // "Hoy a las 8 y media" dicho a mediodía son las 20:30: las 8:30 ya
        // pasaron y nadie apunta algo para una hora que ya se fue.
        if (hoyDicho && !horaSegura && hora != null && hora!!.first < 12) {
            val manana = hoy.atTime(hora!!.first, hora!!.second)
            if (!manana.isAfter(ahora) && hoy.atTime(hora!!.first + 12, hora!!.second).isAfter(ahora)) {
                hora = hora!!.first + 12 to hora!!.second
            }
        }

        // "Todos los días de lunes a viernes": es cada día laborable, no un
        // evento de cinco días. Igual si es para despertarse ("alarma de
        // lunes a viernes a las 7"): eso se repite.
        val tramoSemanal = tramoDeSemana
        if (tramoSemanal != null && (repeticion == Repeticion.DIARIA || (repeticion == Repeticion.NINGUNA && deManana))) {
            repeticion = Repeticion.SEMANAL
            diasSemana.clear()
            diasSemana += tramoSemanal.distinct()
            fecha = null
            hasta = null
            if (diasSemana.size == 1) diaRepetido = diasSemana.first()
        }
        if (fecha == null && diasSemana.size > 1) {
            // La primera vez: el más cercano de esos días (hoy, si aún llega).
            fecha = diasSemana.map { d ->
                val candidato = hoy.with(TemporalAdjusters.nextOrSame(d))
                val pasado = candidato == hoy && (hora == null || !hoy.atTime(hora!!.first, hora!!.second).isAfter(ahora))
                if (pasado) candidato.plusWeeks(1) else candidato
            }.minOrNull()
        }
        if (fecha == null && diaRepetido != null) {
            // "Todos los martes a las 7": el primero es el próximo martes (hoy
            // mismo si es martes y la hora aún no ha pasado).
            val candidato = hoy.with(TemporalAdjusters.nextOrSame(diaRepetido!!))
            fecha = if (candidato == hoy && hora != null && !hoy.atTime(hora!!.first, hora!!.second).isAfter(ahora))
                candidato.plusWeeks(1) else if (candidato == hoy && hora == null) candidato.plusWeeks(1) else candidato
        }

        if (fecha == null) {
            fecha = if (hora != null) {
                // Hora sin día: hoy si aún llega, mañana si ya pasó.
                val candidato = hoy.atTime(hora!!.first, hora!!.second)
                if (!candidato.isAfter(ahora)) hoy.plusDays(1) else hoy
            } else hoy
        }

        val inicio = fecha!!.atTime(hora?.first ?: 0, hora?.second ?: 0)
        if (hasta == null) cuantoDura?.let { dura -> hasta = dura(fecha!!) }
        val ultimoDia = hasta?.takeIf { it.isAfter(fecha!!) }

        // --- Título --------------------------------------------------------
        // Se recorta del texto ORIGINAL (con tildes) usando los tramos que las
        // reglas fueron marcando. Los que se tocan se unen antes de recortar:
        // al quitar el primero, los índices del segundo ya no valdrían.
        val tramos = mutableListOf<IntRange>()
        for (rango in consumido.sortedBy { it.first }) {
            val ultimo = tramos.lastOrNull()
            if (ultimo != null && rango.first <= ultimo.last + 1) {
                tramos[tramos.size - 1] = ultimo.first..maxOf(ultimo.last, rango.last)
            } else {
                tramos.add(rango)
            }
        }

        var titulo = original
        for (rango in tramos.sortedByDescending { it.first }) {
            val de = rango.first.coerceIn(0, titulo.length)
            val a = (rango.last + 1).coerceIn(de, titulo.length)
            titulo = titulo.substring(0, de) + " " + titulo.substring(a)
        }
        titulo = titulo.replace(Regex("""[¿?¡!]"""), " ").replace(Regex("""\s+"""), " ").trim()

        val arranques = REGEX_ARRANQUES
        var previo: String
        do {
            previo = titulo
            // "Un evento" no es un nombre ("ponme un evento para mañana"), pero
            // "evento en Valencia" sí dice algo: se quita salvo que diga dónde
            // o con quién.
            GENERICO.find(normalizar(titulo))?.let { g ->
                titulo = titulo.substring(g.value.length).trim()
            }
            if (titulo != previo) continue
            // Primero las muletillas, una a una, y solo cuando ya no queda
            // ninguna, los enlaces sueltos. Al revés, "pon en el calendario"
            // perdería el "en el" antes de reconocerse entero.
            val arranque = arranques.find(normalizar(titulo))
            if (arranque != null) {
                titulo = titulo.substring(arranque.value.length).trim()
                continue
            }
            // Preposiciones que quedan huérfanas al quitar la parte temporal,
            // por delante y por detrás: "la cena del viernes" se queda en "la
            // cena del" cuando la regla del día se lleva su parte. Y el verbo
            // de "que el domingo es el cumpleaños de...".
            titulo = titulo
                .replace(Regex("""^(?:de|del|el|la|lo|los|las|a|al|en|que|para|por|un|una|y|es|son|ya)\s+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(?:de|del|el|la|los|las|a|al|en|que|para|por|con|y|antes|es|son|sera|será|era)$", RegexOption.IGNORE_CASE), "")
                .trim(' ', ',', ';', '.', ':')
        } while (titulo != previo && titulo.isNotEmpty())

        if (normalizar(titulo) in SOLO_ENLACE) titulo = ""
        // "Apunta algo para el lunes": "algo" no es un nombre, es que falta.
        if (Regex("""^(?:algo|una\s+cosa|cosa|cosas|un\s+plan|plan|lo\s+que\s+sea|eso|esto|una\s+movida)$""")
                .matches(normalizar(titulo))) titulo = ""
        titulo = retocar(titulo)
        titulo = titulo.replaceFirstChar { it.uppercase() }

        return Interpretacion(
            accion = accion,
            titulo = titulo,
            inicio = inicio,
            todoElDia = todoElDia,
            avisos = avisos ?: if (todoElDia) listOf(1440, 0) else listOf(1440, 60, 0),
            duracionMin = duracionMin,
            fechaDicha = fechaDicha,
            horaDicha = horaDicha,
            dictado = original,
            confianza = Confianza.ALTA,
            repeticion = repeticion,
            avisosDichos = avisos != null,
            hasta = ultimoDia,
            intervalo = intervalo,
            diasSemana = if (diasSemana.size > 1) diasSemana.sorted() else emptyList(),
            elUltimo = elUltimo,
        )
    }

    /**
     * El título como se apuntaría a mano: "quedé con Luis" es "Quedada con
     * Luis", "he quedado para cenar con Ana" es "Cenar con Ana", y el verbo
     * de "avísame que saque la pizza", "Sacar la pizza".
     */
    private fun retocar(titulo: String): String {
        var t = titulo
        val n = normalizar(t)
        Regex("""^(?:he\s+quedado|hemos\s+quedado|quede|quedamos)\s+(?:para|a)\s+""").find(n)?.let {
            return retocar(t.substring(it.value.length))
        }
        Regex("""^(?:he\s+quedado|hemos\s+quedado|quede|quedamos)\s+con\b""").find(n)?.let {
            return "Quedada con" + t.substring(it.value.length)
        }
        Regex("""^(?:me\s+voy|nos\s+vamos|voy|vamos)\s+a\s+""").find(n)?.let {
            return "Ir a " + t.substring(it.value.length)
        }
        Regex("""^nos\s+vemos\b""").find(n)?.let {
            return "Quedada" + t.substring(it.value.length)
        }
        val primera = n.substringBefore(' ')
        MEJOR_DICHO[primera]?.let { t = it + t.substring(primera.length) }
        return t
    }

    /**
     * Pasa una hora dicha a la hora del reloj. "A las cinco" en boca de
     * cualquiera es la tarde; "a las nueve", la mañana: el corte está donde lo
     * pone el habla, no el reloj. Con una cena o una fiesta de por medio, "a
     * las diez" son las diez de la noche.
     */
    private fun ajustarHora(h: Int, tramo: String, meridiano: String, deNoche: Boolean, deManana: Boolean = false): Int = when {
        tramo == "noche" && h == 12 -> 0
        (tramo == "tarde" || tramo == "noche") && h < 12 -> h + 12
        tramo == "mediodia" && h in 1..4 -> h + 12
        tramo == "madrugada" && h == 12 -> 0
        meridiano == "pm" && h < 12 -> h + 12
        meridiano == "am" && h == 12 -> 0
        // Despertarse o desayunar a las siete es por la mañana.
        tramo.isEmpty() && meridiano.isEmpty() && deManana && h in 1..11 -> h
        tramo.isEmpty() && meridiano.isEmpty() && h in 1..7 -> h + 12
        tramo.isEmpty() && meridiano.isEmpty() && deNoche && h in 8..11 -> h + 12
        // "Copas a las doce": la medianoche, no el mediodía.
        tramo.isEmpty() && meridiano.isEmpty() && deNoche && h == 12 -> 0
        else -> h % 24
    }

    /** La próxima vez que cae ese día y mes: este año o, si ya pasó, el siguiente. */
    private fun proximaFecha(hoy: LocalDate, mes: Int, dia: Int): LocalDate {
        val esteAno = fechaSegura(hoy.year, mes, dia)
        return if (esteAno.isBefore(hoy)) fechaSegura(hoy.year + 1, mes, dia) else esteAno
    }

    /** "el 31 de febrero" no existe: se recorta al último día real del mes. */
    private fun fechaSegura(ano: Int, mes: Int, dia: Int): LocalDate {
        val primero = LocalDate.of(ano, mes, 1)
        return primero.withDayOfMonth(minOf(dia, primero.lengthOfMonth()))
    }
}
