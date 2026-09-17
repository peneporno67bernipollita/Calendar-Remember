# Nébula

Calendario y recordatorios por voz para Android. Negro con luces de neón,
todo dentro del móvil: sin cuentas, sin servidor y sin conexión.

- **Dictado en español** — «cita con el dentista el martes a las cinco y
  media» se convierte en un evento el martes a las 17:30. Lo entiende la
  propia app, sin enviar nada a ningún sitio.
- **Alarma el día del evento** — suena como un despertador y sale a pantalla
  completa sobre el bloqueo, aunque el móvil lleve horas suspendido.
- **Avisos los días antes** — cada evento elige sus antelaciones: una semana,
  dos días, un día, una hora, a la hora en punto.
- **Widget en la pantalla de inicio** — los tres próximos eventos y un botón
  de micrófono que dicta de un toque.
- **Agenda del día en la pantalla de bloqueo** — lo que te queda por hacer hoy,
  sin desbloquear. Va sola: quita cada evento en cuanto pasa su hora y
  desaparece cuando no queda nada.

## Instalar

1. Entra en la pestaña **Releases** de este repositorio.
2. Descarga `Nebula.apk` desde el móvil.
3. Ábrelo. Android pedirá permiso para instalar apps de esta procedencia:
   concédelo y acepta.
4. Al abrirla por primera vez, dale permiso de notificaciones y entra en
   **Ajustes → Permitir alarmas exactas**. Sin eso, Android puede retrasar
   los avisos.

No hace falta Android Studio ni compilar nada: cada cambio en `main` genera
un APK nuevo automáticamente.

## Dictar un evento

Cuatro caminos, ninguno de los cuales deja nada escuchando en segundo plano:

- **Ajustes rápidos**: baja la persiana y toca «Dictar». Se llega desde la
  pantalla de bloqueo.
- **Icono suelto**: mantén pulsado el icono de la app, arrastra «Dictar» a la
  pantalla de inicio y queda como un acceso directo propio.
- **Gesto del sistema**: la app responde al gesto de asistente, así que se
  puede abrir manteniendo pulsado el botón de encendido si se configura como
  asistente. En MIUI, *Configuración adicional → Atajos de gestos* permite
  además asignarla a un gesto cualquiera.
- **Dentro de la app**: el botón grande de abajo.

La app repite en voz baja lo que ha entendido —día y hora completos— para que
un error se note en el momento. Si no entiende de qué va el evento, abre la
ficha para que la corrijas en vez de guardar algo torcido.

### Lo que entiende

| Dices | Guarda |
|---|---|
| mañana a las nueve | el día siguiente, 09:00 |
| el martes a las cinco y media | próximo martes, 17:30 |
| el 25 de octubre | ese día, sin hora |
| dentro de dos horas | dos horas desde ahora |
| el viernes a las 21:30 | próximo viernes, 21:30 |
| a las siete menos cuarto de la mañana | 06:45 |
| el día 1 | el día 1 del mes que viene |
| ...avísame dos días antes | cambia la antelación del aviso |
| ...durante dos horas | fija la duración |

Las horas sin más ("a las cinco") se entienden como la tarde entre la una y
las siete, y como la mañana de las ocho en adelante, que es como se habla.
Para forzarlo: "de la mañana", "de la tarde".

## Estructura

```
app/src/main/java/com/calendarremember/
  MainActivity.kt          Pantalla principal y permisos
  datos/     Evento.kt      Un evento y su paso a JSON
             Almacen.kt     Todos los eventos en un fichero, y su guardado
  voz/       Interprete.kt  Convierte la frase dictada en un evento
             VozActivity.kt Dictar de un toque, sin abrir el calendario
  avisos/    Programador.kt Pone las alarmas en el sistema
             ReceptorAviso.kt  Lo que corre cuando salta una
             AlarmaActivity.kt La pantalla de la alarma
             Notificaciones.kt Canales y textos de los avisos
  widget/    WidgetProximos.kt El widget de la pantalla de inicio
  ui/        Tema.kt, Pantalla.kt, DialogoEvento.kt, DialogoAjustes.kt
```

Los eventos viven en `eventos.json`, dentro de la app. **Ajustes → Exportar
copia** lo saca entero para guardarlo o pasarlo a otro móvil.

## Compilar

No hace falta en local: `.github/workflows/apk.yml` lo hace en cada push y
publica el APK en Releases. Si quieres hacerlo en tu máquina, con JDK 17:

```bash
gradle :app:assembleRelease
```

Las pruebas del intérprete de voz corren antes de cada compilación, así que
si una regla de interpretación se rompe, el APK no llega a generarse.

## Lo que no hace, y por qué

- **Palabra clave con la pantalla apagada** («oye Nébula»). Descartado a
  propósito. Exige un servicio con el micrófono abierto a todas horas, una
  notificación permanente que Android obliga a mostrar y un consumo continuo
  de batería; encima, las capas que matan procesos en segundo plano lo cortan
  cada dos por tres. Los cuatro atajos de arriba llegan a lo mismo con un
  gesto y sin coste. Si algún día compensa, se añade sobre esta base.
- **Widget en la pantalla de bloqueo.** No depende de la app: Android retiró
  los widgets de terceros del bloqueo y solo los ha devuelto a partir de la
  versión 14, en tablets. El widget ya se declara apto para esa ubicación, así
  que aparecerá en los dispositivos que lleguen a admitirlo. Mientras tanto,
  la agenda del día en la pantalla de bloqueo cumple esa función.
- Eventos que se repiten.
