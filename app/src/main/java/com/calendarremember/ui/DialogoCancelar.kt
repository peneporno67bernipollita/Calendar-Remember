package com.calendarremember.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.calendarremember.datos.Evento
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "ES")
private val FMT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
private val FMT_HORA = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Confirmación antes de cancelar un evento dictado por voz.
 *
 * Borrar no se hace nunca sin enseñar antes qué se va a borrar: si el
 * dictado se entendió mal, equivocarse aquí pierde algo que no vuelve.
 */
@Composable
fun DialogoCancelar(
    candidatos: List<Evento>,
    alConfirmar: (Evento) -> Unit,
    alCerrar: () -> Unit,
) {
    val varios = candidatos.size > 1

    Dialog(onDismissRequest = alCerrar) {
        Surface(
            color = Neon.Superficie,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Neon.Borde),
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (varios) "¿Cuál cancelo?" else "¿Cancelo esto?",
                    color = Neon.Texto,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                for (evento in candidatos.take(4)) {
                    FichaEvento(evento) { alConfirmar(evento) }
                }

                if (!varios) {
                    Text(
                        text = "Se borrará junto con sus avisos.",
                        color = Neon.Tenue,
                        fontSize = 12.sp,
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "Déjalo",
                        color = Neon.Tenue,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = alCerrar)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FichaEvento(evento: Evento, alPulsar: () -> Unit) {
    val color = Neon.de(evento.color)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Neon.SuperficieAlta)
            .border(1.dp, Neon.Rojo.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = alPulsar)
            .padding(13.dp),
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
            Text(
                text = buildString {
                    append(evento.inicio.format(FMT).replaceFirstChar { it.uppercase(ES) })
                    if (!evento.todoElDia) append(" · ${evento.inicio.format(FMT_HORA)}")
                },
                color = Neon.Tenue,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        // "Borrar" y no "Cancelar": en un diálogo, "Cancelar" se lee como
        // "cerrar sin hacer nada", justo lo contrario de lo que hace.
        Text("Borrar", color = Neon.Rojo, fontSize = 14.sp)
    }
}

/**
 * Cuando una frase suena a cancelar pero no encaja con ningún evento.
 *
 * Puede que no hubiera nada que cancelar, o puede que fuera una tarea que
 * menciona el verbo ("necesito anular la tarjeta el lunes"). Por eso, además
 * de decirlo, ofrece apuntarla tal cual: así nada de lo dictado se pierde.
 */
@Composable
fun DialogoNoEncontrado(
    buscado: String,
    dictado: String,
    alApuntar: () -> Unit,
    alCerrar: () -> Unit,
) {
    Dialog(onDismissRequest = alCerrar) {
        Surface(
            color = Neon.Superficie,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Neon.Borde),
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "No encuentro nada que cancelar",
                    color = Neon.Texto,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (buscado.isNotBlank())
                        "Ningún evento se parece a «$buscado»."
                    else
                        "No sé a qué evento te refieres.",
                    color = Neon.Tenue,
                    fontSize = 14.sp,
                )
                Text(
                    text = "¿Querías apuntarlo? «$dictado»",
                    color = Neon.Tenue,
                    fontSize = 13.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "Nada",
                        color = Neon.Tenue,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = alCerrar)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Apuntarlo",
                        color = Neon.Cian,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Neon.Cian.copy(alpha = 0.12f))
                            .clickable(onClick = alApuntar)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
