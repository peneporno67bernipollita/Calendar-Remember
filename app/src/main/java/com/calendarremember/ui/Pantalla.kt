package com.calendarremember.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendarremember.datos.Etiquetas
import com.calendarremember.datos.Evento
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ES = Locale("es", "ES")
private val FMT_HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FMT_MES = DateTimeFormatter.ofPattern("MMMM yyyy", ES)
private val FMT_DIA_LARGO = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)

/**
 * La pantalla principal, sobre la nebulosa animada: lo próximo en grande,
 * el calendario del mes y la lista de lo que viene. Los paneles son
 * translúcidos para que el fondo se vea detrás sin estorbar la lectura.
 */
@Composable
fun PantallaPrincipal(
    eventos: List<Evento>,
    alDictar: () -> Unit,
    alNuevo: (LocalDate?) -> Unit,
    alAbrir: (Evento) -> Unit,
    alAjustes: () -> Unit,
) {
    var mesVisible by remember { mutableStateOf(YearMonth.now()) }
    var diaElegido by remember { mutableStateOf<LocalDate?>(null) }
    // El reloj de la pantalla: "lo próximo", la cuenta atrás y "hoy" se
    // ponen al día solos aunque la app se quede abierta.
    val ahora by produceState(LocalDateTime.now()) {
        while (true) {
            delay(20_000)
            value = LocalDateTime.now()
        }
    }
    val hoy = ahora.toLocalDate()

    Box(Modifier.fillMaxSize()) {
        FondoNebula()

        Column(Modifier.fillMaxSize()) {
            Cabecera(hoy, alAjustes)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { LoProximo(eventos, ahora, alAbrir) }
                item {
                    Panel {
                        BarraMes(
                            mes = mesVisible,
                            enHoy = mesVisible == YearMonth.from(hoy) && diaElegido == null,
                            alAnterior = { mesVisible = mesVisible.minusMonths(1) },
                            alSiguiente = { mesVisible = mesVisible.plusMonths(1) },
                            alHoy = {
                                mesVisible = YearMonth.from(hoy)
                                diaElegido = null
                            },
                        )
                        Calendario(
                            mes = mesVisible,
                            eventos = eventos,
                            hoy = hoy,
                            diaElegido = diaElegido,
                            alElegirDia = { dia ->
                                diaElegido = if (diaElegido == dia) null else dia
                            },
                        )
                    }
                }
                item {
                    Panel {
                        Agenda(
                            eventos = eventos,
                            hoy = hoy,
                            diaElegido = diaElegido,
                            alAbrir = alAbrir,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BotonRedondo(
                icono = Icons.Default.Add,
                descripcion = "Nuevo evento",
                alPulsar = { alNuevo(diaElegido) },
            )
            BotonVoz(alDictar)
        }
    }
}

// --- Cabecera ----------------------------------------------------------------

@Composable
private fun Cabecera(hoy: LocalDate, alAjustes: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "NÉBULA",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 7.sp,
                    // Sin desplazamiento y muy difusa: no es una sombra, es el
                    // halo del neón.
                    shadow = Shadow(Neon.Cian, Offset.Zero, 28f),
                ),
            )
            Text(
                text = hoy.format(FMT_DIA_LARGO).replaceFirstChar { it.uppercase(ES) },
                color = Neon.Texto.copy(alpha = 0.7f),
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
            )
        }
        IconButton(onClick = alAjustes) {
            Icon(Icons.Default.Settings, "Ajustes", tint = Neon.Texto.copy(alpha = 0.85f))
        }
    }
}

// --- Lo próximo -------------------------------------------------------------

/**
 * Lo siguiente que toca, en grande y con cuenta atrás: es lo que se mira al
 * abrir la app. Debajo, lo que está en curso y dura varios días (un viaje).
 */
@Composable
private fun LoProximo(eventos: List<Evento>, ahora: LocalDateTime, alAbrir: (Evento) -> Unit) {
    val hoy = ahora.toLocalDate()
    val siguiente = remember(eventos, ahora) {
        eventos.firstOrNull { e ->
            if (e.todoElDia) !e.inicio.toLocalDate().isBefore(hoy) else e.inicio.isAfter(ahora)
        }
    }
    val enCurso = remember(eventos, ahora) {
        eventos.filter { it.variosDias && it.inicio.toLocalDate().isBefore(hoy) && !it.ultimoDia.isBefore(hoy) }
    }
    val color = siguiente?.let { Neon.de(it.color) } ?: Neon.Cian
    val forma = RoundedCornerShape(22.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .halo(color, 16.dp, 22.dp)
            .clip(forma)
            .background(
                Brush.linearGradient(
                    listOf(Neon.Superficie.copy(alpha = 0.80f), Color(0xCC120A24)),
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(color.copy(alpha = 0.75f), Neon.Violeta.copy(alpha = 0.35f), Neon.Magenta.copy(alpha = 0.5f))),
                forma,
            )
            .clickable(enabled = siguiente != null) { siguiente?.let(alAbrir) }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "LO PRÓXIMO",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            siguiente?.let { e ->
                Text(
                    text = cuentaAtras(e, ahora),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(color.copy(alpha = 0.22f))
                        .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (siguiente == null) {
            Text("Nada a la vista", color = Neon.Texto, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Di «Nébula» o toca el micrófono para apuntar algo.",
                color = Neon.Texto.copy(alpha = 0.65f), fontSize = 14.sp,
            )
        } else {
            Text(
                text = siguiente.titulo,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(color.copy(alpha = 0.6f), Offset.Zero, 20f),
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = cuandoLargo(siguiente, hoy),
                color = Neon.Texto.copy(alpha = 0.8f),
                fontSize = 15.sp,
            )
        }

        for (e in enCurso.take(2)) {
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Neon.de(e.color).copy(alpha = 0.12f))
                    .clickable { alAbrir(e) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Neon.de(e.color)))
                Spacer(Modifier.width(8.dp))
                Text(
                    "En curso · ${e.titulo}",
                    color = Neon.Texto, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(Etiquetas.corta(e, hoy), color = Neon.de(e.color), fontSize = 12.sp)
            }
        }
    }
}

/** "Hoy a las 21:30", "Mañana, todo el día", "Del 27 sep al 25 oct". */
private fun cuandoLargo(e: Evento, hoy: LocalDate): String {
    val dia = when (val d = e.inicio.toLocalDate()) {
        hoy -> "Hoy"
        hoy.plusDays(1) -> "Mañana"
        else -> d.format(FMT_DIA_LARGO).replaceFirstChar { it.uppercase(ES) }
    }
    val hora = if (e.todoElDia) "" else " a las " + e.inicio.format(FMT_HORA)
    if (e.variosDias) {
        val fin = e.ultimoDia.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES))
        return "$dia$hora, hasta el $fin"
    }
    return if (e.todoElDia) "$dia, todo el día" else "$dia$hora"
}

/** "en 25 min", "en 3 h 10 min", "mañana", "en 5 días". */
private fun cuentaAtras(e: Evento, ahora: LocalDateTime): String {
    val hoy = ahora.toLocalDate()
    val dias = ChronoUnit.DAYS.between(hoy, e.inicio.toLocalDate())
    if (e.todoElDia || dias >= 1) {
        return when (dias) {
            0L -> "hoy"
            1L -> "mañana"
            else -> "en $dias días"
        }
    }
    val minutos = ChronoUnit.MINUTES.between(ahora, e.inicio).coerceAtLeast(0)
    return when {
        minutos < 1 -> "ahora"
        minutos < 60 -> "en $minutos min"
        minutos < 600 && minutos % 60 != 0L -> "en ${minutos / 60} h ${minutos % 60} min"
        else -> "en ${minutos / 60} h"
    }
}

// --- Paneles -------------------------------------------------------------------

/**
 * Un halo de neón alrededor de una forma redondeada: capas cada vez más
 * grandes y más tenues por detrás. Más barato que un desenfoque de verdad y,
 * a este tamaño, no se distingue.
 */
private fun Modifier.halo(color: Color, radio: Dp, esquina: Dp): Modifier = drawBehind {
    val pasos = 7
    val r = esquina.toPx()
    for (i in pasos downTo 1) {
        val extra = radio.toPx() * i / pasos
        drawRoundRect(
            color = color.copy(alpha = 0.035f),
            topLeft = Offset(-extra, -extra),
            size = Size(size.width + extra * 2, size.height + extra * 2),
            cornerRadius = CornerRadius(r + extra),
        )
    }
}

@Composable
private fun Panel(contenido: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Neon.Superficie.copy(alpha = 0.74f))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Neon.Cian.copy(alpha = 0.30f), Neon.Violeta.copy(alpha = 0.18f), Neon.Magenta.copy(alpha = 0.26f)),
                ),
                forma,
            )
            .padding(14.dp),
        content = contenido,
    )
}

// --- Calendario ----------------------------------------------------------------

@Composable
private fun BarraMes(
    mes: YearMonth,
    enHoy: Boolean,
    alAnterior: () -> Unit,
    alSiguiente: () -> Unit,
    alHoy: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = mes.format(FMT_MES).replaceFirstChar { it.uppercase(ES) },
            color = Neon.Texto,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp).weight(1f),
        )
        if (!enHoy) {
            Text(
                "Hoy",
                color = Neon.Cian,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, Neon.Cian.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .clickable(onClick = alHoy)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        IconButton(onClick = alAnterior) {
            Icon(Icons.Default.ChevronLeft, "Mes anterior", tint = Neon.Texto.copy(alpha = 0.8f))
        }
        IconButton(onClick = alSiguiente) {
            Icon(Icons.Default.ChevronRight, "Mes siguiente", tint = Neon.Texto.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun Calendario(
    mes: YearMonth,
    eventos: List<Evento>,
    hoy: LocalDate,
    diaElegido: LocalDate?,
    alElegirDia: (LocalDate) -> Unit,
) {
    val primero = mes.atDay(1)
    // La semana española empieza en lunes; DayOfWeek.value ya la numera así.
    val inicio = primero.minusDays((primero.dayOfWeek.value - 1).toLong())
    val fin = inicio.plusDays(41)

    // Los de un día van como puntos; los de varios, como una barra que cruza
    // todos sus días.
    val (sueltos, tramos) = remember(eventos, mes) {
        val sueltos = HashMap<LocalDate, MutableList<Evento>>()
        val tramos = HashMap<LocalDate, MutableList<Evento>>()
        for (e in eventos) {
            if (e.variosDias) {
                var d = maxOf(e.inicio.toLocalDate(), inicio)
                val ultimo = minOf(e.ultimoDia, fin)
                while (!d.isAfter(ultimo)) {
                    tramos.getOrPut(d) { mutableListOf() }.add(e)
                    d = d.plusDays(1)
                }
            } else {
                sueltos.getOrPut(e.inicio.toLocalDate()) { mutableListOf() }.add(e)
            }
        }
        sueltos to tramos
    }

    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, etiqueta ->
            Text(
                text = etiqueta,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = if (i >= 5) Neon.Violeta.copy(alpha = 0.9f) else Neon.Tenue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
    }

    for (semana in 0 until 6) {
        Row(Modifier.fillMaxWidth()) {
            for (dia in 0 until 7) {
                val fecha = inicio.plusDays((semana * 7 + dia).toLong())
                CeldaDia(
                    fecha = fecha,
                    delMes = fecha.month == mes.month,
                    esHoy = fecha == hoy,
                    elegido = fecha == diaElegido,
                    sueltos = sueltos[fecha].orEmpty(),
                    tramos = tramos[fecha].orEmpty(),
                    modifier = Modifier.weight(1f),
                    alPulsar = { alElegirDia(fecha) },
                )
            }
        }
    }
}

@Composable
private fun CeldaDia(
    fecha: LocalDate,
    delMes: Boolean,
    esHoy: Boolean,
    elegido: Boolean,
    sueltos: List<Evento>,
    tramos: List<Evento>,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit,
) {
    val forma = RoundedCornerShape(12.dp)
    Box(modifier.aspectRatio(1f)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(2.dp)
                .clip(forma)
                .background(
                    when {
                        elegido -> Neon.Cian.copy(alpha = 0.14f)
                        esHoy -> Neon.Cian.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    }
                )
                .then(
                    when {
                        elegido -> Modifier.border(1.dp, Neon.Cian, forma)
                        esHoy -> Modifier.border(1.dp, Neon.Cian.copy(alpha = 0.55f), forma)
                        else -> Modifier
                    }
                )
                .clickable(onClick = alPulsar)
        )
        Column(
            Modifier.align(Alignment.Center).padding(bottom = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = fecha.dayOfMonth.toString(),
                style = TextStyle(
                    color = when {
                        esHoy -> Neon.Cian
                        delMes && fecha.dayOfWeek.value >= DayOfWeek.SATURDAY.value -> Neon.Texto.copy(alpha = 0.85f)
                        delMes -> Neon.Texto
                        else -> Neon.Tenue.copy(alpha = 0.4f)
                    },
                    fontSize = 14.sp,
                    fontWeight = if (esHoy) FontWeight.Bold else FontWeight.Normal,
                    shadow = if (esHoy) Shadow(Neon.Cian, Offset.Zero, 16f) else null,
                ),
            )
            Row(
                modifier = Modifier.height(7.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tres puntos como mucho: a partir de ahí es ruido.
                for (evento in sueltos.take(3)) {
                    val c = Neon.de(evento.color)
                    Box(
                        Modifier
                            .size(5.dp)
                            .drawBehind { drawCircle(c.copy(alpha = 0.35f), radius = size.minDimension) }
                            .clip(CircleShape)
                            .background(if (delMes) c else c.copy(alpha = 0.4f))
                    )
                }
            }
        }
        // Las barras van fuera del recorte de la celda, de lado a lado: así
        // se juntan con las de los días vecinos y se leen como una sola.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (e in tramos.sortedBy { it.inicio }.take(2)) {
                val empieza = e.inicio.toLocalDate() == fecha || fecha.dayOfWeek == DayOfWeek.MONDAY
                val acaba = e.ultimoDia == fecha || fecha.dayOfWeek == DayOfWeek.SUNDAY
                val punta = 2.dp
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = if (empieza) 5.dp else 0.dp, end = if (acaba) 5.dp else 0.dp)
                        .height(3.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = if (empieza) punta else 0.dp, bottomStart = if (empieza) punta else 0.dp,
                                topEnd = if (acaba) punta else 0.dp, bottomEnd = if (acaba) punta else 0.dp,
                            )
                        )
                        .background(Neon.de(e.color).copy(alpha = if (delMes) 0.9f else 0.35f))
                )
            }
        }
    }
}

// --- Agenda ---------------------------------------------------------------------

@Composable
private fun Agenda(
    eventos: List<Evento>,
    hoy: LocalDate,
    diaElegido: LocalDate?,
    alAbrir: (Evento) -> Unit,
) {
    val mostrar = remember(eventos, diaElegido, hoy) {
        if (diaElegido != null) {
            eventos.filter { it.ocupa(diaElegido) }
        } else {
            // Desde esta madrugada, y lo que dura varios días mientras no
            // haya acabado: un viaje en curso sigue siendo de hoy.
            eventos.filter { !it.ultimoDia.isBefore(hoy) }.take(30)
        }
    }
    val titulo = diaElegido?.format(FMT_DIA_LARGO)?.replaceFirstChar { it.uppercase(ES) }
        ?: "Próximos"

    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(titulo, color = Neon.Texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (mostrar.isNotEmpty()) {
            Text(
                text = if (mostrar.size == 1) "1 evento" else "${mostrar.size} eventos",
                color = Neon.Tenue,
                fontSize = 12.sp,
            )
        }
    }

    if (mostrar.isEmpty()) {
        Text(
            text = if (diaElegido != null) "Nada este día." else "No hay nada a la vista.",
            color = Neon.Tenue,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
        return
    }

    var ultimoDia: LocalDate? = null
    for (evento in mostrar) {
        // Lo que empezó antes y sigue, se agrupa en hoy.
        val dia = maxOf(evento.inicio.toLocalDate(), hoy)
        if (diaElegido == null && dia != ultimoDia) {
            ultimoDia = dia
            Text(
                text = etiquetaDia(dia, hoy),
                color = if (dia == hoy) Neon.Cian else Neon.Tenue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
            )
        }
        FilaEvento(evento, diaElegido ?: hoy) { alAbrir(evento) }
        Spacer(Modifier.height(8.dp))
    }
}

private fun etiquetaDia(dia: LocalDate, hoy: LocalDate): String = when (dia) {
    hoy -> "HOY"
    hoy.plusDays(1) -> "MAÑANA"
    else -> dia.format(FMT_DIA_LARGO).uppercase(ES)
}

@Composable
private fun FilaEvento(evento: Evento, referencia: LocalDate, alPulsar: () -> Unit) {
    val color = Neon.de(evento.color)
    val forma = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.10f), Neon.SuperficieAlta.copy(alpha = 0.70f)))
            )
            .border(1.dp, color.copy(alpha = 0.22f), forma)
            .clickable(onClick = alPulsar)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(38.dp)
                .drawBehind {
                    drawRoundRect(color.copy(alpha = 0.25f), Offset(-4.dp.toPx(), -2.dp.toPx()),
                        Size(size.width + 8.dp.toPx(), size.height + 4.dp.toPx()), CornerRadius(6.dp.toPx()))
                }
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(evento.titulo, color = Neon.Texto, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detalle = when {
                evento.variosDias -> {
                    val total = ChronoUnit.DAYS.between(evento.inicio.toLocalDate(), evento.ultimoDia) + 1
                    if (evento.ocupa(referencia)) {
                        val n = ChronoUnit.DAYS.between(evento.inicio.toLocalDate(), referencia) + 1
                        "Día $n de $total"
                    } else "$total días"
                }
                else -> evento.notas
            }
            detalle?.let {
                Text(it, color = Neon.Tenue, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when {
                    evento.variosDias -> Etiquetas.tramo(evento)
                    evento.todoElDia -> "Todo el día"
                    else -> evento.inicio.format(FMT_HORA)
                },
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val dura = evento.duracionMin
            if (!evento.todoElDia && !evento.variosDias && dura != null) {
                Text(
                    "hasta " + evento.inicio.plusMinutes(dura.toLong()).format(FMT_HORA),
                    color = Neon.Tenue, fontSize = 11.sp,
                )
            }
        }
    }
}

// --- Botones ----------------------------------------------------------------------

/** El micrófono, con un anillo que late: es lo principal de la app. */
@Composable
private fun BotonVoz(alPulsar: () -> Unit) {
    val latido = rememberInfiniteTransition(label = "latido")
    val escala by latido.animateFloat(
        1f, 1.45f,
        infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "escala",
    )
    val alfa by latido.animateFloat(
        0.55f, 0f,
        infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "alfa",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(68.dp)
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    alpha = alfa
                }
                .border(2.dp, Neon.Cian, CircleShape)
        )
        Box(
            Modifier
                .size(68.dp)
                .halo(Neon.Cian, 14.dp, 34.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF0B3A52), Color(0xFF2A1459))))
                .border(1.5.dp, Neon.Cian.copy(alpha = 0.9f), CircleShape)
                .clickable(onClick = alPulsar),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Mic, "Dictar", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun BotonRedondo(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String,
    alPulsar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Neon.Superficie.copy(alpha = 0.82f))
            .border(1.dp, Neon.Violeta.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, descripcion, tint = Neon.Texto, modifier = Modifier.size(22.dp))
    }
}
