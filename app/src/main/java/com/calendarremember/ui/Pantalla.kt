package com.calendarremember.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendarremember.datos.Evento
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "ES")
private val FMT_HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FMT_MES = DateTimeFormatter.ofPattern("MMMM yyyy", ES)
private val FMT_DIA_LARGO = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)

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

    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Fondo)
    ) {
        // Dos halos muy difusos. Evitan que el negro plano parezca una
        // pantalla apagada, sin competir con el contenido.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Neon.Cian.copy(alpha = 0.06f), Color.Transparent),
                        radius = 900f,
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            Barra(
                titulo = mesVisible.format(FMT_MES).replaceFirstChar { it.uppercase(ES) },
                alAnterior = { mesVisible = mesVisible.minusMonths(1) },
                alSiguiente = { mesVisible = mesVisible.plusMonths(1) },
                alAjustes = alAjustes,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Panel {
                        Calendario(
                            mes = mesVisible,
                            eventos = eventos,
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
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BotonRedondo(
                icono = Icons.Default.Add,
                descripcion = "Nuevo evento",
                tamano = 48.dp,
                color = Neon.Texto,
                borde = Neon.Borde,
                alPulsar = { alNuevo(diaElegido) },
            )
            BotonRedondo(
                icono = Icons.Default.Mic,
                descripcion = "Dictar un evento",
                tamano = 64.dp,
                color = Neon.Cian,
                borde = Neon.Cian.copy(alpha = 0.5f),
                alPulsar = alDictar,
            )
        }
    }
}

@Composable
private fun Barra(
    titulo: String,
    alAnterior: () -> Unit,
    alSiguiente: () -> Unit,
    alAjustes: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NÉBULA",
            color = Neon.Cian,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.4.sp,
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = alAnterior) {
            Icon(Icons.Default.ChevronLeft, "Mes anterior", tint = Neon.Tenue)
        }
        Text(
            text = titulo,
            color = Neon.Texto,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = alSiguiente) {
            Icon(Icons.Default.ChevronRight, "Mes siguiente", tint = Neon.Tenue)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = alAjustes) {
            Icon(Icons.Default.Settings, "Ajustes", tint = Neon.Tenue)
        }
    }
}

@Composable
private fun Panel(contenido: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Neon.Superficie,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Neon.Borde),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) { contenido() }
    }
}

@Composable
private fun Calendario(
    mes: YearMonth,
    eventos: List<Evento>,
    diaElegido: LocalDate?,
    alElegirDia: (LocalDate) -> Unit,
) {
    val hoy = LocalDate.now()
    val porDia = remember(eventos) { eventos.groupBy { it.inicio.toLocalDate() } }

    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        for (etiqueta in listOf("L", "M", "X", "J", "V", "S", "D")) {
            Text(
                text = etiqueta,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = Neon.Tenue,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }
    }

    // La semana española empieza en lunes; DayOfWeek.value ya la numera así.
    val primero = mes.atDay(1)
    val desplazamiento = primero.dayOfWeek.value - 1
    val inicio = primero.minusDays(desplazamiento.toLong())

    for (semana in 0 until 6) {
        Row(Modifier.fillMaxWidth()) {
            for (dia in 0 until 7) {
                val fecha = inicio.plusDays((semana * 7 + dia).toLong())
                CeldaDia(
                    fecha = fecha,
                    delMes = fecha.month == mes.month,
                    esHoy = fecha == hoy,
                    elegido = fecha == diaElegido,
                    eventos = porDia[fecha].orEmpty(),
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
    eventos: List<Evento>,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit,
) {
    val fondo = when {
        elegido -> Neon.SuperficieAlta
        else -> Color.Transparent
    }
    val borde = when {
        elegido -> Neon.Cian
        esHoy -> Neon.Cian.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fondo)
            .then(
                if (borde != Color.Transparent) {
                    Modifier.border(1.dp, borde, RoundedCornerShape(10.dp))
                } else Modifier
            )
            .clickable(onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = fecha.dayOfMonth.toString(),
                color = when {
                    esHoy -> Neon.Cian
                    delMes -> Neon.Texto
                    else -> Neon.Tenue.copy(alpha = 0.45f)
                },
                fontSize = 14.sp,
            )
            Row(
                modifier = Modifier.height(7.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Tres puntos como mucho: a partir de ahí es ruido.
                for (evento in eventos.take(3)) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Neon.de(evento.color))
                    )
                }
            }
        }
    }
}

@Composable
private fun Agenda(
    eventos: List<Evento>,
    diaElegido: LocalDate?,
    alAbrir: (Evento) -> Unit,
) {
    val hoy = LocalDate.now()
    val mostrar = remember(eventos, diaElegido) {
        if (diaElegido != null) {
            eventos.filter { it.inicio.toLocalDate() == diaElegido }
        } else {
            // Desde esta madrugada: un evento de hace dos horas todavía importa.
            eventos.filter { !it.inicio.toLocalDate().isBefore(hoy) }.take(30)
        }
    }
    val titulo = diaElegido?.format(FMT_DIA_LARGO)?.replaceFirstChar { it.uppercase(ES) }
        ?: "Próximos"

    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(titulo, color = Neon.Texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
        val dia = evento.inicio.toLocalDate()
        if (diaElegido == null && dia != ultimoDia) {
            ultimoDia = dia
            Text(
                text = etiquetaDia(dia, hoy),
                color = Neon.Tenue,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
        FilaEvento(evento) { alAbrir(evento) }
        Spacer(Modifier.height(8.dp))
    }
}

private fun etiquetaDia(dia: LocalDate, hoy: LocalDate): String = when (dia) {
    hoy -> "HOY"
    hoy.plusDays(1) -> "MAÑANA"
    else -> dia.format(FMT_DIA_LARGO).uppercase(ES)
}

@Composable
private fun FilaEvento(evento: Evento, alPulsar: () -> Unit) {
    val color = Neon.de(evento.color)
    Surface(
        color = Neon.SuperficieAlta,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Neon.Borde),
        modifier = Modifier.fillMaxWidth().clickable(onClick = alPulsar),
    ) {
        Row(
            Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(evento.titulo, color = Neon.Texto, fontSize = 15.sp)
                evento.notas?.let {
                    Text(it, color = Neon.Tenue, fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (evento.todoElDia) "Todo el día" else evento.inicio.format(FMT_HORA),
                color = color,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun BotonRedondo(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String,
    tamano: androidx.compose.ui.unit.Dp,
    color: Color,
    borde: Color,
    alPulsar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(tamano)
            .clip(CircleShape)
            .background(Neon.SuperficieAlta)
            .border(1.dp, borde, CircleShape)
            .clickable(onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, descripcion, tint = color, modifier = Modifier.size(tamano * 0.42f))
    }
}
