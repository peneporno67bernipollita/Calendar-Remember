package com.calendarremember.datos

import android.content.Context

/** Los ajustes que el usuario puede cambiar. */
object Preferencias {

    private const val ARCHIVO = "ajustes"
    private const val AGENDA_EN_BLOQUEO = "agenda_en_bloqueo"
    private const val ESCUCHA = "escucha_nebula"

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    /**
     * La agenda fija en la pantalla de bloqueo. Viene activada: es la manera
     * de ver lo que viene sin desbloquear, que es para lo que existe.
     */
    fun agendaEnBloqueo(contexto: Context): Boolean =
        prefs(contexto).getBoolean(AGENDA_EN_BLOQUEO, true)

    fun ponerAgendaEnBloqueo(contexto: Context, activa: Boolean) {
        prefs(contexto).edit().putBoolean(AGENDA_EN_BLOQUEO, activa).apply()
    }

    /**
     * Escuchar "Nébula" con la pantalla encendida. Viene apagada: necesita
     * permisos que hay que dar a mano, y sin ellos no funcionaría.
     */
    fun escuchaActiva(contexto: Context): Boolean =
        prefs(contexto).getBoolean(ESCUCHA, false)

    fun ponerEscucha(contexto: Context, activa: Boolean) {
        prefs(contexto).edit().putBoolean(ESCUCHA, activa).apply()
    }

    /**
     * La última vez que se oyó "Nébula", el sistema no dejó abrir la pantalla
     * del dictado (en MIUI, falta el permiso de ventanas en segundo plano).
     * Se atendió igual, sin pantalla; esto sirve para decirle al usuario qué
     * permiso le falta para tener el círculo.
     */
    fun aperturaBloqueada(contexto: Context): Boolean =
        prefs(contexto).getBoolean(APERTURA_BLOQUEADA, false)

    fun ponerAperturaBloqueada(contexto: Context, bloqueada: Boolean) {
        prefs(contexto).edit().putBoolean(APERTURA_BLOQUEADA, bloqueada).apply()
    }

    private const val APERTURA_BLOQUEADA = "apertura_bloqueada"
}
