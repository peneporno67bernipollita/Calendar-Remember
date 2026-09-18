package com.calendarremember

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.calendarremember.avisos.Notificaciones
import com.calendarremember.avisos.Programador
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import com.calendarremember.datos.Preferencias
import com.calendarremember.ui.DialogoAjustes
import com.calendarremember.ui.DialogoEvento
import com.calendarremember.ui.PantallaPrincipal
import com.calendarremember.ui.TemaNebula
import com.calendarremember.voz.VozActivity
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val pedirNotificaciones = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (!concedido) {
            Toast.makeText(
                this,
                "Sin permiso de notificaciones no podré avisarte",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Almacen.cargar(this)
        Notificaciones.crearCanales(this)
        pedirPermisos()

        // Al abrir la app se reprograma todo: es el momento en que se sabe
        // seguro que el proceso está vivo y los datos cargados.
        Programador.reprogramarTodo(this, Almacen.eventos.value)
        Notificaciones.refrescarAgenda(this)

        setContent {
            TemaNebula {
                val eventos by Almacen.eventos.collectAsState()

                // El diálogo de edición se controla con dos variables: qué
                // evento se edita (null = uno nuevo) y si está abierto.
                var editando by remember { mutableStateOf<Evento?>(null) }
                var diaSugerido by remember { mutableStateOf<LocalDate?>(null) }
                var dialogoAbierto by remember { mutableStateOf(false) }
                var ajustesAbiertos by remember { mutableStateOf(false) }
                var agendaActiva by remember { mutableStateOf(Preferencias.agendaEnBloqueo(this)) }

                // Cuando se llega desde una notificación o desde un dictado
                // dudoso, se abre directamente ese evento.
                remember(eventos) {
                    intent?.getStringExtra("evento")?.let { id ->
                        Almacen.porId(id)?.let {
                            editando = it
                            dialogoAbierto = true
                            intent.removeExtra("evento")
                        }
                    }
                    true
                }

                PantallaPrincipal(
                    eventos = eventos,
                    alDictar = { startActivity(Intent(this, VozActivity::class.java)) },
                    alNuevo = { dia ->
                        editando = null
                        diaSugerido = dia
                        dialogoAbierto = true
                    },
                    alAbrir = { evento ->
                        editando = evento
                        dialogoAbierto = true
                    },
                    alAjustes = { ajustesAbiertos = true },
                )

                if (dialogoAbierto) {
                    DialogoEvento(
                        evento = editando,
                        diaSugerido = diaSugerido,
                        alGuardar = { evento ->
                            Almacen.guardar(this, evento)
                            dialogoAbierto = false
                        },
                        alBorrar = { id ->
                            Almacen.borrar(this, id)
                            dialogoAbierto = false
                        },
                        alCerrar = { dialogoAbierto = false },
                    )
                }

                if (ajustesAbiertos) {
                    DialogoAjustes(
                        agendaActiva = agendaActiva,
                        alCambiarAgenda = {
                            agendaActiva = !agendaActiva
                            Preferencias.ponerAgendaEnBloqueo(this, agendaActiva)
                            Notificaciones.refrescarAgenda(this)
                        },
                        alCerrar = { ajustesAbiertos = false },
                        alPermisoAlarmas = { abrirAjustesAlarmas() },
                        alExportar = { exportarCopia() },
                        alProbarAlarma = { probarAviso() },
                    )
                }
            }
        }
    }

    private fun pedirPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Desde Android 12 las alarmas exactas hay que autorizarlas en los
     * ajustes del sistema. Sin eso, el aviso del evento puede llegar tarde.
     */
    private fun abrirAjustesAlarmas() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "Tu versión de Android no lo necesita", Toast.LENGTH_SHORT).show()
            return
        }
        val gestor = getSystemService(AlarmManager::class.java)
        if (gestor?.canScheduleExactAlarms() == true) {
            Toast.makeText(this, "Ya están permitidas", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }

    private fun exportarCopia() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Copia de Nébula")
            putExtra(Intent.EXTRA_TEXT, Almacen.exportar())
        }
        startActivity(Intent.createChooser(intent, "Guardar copia"))
    }

    private fun probarAviso() {
        val prueba = Evento(
            titulo = "Aviso de prueba",
            inicio = java.time.LocalDateTime.now(),
        )
        Notificaciones.mostrarAviso(this, prueba, 60)
        Toast.makeText(this, "Enviado", Toast.LENGTH_SHORT).show()
    }
}
