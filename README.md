# Calendar Remember

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
- **Agenda del día fija en la pantalla de bloqueo** — lo que tienes hoy, sin
  desbloquear.

## Instalar

1. Entra en la pestaña **Releases** de este repositorio.
2. Descarga `CalendarRemember.apk` desde el móvil.
3. Ábrelo. Android pedirá permiso para instalar apps de esta procedencia:
   concédelo y acepta.
4. Al abrirla por primera vez, dale permiso de notificaciones y entra en
   **Ajustes → Permitir alarmas exactas**. Sin eso, Android puede retrasar
   los avisos.

No hace falta Android Studio ni compilar nada: cada cambio en `main` genera
un APK nuevo automáticamente.

## Dictar un evento

Tres caminos, del más rápido al más cómodo:

- **Widget**: el botón del micrófono, un toque.
- **Dentro de la app**: el botón grande de abajo.
- **Con la voz**: «Hey Google, abre Dictar un evento». Funciona con la
  pantalla bloqueada; Google pedirá desbloquear antes de escuchar.

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

## Lo que todavía no hace

- **Palabra clave propia con la pantalla apagada** («oye calendario»). Android
  no deja que una app escuche en segundo plano sin un motor de detección
  aparte. Se puede añadir sobre esta misma base.
- **Widget en la pantalla de bloqueo.** En móviles Android no existen: solo
  hay widgets en la pantalla de inicio. Lo más parecido, y ya está puesto, es
  la notificación fija con la agenda del día.
- Eventos que se repiten.
