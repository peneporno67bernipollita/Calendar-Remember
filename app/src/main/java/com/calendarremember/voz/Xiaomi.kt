package com.calendarremember.voz

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Los permisos propios de MIUI, que Android no conoce.
 *
 * MIUI añade los suyos encima de los de Android y los deniega por defecto a
 * las apps instaladas a mano. Dos de ellos deciden si el círculo del dictado
 * puede abrirse al oír "Nébula": "ventanas emergentes en segundo plano"
 * (desde el escritorio) y "mostrar en pantalla de bloqueo" (con el móvil
 * bloqueado). No hay API para consultarlos, pero MIUI los guarda como
 * operaciones de AppOps con números propios, y se pueden leer. Si algo
 * falla (otra versión de MIUI, otro móvil), se devuelve null: "no se sabe".
 */
object Xiaomi {

    private const val OP_MOSTRAR_EN_BLOQUEO = 10020
    private const val OP_VENTANAS_EN_SEGUNDO_PLANO = 10021

    val esXiaomi: Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
        Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

    fun ventanasEnSegundoPlano(contexto: Context): Boolean? = permitido(contexto, OP_VENTANAS_EN_SEGUNDO_PLANO)

    fun mostrarEnBloqueo(contexto: Context): Boolean? = permitido(contexto, OP_MOSTRAR_EN_BLOQUEO)

    private fun permitido(contexto: Context, op: Int): Boolean? {
        if (!esXiaomi) return null
        return runCatching {
            val gestor = contexto.getSystemService(AppOpsManager::class.java)
            val metodo = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            )
            val modo = metodo.invoke(gestor, op, Process.myUid(), contexto.packageName) as Int
            modo == AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }
}
