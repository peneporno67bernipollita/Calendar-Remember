package com.calendarremember.avisos

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.calendarremember.datos.Almacen
import com.calendarremember.voz.Planes

/**
 * Lee los mensajes de WhatsApp que llegan, para ofrecer apuntar los planes.
 *
 * Solo funciona si se le da "acceso a notificaciones" en los ajustes del
 * móvil, y eso lo decide el usuario desde los ajustes de Nébula. No guarda ni
 * envía nada: cada mensaje se mira en el momento y se olvida. Si propone algo
 * con día u hora, sale un aviso de Nébula con el botón "Apuntar".
 */
class OyenteWhatsApp : NotificationListenerService() {

    companion object {
        private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
    }

    /**
     * Lo ya visto. WhatsApp vuelve a publicar la notificación de un chat con
     * todos sus mensajes cada vez que llega uno nuevo: sin esto, el mismo
     * plan se ofrecería una y otra vez.
     */
    private val vistos = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 300
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP) return
        val n = sbn.notification ?: return
        // El resumen de "3 mensajes de 2 chats" no trae mensajes propios.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        runCatching { atender(n) }
    }

    private fun atender(n: Notification) {
        val extras = n.extras ?: return
        val esGrupo = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val chat = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()

        // El último mensaje del chat, con quién lo escribe. En los mensajes
        // de estilo conversación, los propios van sin remitente: se saltan.
        @Suppress("DEPRECATION")
        val mensajes = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val (texto, deQuien) = if (!mensajes.isNullOrEmpty()) {
            val ultimo = mensajes.last() as? Bundle ?: return
            val remitente = ultimo.getCharSequence("sender")?.toString() ?: return
            (ultimo.getCharSequence("text")?.toString() ?: return) to remitente
        } else {
            val t = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
            t to (chat ?: "WhatsApp")
        }

        val clave = "$chat|$deQuien|$texto"
        if (vistos.containsKey(clave)) return
        vistos[clave] = true

        Almacen.cargar(this)
        val grupo = if (esGrupo) chat?.substringBefore(" (")?.substringBefore(":") else null
        val plan = Planes.detectar(texto, deQuien.substringBefore(" @").trim(), grupo) ?: return
        // Si ya está apuntado (mismo título y día), no se ofrece otra vez.
        val repetido = Almacen.eventos.value.any {
            it.inicio.toLocalDate() == plan.inicio.toLocalDate() && it.titulo.equals(plan.titulo, ignoreCase = true)
        }
        if (!repetido) Notificaciones.ofrecerPlan(this, plan)
    }
}
