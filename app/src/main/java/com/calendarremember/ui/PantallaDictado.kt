package com.calendarremember.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendarremember.datos.Evento
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "ES")

/** En qué punto está el dictado. */
sealed interface EstadoDictado {
    /** Escuchando. [nivel] es el volumen de la voz, de 0 a 1, para el pulso. */
    data class Escuchando(val parcial: String, val nivel: Float) : EstadoDictado
    data class Hecho(val mensaje: String, val bien: Boolean) : EstadoDictado
    /** Varios eventos encajan por igual: toca decir cuál. [boton]: "Borrar", "Cambiar". */
    data class Elegir(val pregunta: String, val candidatos: List<Evento>, val boton: String) : EstadoDictado
    /** Sonaba a una orden sobre un evento, pero no encaja con ninguno. */
    data class NoEncontrado(val mensaje: String, val dictado: String) : EstadoDictado
}

/**
 * La pantalla del dictado. Va sobre lo que hubiera detrás —el escritorio o
 * la pantalla de bloqueo— con un velo oscuro, y enseña en grande lo que va
 * entendiendo mientras se habla: si algo se entiende mal, se ve al momento.
 */
@Composable
fun PantallaDictado(
    estado: EstadoDictado,
    alCerrar: () -> Unit,
    alElegir: (Evento) -> Unit,
    alApuntar: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Fondo.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = alCerrar,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            when (estado) {
                is EstadoDictado.Escuchando -> {
                    Pulso(Neon.Cian, estado.nivel) {
                        Icon(Icons.Default.Mic, null, tint = Neon.Cian, modifier = Modifier.size(40.dp))
                    }
                    Text(
                        text = estado.parcial.ifBlank { "Te escucho…" },
                        color = Neon.Texto,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Di qué apunto o qué cancelo",
                        color = Neon.Tenue,
                        fontSize = 14.sp,
                    )
                }

                is EstadoDictado.Hecho -> {
                    val color = if (estado.bien) Neon.Verde else Neon.Ambar
                    Pulso(color, 0f) {
                        Icon(
                            if (estado.bien) Icons.Default.Check else Icons.Default.PriorityHigh,
                            null, tint = color, modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        text = estado.mensaje,
                        color = Neon.Texto,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                is EstadoDictado.Elegir -> {
                    Text(
                        text = estado.pregunta,
                        color = Neon.Texto,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (evento in estado.candidatos) {
                            FichaElegir(evento, estado.boton) { alElegir(evento) }
                        }
                    }
                }

                is EstadoDictado.NoEncontrado -> {
                    Pulso(Neon.Ambar, 0f) {
                        Icon(Icons.Default.PriorityHigh, null, tint = Neon.Ambar, modifier = Modifier.size(40.dp))
                    }
                    Text(
                        text = estado.mensaje,
                        color = Neon.Texto,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "¿Querías apuntarlo? «${estado.dictado}»",
                        color = Neon.Tenue,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Boton("Nada", Neon.Tenue, Color.Transparent, alCerrar)
                        Boton("Apuntarlo", Neon.Cian, Neon.Cian.copy(alpha = 0.12f), alApuntar)
                    }
                }
            }
        }
    }
}

/** El círculo de neón que late; con la voz, late más fuerte. */
@Composable
private fun Pulso(color: Color, nivel: Float, contenido: @Composable () -> Unit) {
    val transicion = rememberInfiniteTransition(label = "pulso")
    val latido by transicion.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "latido",
    )
    Box(
        Modifier
            .size(118.dp)
            .scale(latido + nivel.coerceIn(0f, 1f) * 0.12f)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.08f))
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) { contenido() }
}

@Composable
private fun FichaElegir(evento: Evento, boton: String, alPulsar: () -> Unit) {
    val formato = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
    // Rojo para borrar, que no tiene vuelta atrás; cian para lo demás.
    val color = if (boton == "Borrar") Neon.Rojo else Neon.Cian
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Neon.SuperficieAlta)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = alPulsar)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Neon.de(evento.color))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(evento.titulo, color = Neon.Texto, fontSize = 16.sp)
            Text(
                text = buildString {
                    append(evento.inicio.format(formato).replaceFirstChar { it.uppercase(ES) })
                    if (!evento.todoElDia) {
                        append(" · ")
                        append(evento.inicio.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                },
                color = Neon.Tenue,
                fontSize = 12.sp,
            )
        }
        Text(boton, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun Boton(texto: String, color: Color, fondo: Color, alPulsar: () -> Unit) {
    Text(
        text = texto,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fondo)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = alPulsar)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
