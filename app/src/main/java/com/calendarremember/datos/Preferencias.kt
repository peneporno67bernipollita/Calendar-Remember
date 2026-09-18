package com.calendarremember.datos

import android.content.Context

/** Los ajustes que el usuario puede cambiar. Por ahora, uno. */
object Preferencias {

    private const val ARCHIVO = "ajustes"
    private const val AGENDA_EN_BLOQUEO = "agenda_en_bloqueo"

    /**
     * La agenda fija en la pantalla de bloqueo. Viene activada: es la manera
     * de ver lo que viene sin desbloquear, que es para lo que existe.
     */
    fun agendaEnBloqueo(contexto: Context): Boolean =
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            .getBoolean(AGENDA_EN_BLOQUEO, true)

    fun ponerAgendaEnBloqueo(contexto: Context, activa: Boolean) {
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AGENDA_EN_BLOQUEO, activa)
            .apply()
    }
}
