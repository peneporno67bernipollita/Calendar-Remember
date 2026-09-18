package com.calendarremember.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Preparar la escucha de "Nébula".
 *
 * Android (y más aún MIUI) no deja escuchar en segundo plano sin varios
 * permisos que solo se dan a mano. Cada paso dice si ya está y lleva directo
 * al ajuste que toca. Los de Xiaomi no se pueden comprobar desde la app, así
 * que no llevan marca: solo el botón para ir a ellos.
 */
@Composable
fun DialogoEscucha(
    activa: Boolean,
    micro: Boolean,
    sobreApps: Boolean,
    bateria: Boolean,
    /** La última vez, el sistema no dejó abrir el círculo al oír la palabra. */
    aperturaBloqueada: Boolean,
    alPedirMicro: () -> Unit,
    alPedirSobreApps: () -> Unit,
    alPedirBateria: () -> Unit,
    alAbrirInicioXiaomi: () -> Unit,
    alAbrirPermisosXiaomi: () -> Unit,
    alCambiar: (Boolean) -> Unit,
    alCerrar: () -> Unit,
) {
    Dialog(onDismissRequest = alCerrar) {
        Surface(
            color = Neon.Superficie,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Neon.Borde),
        ) {
            Column(
                Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Escuchar «Nébula»", color = Neon.Texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Con la pantalla encendida, di «Nébula» y se abre el dictado. " +
                        "Con la pantalla apagada no escucha nada.",
                    color = Neon.Tenue, fontSize = 13.sp,
                )

                // Sin el círculo, la palabra funciona igual: suena un pitido y
                // se contesta en voz alta. Pero hay que decir por qué no sale.
                if (activa && (aperturaBloqueada || !sobreApps)) {
                    Text(
                        text = if (!sobreApps)
                            "Ahora mismo, al oírte suena un pitido y te contesto en voz alta, " +
                                "pero sin el círculo en pantalla. Para verlo, activa «Mostrar sobre otras apps»."
                        else
                            "La última vez el móvil no dejó abrir el círculo y te contesté solo en voz. " +
                                "En Xiaomi falta «Mostrar ventanas emergentes en segundo plano» (en «Otros permisos»).",
                        color = Neon.Ambar,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Neon.Ambar.copy(alpha = 0.08f))
                            .border(1.dp, Neon.Ambar.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    )
                }

                Paso("Micrófono", "Para oír la palabra.", micro, alPedirMicro)
                Paso(
                    "Mostrar sobre otras apps",
                    "Para abrir el dictado al oírte, estés donde estés.",
                    sobreApps, alPedirSobreApps,
                )
                Paso(
                    "Batería sin restricciones",
                    "Para que el ahorro de energía no la apague.",
                    bateria, alPedirBateria,
                )
                Paso(
                    "Xiaomi: inicio automático",
                    "Actívalo para Nébula. Sin esto, MIUI la cierra al rato.",
                    null, alAbrirInicioXiaomi,
                )
                Paso(
                    "Xiaomi: otros permisos",
                    "Activa «Mostrar ventanas emergentes en segundo plano» y «Mostrar en pantalla de bloqueo».",
                    null, alAbrirPermisosXiaomi,
                )

                Spacer(Modifier.width(4.dp))

                val puede = activa || micro
                Text(
                    text = when {
                        activa -> "Dejar de escuchar"
                        micro -> "Empezar a escuchar"
                        else -> "Falta el permiso del micrófono"
                    },
                    color = when {
                        activa -> Neon.Rojo
                        micro -> Neon.Cian
                        else -> Neon.Tenue
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (puede && !activa) Neon.Cian.copy(alpha = 0.12f) else Color.Transparent)
                        .border(1.dp, Neon.Borde, RoundedCornerShape(12.dp))
                        .clickable(enabled = puede) { alCambiar(!activa) }
                        .padding(14.dp),
                )

                Text(
                    "Cerrar",
                    color = Neon.Tenue,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = alCerrar)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** [hecho] a null: no se puede comprobar, solo ofrecer el acceso. */
@Composable
private fun Paso(titulo: String, detalle: String, hecho: Boolean?, alPulsar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Neon.SuperficieAlta)
            .border(1.dp, Neon.Borde, RoundedCornerShape(12.dp))
            .clickable(enabled = hecho != true, onClick = alPulsar)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Neon.Texto, fontSize = 14.sp)
            Text(detalle, color = Neon.Tenue, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = when (hecho) {
                true -> "Listo"
                false -> "Permitir"
                null -> "Abrir"
            },
            color = when (hecho) {
                true -> Neon.Verde
                false -> Neon.Ambar
                null -> Neon.Cian
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
