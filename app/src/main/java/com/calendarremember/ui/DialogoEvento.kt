package com.calendarremember.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.calendarremember.datos.ColorEvento
import com.calendarremember.datos.Evento
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "ES")
private val FMT_FECHA = DateTimeFormatter.ofPattern("EEE d MMM yyyy", ES)

/** Las antelaciones que se ofrecen, en minutos. */
private val OPCIONES_AVISO = listOf(
    0 to "A la hora",
    10 to "10 min",
    60 to "1 hora",
    180 to "3 horas",
    1440 to "1 día",
    2880 to "2 días",
    10080 to "1 semana",
)

@Composable
fun DialogoEvento(
    evento: Evento?,
    diaSugerido: LocalDate?,
    alGuardar: (Evento) -> Unit,
    alBorrar: ((String) -> Unit)?,
    alCerrar: () -> Unit,
) {
    val contexto = LocalContext.current
    val partida = evento ?: Evento(
        titulo = "",
        inicio = (diaSugerido ?: LocalDate.now()).atTime(12, 0),
    )

    var titulo by remember { mutableStateOf(partida.titulo) }
    var notas by remember { mutableStateOf(partida.notas ?: "") }
    var fecha by remember { mutableStateOf(partida.inicio.toLocalDate()) }
    var hora by remember { mutableStateOf(partida.inicio.toLocalTime()) }
    var todoElDia by remember { mutableStateOf(partida.todoElDia) }
    var color by remember { mutableStateOf(partida.color) }
    var avisos by remember { mutableStateOf(partida.avisos) }

    Dialog(onDismissRequest = alCerrar) {
        Surface(
            color = Neon.Superficie,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Neon.Borde),
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (evento != null) "Editar evento" else "Nuevo evento",
                    color = Neon.Texto,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título") },
                    singleLine = true,
                    colors = coloresCampo(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Selector(
                        etiqueta = "Día",
                        valor = fecha.format(FMT_FECHA),
                        modifier = Modifier.weight(1f),
                    ) {
                        DatePickerDialog(
                            contexto,
                            { _, a, m, d -> fecha = LocalDate.of(a, m + 1, d) },
                            fecha.year, fecha.monthValue - 1, fecha.dayOfMonth,
                        ).show()
                    }
                    Selector(
                        etiqueta = "Hora",
                        valor = if (todoElDia) "—" else hora.format(DateTimeFormatter.ofPattern("HH:mm")),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (!todoElDia) {
                            TimePickerDialog(
                                contexto,
                                { _, h, m -> hora = java.time.LocalTime.of(h, m) },
                                hora.hour, hora.minute, true,
                            ).show()
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = todoElDia,
                        onCheckedChange = { todoElDia = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Neon.Cian,
                            uncheckedColor = Neon.Tenue,
                            checkmarkColor = Neon.Fondo,
                        ),
                    )
                    Text("Todo el día", color = Neon.Texto, fontSize = 14.sp)
                }

                OutlinedTextField(
                    value = notas,
                    onValueChange = { notas = it },
                    label = { Text("Notas") },
                    colors = coloresCampo(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Color", color = Neon.Tenue, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in ColorEvento.entries) {
                        val elegido = c == color
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Neon.de(c).copy(alpha = if (elegido) 1f else 0.4f))
                                .border(
                                    2.dp,
                                    if (elegido) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { color = c }
                        )
                    }
                }

                Text("Avisarme", color = Neon.Tenue, fontSize = 13.sp)
                // Una rejilla a mano: dos filas de fichas. Con siete opciones
                // fijas no compensa traerse FlowRow.
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    for (grupo in OPCIONES_AVISO.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            for ((minutos, etiqueta) in grupo) {
                                Ficha(
                                    texto = etiqueta,
                                    activa = minutos in avisos,
                                ) {
                                    avisos = if (minutos in avisos) avisos - minutos
                                    else avisos + minutos
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.size(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (evento != null && alBorrar != null) {
                        Text(
                            text = "Borrar",
                            color = Neon.Rojo,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { alBorrar(evento.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Cancelar",
                        color = Neon.Tenue,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = alCerrar)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Guardar",
                        color = if (titulo.isBlank()) Neon.Tenue else Neon.Cian,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (titulo.isBlank()) Color.Transparent
                                else Neon.Cian.copy(alpha = 0.12f)
                            )
                            .clickable(enabled = titulo.isNotBlank()) {
                                val inicio = if (todoElDia) fecha.atStartOfDay()
                                else LocalDateTime.of(fecha, hora)
                                alGuardar(
                                    partida.copy(
                                        titulo = titulo.trim(),
                                        notas = notas.trim().takeIf { it.isNotEmpty() },
                                        inicio = inicio,
                                        todoElDia = todoElDia,
                                        color = color,
                                        avisos = avisos.sortedDescending(),
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Selector(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit,
) {
    Column(modifier) {
        Text(etiqueta, color = Neon.Tenue, fontSize = 12.sp)
        Spacer(Modifier.size(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Neon.Fondo)
                .border(1.dp, Neon.Borde, RoundedCornerShape(10.dp))
                .clickable(onClick = alPulsar)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(valor, color = Neon.Texto, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Ficha(texto: String, activa: Boolean, alPulsar: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                1.dp,
                if (activa) Neon.Cian.copy(alpha = 0.5f) else Neon.Borde,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = alPulsar)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(texto, color = if (activa) Neon.Cian else Neon.Tenue, fontSize = 13.sp)
    }
}

@Composable
private fun coloresCampo() = TextFieldDefaults.colors(
    focusedTextColor = Neon.Texto,
    unfocusedTextColor = Neon.Texto,
    focusedContainerColor = Neon.Fondo,
    unfocusedContainerColor = Neon.Fondo,
    cursorColor = Neon.Cian,
    focusedIndicatorColor = Neon.Cian,
    unfocusedIndicatorColor = Neon.Borde,
    focusedLabelColor = Neon.Cian,
    unfocusedLabelColor = Neon.Tenue,
)
