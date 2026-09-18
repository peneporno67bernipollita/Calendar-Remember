package com.calendarremember.avisos

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import com.calendarremember.ui.Neon
import com.calendarremember.ui.TemaNebula
import java.time.format.DateTimeFormatter

/**
 * La pantalla que sale cuando llega la hora de un evento.
 *
 * Se muestra encima del bloqueo y enciende la pantalla, como un despertador:
 * el sonido va por el canal de alarma, así que suena aunque el timbre esté
 * silenciado. Es el único aviso de la app que no se puede ignorar sin querer.
 */
class AlarmaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        Almacen.cargar(this)
        val id = intent.getStringExtra("evento")
        val evento = id?.let { Almacen.porId(it) }

        Sonido.arrancar(this)

        setContent {
            TemaNebula {
                PantallaAlarma(
                    evento = evento,
                    alDescartar = { cerrar(evento) },
                    alPosponer = {
                        evento?.let { Pospuestos.posponer(this, it, 10) }
                        cerrar(evento)
                    },
                )
            }
        }
    }

    /** Descartar es callar, quitar el aviso de la barra y cerrar. */
    private fun cerrar(evento: Evento?) {
        Sonido.parar()
        evento?.let { Notificaciones.quitar(this, it.id.hashCode()) }
        Notificaciones.refrescarAgenda(this)
        finish()
    }

    override fun onDestroy() {
        Sonido.parar()
        super.onDestroy()
    }
}

@Composable
private fun PantallaAlarma(
    evento: Evento?,
    alDescartar: () -> Unit,
    alPosponer: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = evento?.inicio?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                color = Neon.Cian,
            )
            Text(
                text = evento?.titulo ?: "Evento",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = Neon.Texto,
                textAlign = TextAlign.Center,
            )
            evento?.notas?.let {
                Text(text = it, fontSize = 15.sp, color = Neon.Tenue, textAlign = TextAlign.Center)
            }

            Box(Modifier.size(24.dp))

            Button(
                onClick = alDescartar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Neon.Cian.copy(alpha = 0.16f),
                    contentColor = Neon.Cian,
                ),
            ) {
                Text("Hecho", fontSize = 17.sp, modifier = Modifier.padding(vertical = 6.dp))
            }

            OutlinedButton(
                onClick = alPosponer,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Posponer 10 minutos", color = Color(0xFF7C869C))
            }
        }
    }
}
