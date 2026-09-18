package com.calendarremember.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun DialogoAjustes(
    escuchaActiva: Boolean,
    alEscucha: () -> Unit,
    agendaActiva: Boolean,
    alCambiarAgenda: () -> Unit,
    alCerrar: () -> Unit,
    alPermisoAlarmas: () -> Unit,
    alExportar: () -> Unit,
    alProbarAlarma: () -> Unit,
) {
    Dialog(onDismissRequest = alCerrar) {
        Surface(
            color = Neon.Superficie,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Neon.Borde),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Ajustes", color = Neon.Texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

                Opcion(
                    titulo = if (escuchaActiva) "Escuchar «Nébula»: activada"
                        else "Escuchar «Nébula»: desactivada",
                    detalle = if (escuchaActiva)
                        "Di «Nébula» y se abre el dictado, también con el móvil bloqueado."
                    else
                        "Toca para prepararla. Necesita unos permisos.",
                    alPulsar = alEscucha,
                )
                Opcion(
                    titulo = if (agendaActiva) "Agenda en el bloqueo: activada"
                        else "Agenda en el bloqueo: desactivada",
                    detalle = if (agendaActiva)
                        "Notificación fija con tus próximos eventos. Toca para quitarla."
                    else
                        "Toca para ver tus próximos eventos sin desbloquear.",
                    alPulsar = alCambiarAgenda,
                )
                Opcion(
                    titulo = "Permitir alarmas exactas",
                    detalle = "Necesario desde Android 12 para que el aviso suene a su hora.",
                    alPulsar = alPermisoAlarmas,
                )
                Opcion(
                    titulo = "Probar un aviso",
                    detalle = "Comprueba que las notificaciones llegan.",
                    alPulsar = alProbarAlarma,
                )
                Opcion(
                    titulo = "Exportar copia",
                    detalle = "Todos los eventos en un archivo, por si cambias de móvil.",
                    alPulsar = alExportar,
                )

                Box(
                    Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = alCerrar)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("Cerrar", color = Neon.Cian, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Opcion(titulo: String, detalle: String, alPulsar: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Neon.SuperficieAlta)
            .border(1.dp, Neon.Borde, RoundedCornerShape(12.dp))
            .clickable(onClick = alPulsar)
            .padding(13.dp),
    ) {
        Text(titulo, color = Neon.Texto, fontSize = 15.sp)
        Text(detalle, color = Neon.Tenue, fontSize = 12.sp)
    }
}
