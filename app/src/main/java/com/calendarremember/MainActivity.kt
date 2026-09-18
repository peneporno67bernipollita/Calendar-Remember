package com.calendarremember

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import androidx.core.content.ContextCompat
import com.calendarremember.avisos.Notificaciones
import com.calendarremember.avisos.Programador
import com.calendarremember.datos.Almacen
import com.calendarremember.datos.Evento
import com.calendarremember.datos.Preferencias
import com.calendarremember.ui.DialogoAjustes
import com.calendarremember.ui.DialogoEscucha
import com.calendarremember.ui.DialogoEvento
import com.calendarremember.ui.PantallaPrincipal
import com.calendarremember.ui.TemaNebula
import com.calendarremember.voz.EscuchaServicio
import com.calendarremember.voz.VozActivity
import com.calendarremember.voz.Xiaomi
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    companion object {
        /** Un evento sin guardar, en JSON, para abrir el editor ya relleno. */
        const val BORRADOR = "borrador"
    }

    /**
     * Sube cada vez que se vuelve a la app. Los permisos se conceden fuera,
     * en los ajustes del sistema; al volver hay que mirarlos otra vez.
     */
    private var revision by mutableStateOf(0)

    private val pedirMicro = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { revision++ }

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

        // Abrir la app es el momento en que Android deja que el servicio tome
        // el micrófono: si la escucha estaba activada y no corre (tras un
        // reinicio, o porque el sistema la cerró), se levanta aquí.
        if (Preferencias.escuchaActiva(this) && tieneMicro() && !EscuchaServicio.enMarcha) {
            EscuchaServicio.arrancar(this)
        }

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
                var escuchaActiva by remember { mutableStateOf(Preferencias.escuchaActiva(this)) }
                var escuchaApagada by remember { mutableStateOf(Preferencias.escucharApagada(this)) }
                var escuchaAbierta by remember { mutableStateOf(false) }
                var borrador by remember { mutableStateOf<Evento?>(null) }

                // Cuando se llega desde una notificación o desde un dictado
                // dudoso, se abre directamente ese evento. Desde un plan de
                // WhatsApp o un texto compartido, el editor ya relleno.
                remember(eventos, revision) {
                    intent?.getStringExtra("evento")?.let { id ->
                        Almacen.porId(id)?.let {
                            editando = it
                            borrador = null
                            dialogoAbierto = true
                            intent.removeExtra("evento")
                        }
                    }
                    intent?.getStringExtra(BORRADOR)?.let { json ->
                        runCatching { Evento.deJson(org.json.JSONObject(json)) }.getOrNull()?.let {
                            editando = null
                            borrador = it
                            dialogoAbierto = true
                        }
                        intent.removeExtra(BORRADOR)
                    }
                    true
                }

                PantallaPrincipal(
                    eventos = eventos,
                    alDictar = { startActivity(Intent(this, VozActivity::class.java)) },
                    alNuevo = { dia ->
                        editando = null
                        borrador = null
                        diaSugerido = dia
                        dialogoAbierto = true
                    },
                    alAbrir = { evento ->
                        editando = evento
                        borrador = null
                        dialogoAbierto = true
                    },
                    alAjustes = { ajustesAbiertos = true },
                )

                if (dialogoAbierto) {
                    // La clave obliga a rehacer el editor si cambia lo que se
                    // edita con el diálogo ya abierto (llega otro plan).
                    androidx.compose.runtime.key(editando?.id, borrador?.id) {
                        DialogoEvento(
                            evento = editando,
                            diaSugerido = diaSugerido,
                            borrador = borrador,
                            alGuardar = { evento ->
                                Almacen.guardar(this, evento)
                                dialogoAbierto = false
                                borrador = null
                            },
                            alBorrar = { id ->
                                Almacen.borrar(this, id)
                                dialogoAbierto = false
                            },
                            alCerrar = {
                                dialogoAbierto = false
                                borrador = null
                            },
                        )
                    }
                }

                if (ajustesAbiertos) {
                    @Suppress("UNUSED_VARIABLE") val r = revision
                    DialogoAjustes(
                        planesActivos = planesDeWhatsApp(),
                        alPlanes = { abrirAccesoNotificaciones() },
                        escuchaActiva = escuchaActiva,
                        alEscucha = {
                            ajustesAbiertos = false
                            escuchaAbierta = true
                        },
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

                if (escuchaAbierta) {
                    // Leer "revision" hace que el diálogo se repinte al volver
                    // de los ajustes del sistema con un permiso recién dado.
                    @Suppress("UNUSED_VARIABLE") val r = revision
                    DialogoEscucha(
                        activa = escuchaActiva,
                        apagada = escuchaApagada,
                        micro = tieneMicro(),
                        sobreApps = Settings.canDrawOverlays(this),
                        bateria = bateriaSinRestricciones(),
                        esXiaomi = Xiaomi.esXiaomi,
                        ventanasXiaomi = Xiaomi.ventanasEnSegundoPlano(this),
                        bloqueoXiaomi = Xiaomi.mostrarEnBloqueo(this),
                        aperturaBloqueada = Preferencias.aperturaBloqueada(this),
                        aperturaBloqueadaEnBloqueo = Preferencias.aperturaBloqueadaEnBloqueo(this),
                        alCambiarApagada = { si ->
                            escuchaApagada = si
                            Preferencias.ponerEscucharApagada(this, si)
                            EscuchaServicio.releerAjustes(this)
                        },
                        alPedirMicro = { pedirMicro.launch(Manifest.permission.RECORD_AUDIO) },
                        alPedirSobreApps = {
                            abrir(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")))
                        },
                        alPedirBateria = {
                            abrir(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")))
                        },
                        alAbrirInicioXiaomi = { abrirInicioXiaomi() },
                        alAbrirPermisosXiaomi = { abrirPermisosXiaomi() },
                        alCambiar = { activar ->
                            escuchaActiva = activar
                            Preferencias.ponerEscucha(this, activar)
                            if (activar) EscuchaServicio.arrancar(this)
                            else EscuchaServicio.parar(this)
                        },
                        alCerrar = { escuchaAbierta = false },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        revision++
    }

    // La app es de una sola instancia: si ya está abierta, lo que llega de
    // una notificación entra por aquí y no por onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        revision++
    }

    /** Si Nébula puede leer las notificaciones (para los planes de WhatsApp). */
    private fun planesDeWhatsApp(): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    /** El permiso de leer notificaciones solo se da desde los ajustes del sistema. */
    private fun abrirAccesoNotificaciones() {
        val detalle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, com.calendarremember.avisos.OyenteWhatsApp::class.java).flattenToString(),
            )
        } else null
        runCatching { startActivity(detalle ?: error("")) }
            .onFailure { abrir(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun tieneMicro() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun bateriaSinRestricciones(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

    /** Abre un ajuste del sistema; si el móvil no lo tiene, la ficha de la app. */
    private fun abrir(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { abrirFichaApp() }
    }

    private fun abrirFichaApp() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    /** La lista de inicio automático de MIUI. En otros móviles no existe. */
    private fun abrirInicioXiaomi() {
        abrir(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            )
        )
    }

    /** Los permisos propios de MIUI: ventanas en segundo plano, bloqueo... */
    private fun abrirPermisosXiaomi() {
        abrir(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                )
                .putExtra("extra_pkgname", packageName)
        )
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
