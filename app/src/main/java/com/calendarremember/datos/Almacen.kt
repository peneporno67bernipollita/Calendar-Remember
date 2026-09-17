package com.calendarremember.datos

import android.content.Context
import com.calendarremember.avisos.Notificaciones
import com.calendarremember.avisos.Programador
import com.calendarremember.widget.WidgetProximos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * Todos los eventos, en un fichero JSON dentro de la app.
 *
 * Para una agenda personal no hace falta una base de datos: son unos cientos
 * de registros que caben de sobra en memoria. A cambio, la copia de seguridad
 * es el propio fichero y exportarla no cuesta nada.
 *
 * Cada escritura hace lo mismo, siempre junto, porque olvidar una de esas
 * cosas es justo lo que produce un evento sin alarma, un widget desfasado o
 * una agenda que sigue anunciando algo que ya pasó: guardar, reprogramar las
 * alarmas, refrescar el widget y repintar la agenda del día.
 */
object Almacen {

    private const val FICHERO = "eventos.json"

    private val _eventos = MutableStateFlow<List<Evento>>(emptyList())
    val eventos: StateFlow<List<Evento>> = _eventos.asStateFlow()

    private var cargado = false

    fun cargar(contexto: Context) {
        if (cargado) return
        cargado = true
        val fichero = File(contexto.filesDir, FICHERO)
        if (!fichero.exists()) return
        runCatching {
            val arr = JSONArray(fichero.readText())
            _eventos.value = (0 until arr.length())
                .map { Evento.deJson(arr.getJSONObject(it)) }
                .sortedBy { it.inicioMillis }
        }
    }

    private fun persistir(contexto: Context) {
        val arr = JSONArray()
        _eventos.value.forEach { arr.put(it.aJson()) }
        runCatching {
            File(contexto.filesDir, FICHERO).writeText(arr.toString())
        }
        Programador.reprogramarTodo(contexto, _eventos.value)
        WidgetProximos.refrescar(contexto)
        Notificaciones.refrescarAgendaDelDia(contexto)
    }

    fun guardar(contexto: Context, evento: Evento) {
        val lista = _eventos.value.toMutableList()
        val i = lista.indexOfFirst { it.id == evento.id }
        if (i >= 0) lista[i] = evento else lista.add(evento)
        _eventos.value = lista.sortedBy { it.inicioMillis }
        persistir(contexto)
    }

    fun borrar(contexto: Context, id: String) {
        _eventos.value = _eventos.value.filterNot { it.id == id }
        persistir(contexto)
    }

    fun porId(id: String): Evento? = _eventos.value.firstOrNull { it.id == id }

    /** Eventos de hoy en adelante, que es lo que mira el widget. */
    fun proximos(limite: Int = 20): List<Evento> {
        val corte = System.currentTimeMillis() - 12 * 3600_000L
        return _eventos.value.filter { it.inicioMillis >= corte }.take(limite)
    }

    fun exportar(): String {
        val arr = JSONArray()
        _eventos.value.forEach { arr.put(it.aJson()) }
        return arr.toString(2)
    }

    /** Importa una copia sin duplicar: los ids que ya existen se sustituyen. */
    fun importar(contexto: Context, texto: String): Int {
        val arr = JSONArray(texto)
        val entrantes = (0 until arr.length()).map { Evento.deJson(arr.getJSONObject(it)) }
        val porId = _eventos.value.associateBy { it.id }.toMutableMap()
        entrantes.forEach { porId[it.id] = it }
        _eventos.value = porId.values.sortedBy { it.inicioMillis }
        persistir(contexto)
        return entrantes.size
    }
}
