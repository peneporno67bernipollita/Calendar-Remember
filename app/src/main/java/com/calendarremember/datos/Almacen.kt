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
        Notificaciones.refrescarAgenda(contexto)
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

    /**
     * Varios de una vez, con una sola escritura. Una serie diaria son sesenta
     * eventos: guardarlos uno a uno reescribiría el fichero y reprogramaría
     * todas las alarmas sesenta veces seguidas.
     */
    fun guardarVarios(contexto: Context, nuevos: List<Evento>) {
        if (nuevos.isEmpty()) return
        val porId = _eventos.value.associateBy { it.id }.toMutableMap()
        nuevos.forEach { porId[it.id] = it }
        _eventos.value = porId.values.sortedBy { it.inicioMillis }
        persistir(contexto)
    }

    fun borrarVarios(contexto: Context, ids: List<String>) {
        if (ids.isEmpty()) return
        val fuera = ids.toSet()
        _eventos.value = _eventos.value.filterNot { it.id in fuera }
        persistir(contexto)
    }

    /** Alarga las series que se acercan a su final. Lo llama la pasada de medianoche. */
    fun alargarSeries(contexto: Context) {
        guardarVarios(contexto, com.calendarremember.voz.Series.alargar(_eventos.value, java.time.LocalDate.now()))
    }

    fun porId(id: String): Evento? = _eventos.value.firstOrNull { it.id == id }

    /** El almacén visto como la agenda sobre la que trabajan las órdenes de voz. */
    fun comoAgenda(contexto: Context): com.calendarremember.voz.Agenda =
        object : com.calendarremember.voz.Agenda {
            override val eventos: List<Evento> get() = _eventos.value
            override fun guardar(evento: Evento) = guardar(contexto, evento)
            override fun borrar(id: String) = borrar(contexto, id)
            override fun guardarVarios(eventos: List<Evento>) = guardarVarios(contexto, eventos)
            override fun borrarVarios(ids: List<String>) = borrarVarios(contexto, ids)
        }

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
