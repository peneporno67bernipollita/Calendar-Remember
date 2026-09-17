package com.calendarremember.voz

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Botón de dictado en los ajustes rápidos (la persiana que se baja desde
 * arriba).
 *
 * Es la forma más barata de tener el dictado a un gesto: se llega desde la
 * pantalla de bloqueo sin desbloquear nada, no hay ningún proceso escuchando
 * y no cuesta un solo porcentaje de batería.
 */
class TileVoz : TileService() {

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, VozActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Dictar necesita la pantalla desbloqueada: el reconocedor no se abre
        // sobre el bloqueo. unlockAndRun pide el desbloqueo y sigue después.
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
