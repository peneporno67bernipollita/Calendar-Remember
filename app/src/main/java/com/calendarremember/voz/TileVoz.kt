package com.calendarremember.voz

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Botón de dictado en los ajustes rápidos (la persiana que se baja desde
 * arriba).
 *
 * Abre el dictado directamente, también con el móvil bloqueado: la pantalla
 * de dictado es propia y sale encima del bloqueo, así que no hace falta
 * poner la contraseña para apuntar o cancelar algo.
 */
class TileVoz : TileService() {

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, VozActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

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
