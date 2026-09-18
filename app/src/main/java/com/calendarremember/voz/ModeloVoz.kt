package com.calendarremember.voz

import android.content.Context
import java.io.File

/**
 * El modelo de voz en español para escuchar "Nébula".
 *
 * Viaja dentro del APK, pero el motor necesita leerlo como ficheros normales,
 * así que la primera vez se copia a la memoria de la app. La copia lleva una
 * marca de versión: si una actualización trae otro modelo, se sustituye; si
 * no, se reutiliza y el arranque es inmediato.
 */
object ModeloVoz {

    private const val EN_APK = "modelo-es"
    private const val MARCA = "version"

    fun carpeta(contexto: Context): File = File(contexto.filesDir, EN_APK)

    /** Deja el modelo listo en disco. Tarda unos segundos solo la primera vez. */
    fun preparar(contexto: Context): File {
        val destino = carpeta(contexto)
        val versionApk = contexto.assets.open("$EN_APK/$MARCA").bufferedReader().readText().trim()
        val versionDisco = File(destino, MARCA).takeIf { it.exists() }?.readText()?.trim()

        if (versionApk != versionDisco) {
            // Se copia aparte y se renombra al final: si la copia se corta a
            // medias, no queda una carpeta con la marca de versión puesta y
            // el modelo incompleto, que el motor no sabría leer.
            val temporal = File(contexto.filesDir, "$EN_APK.tmp")
            temporal.deleteRecursively()
            copiar(contexto, EN_APK, temporal)
            destino.deleteRecursively()
            temporal.renameTo(destino)
        }
        return destino
    }

    private fun copiar(contexto: Context, origen: String, destino: File) {
        val hijos = contexto.assets.list(origen).orEmpty()
        if (hijos.isEmpty()) {
            // Es un fichero: se copia tal cual.
            destino.parentFile?.mkdirs()
            contexto.assets.open(origen).use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            return
        }
        destino.mkdirs()
        for (hijo in hijos) copiar(contexto, "$origen/$hijo", File(destino, hijo))
    }
}
