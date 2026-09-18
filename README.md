# Nébula

Calendario y recordatorios por voz para Android. Neón sobre una nebulosa
animada, todo dentro del móvil: sin cuentas, sin servidor y sin conexión.

- **Palabra clave** — di «Nébula» y te escucha: en el escritorio, dentro de
  otra app o con el móvil bloqueado. Si lo activas, también con la pantalla
  apagada, como «Oye Siri».
- **Cuatro órdenes por voz** — apuntar, cancelar, cambiar de fecha u hora, y
  preguntar qué tienes. Sin confirmaciones: si está claro, lo hace y lo dice.
- **Varios días** — «viaje del 27 al 3», «vacaciones del 1 al 15 de
  agosto», «de lunes a viernes», «todo el finde». El calendario lo pinta
  como una barra de principio a fin.
- **Repeticiones** — «todos los martes», «los martes y jueves», «cada dos
  semanas», «de lunes a viernes a las 7», «todos los años».
- **Planes de WhatsApp** — si te proponen un plan con día u hora, te
  ofrece apuntarlo de un toque. Y cualquier mensaje se puede compartir con
  Nébula para apuntarlo.
- **Alarma el día del evento** — suena como un despertador y sale a pantalla
  completa sobre el bloqueo, aunque el móvil lleve horas suspendido.
- **Avisos los días antes** — cada evento elige sus antelaciones: una semana,
  dos días, un día, una hora, a la hora en punto.
- **Widget en la pantalla de inicio** — los tres próximos eventos y un botón
  de micrófono que dicta de un toque.
- **Agenda en la pantalla de bloqueo** — una notificación fija que hace de
  widget: plegada, lo siguiente que toca; desplegada, los cuatro próximos
  eventos con sus colores y botones para dictar o abrir la app. Se pone al
  día sola al empezar cada evento y a medianoche. Se apaga en Ajustes.

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

## Hablarle

**Diciendo «Nébula»**. Se activa en *Ajustes → Escuchar «Nébula»*, que guía
por los permisos que hacen falta y dice cuáles faltan (en Xiaomi, varios:
inicio automático, ventanas emergentes en segundo plano y mostrar en
pantalla de bloqueo).

- **Con el móvil desbloqueado** abre el círculo de dictado encima de lo que
  estés haciendo.
- **Con el móvil bloqueado** lo abre encima del bloqueo, sin pedir la
  contraseña: lo lanza como un aviso de pantalla completa, igual que una
  llamada o la alarma de un evento.
- **Con la pantalla apagada**, solo si activas «También con la pantalla
  apagada». Enciende la pantalla y abre el círculo. Si el sensor de
  proximidad está tapado (en un bolsillo), no enciende nada: te escucha con
  un pitido y te contesta en voz alta. Gasta más batería, porque el
  procesador no puede dormirse; para gastar menos, mientras hay silencio el
  reconocedor no recibe audio (medido con las mismas grabaciones: detecta
  exactamente lo mismo).

Si el sistema no le deja abrir el círculo, te atiende igual: suena un
pitido, lo dices, y te contesta en voz alta. Nunca hay que tocar nada para
que escuche.

### Cómo distingue «Nébula»

Está medido, no supuesto. El motor sin conexión no conoce la palabra; la oye
como «nebulosa». Si solo pudiera elegir entre «nebulosa» y nada, cualquier
cosa parecida («me mola», «me gusta») caería en «nebulosa», y con la misma
confianza que un «Nébula» de verdad. Por eso compite con 120 palabras
corrientes, y además tiene que mantenerse medio segundo. Con audio de prueba:

| | Antes | Ahora |
|---|---|---|
| «Nébula» dicho en una conversación | — | 12 de 12 |
| Falsas activaciones en charla normal | 9 en 43 s | 0 en 140 s |
| Frases parecidas sueltas («me mola», «de bola»...) | 37 de 240 | 2 de 240 |

Y sin palabra clave, cuatro caminos más, todos sin desbloquear:

- **Ajustes rápidos**: baja la persiana y toca «Dictar».
- **Icono suelto**: mantén pulsado el icono de la app y arrastra «Dictar» a la
  pantalla de inicio.
- **Gesto del sistema**: la app responde al gesto de asistente. En MIUI,
  *Configuración adicional → Atajos de gestos* permite asignarla a un gesto.
- **Dentro de la app**: el botón grande de abajo.

### Lo que entiende

| Dices | Hace |
|---|---|
| mañana a las cinco cena con Marta | apunta para mañana a las 17:00 |
| el martes 29 a las 9 y cuarto | ese día a las 09:15 |
| sobre las 6 / a eso de las 7 | 18:00 / 19:00 |
| reunión de 5 a 7 | a las 17:00, dos horas |
| el lunes por la mañana | el lunes a las 09:00 |
| en 10 minutos / dentro de media hora | desde ahora |
| el lunes que viene | el lunes siguiente (dicho un jueves, el de dentro de 4 días) |
| viaje con Bernie del 27 al 25 del mes siguiente | del 27 de este mes al 25 del siguiente |
| del 1 al 15 de agosto / de lunes a viernes | un evento de varios días |
| estoy de vacaciones hasta el domingo | desde hoy hasta el domingo |
| viaje a Roma el lunes durante 3 días | de lunes a miércoles |
| todo agosto / toda la semana que viene / todo el finde | el tramo entero |
| a finales de mes / a principios de octubre / en agosto | el día que toca |
| mañana a las 12 de la noche | la medianoche de mañana |
| despiértame a las 7 | a las 07:00, no a las 19:00 |
| cena de Nochebuena | el 24 de diciembre, con la fiesta en el título |
| clase de yoga todos los martes a las 7 | todos los martes a las 19:00 |
| gimnasio los martes y jueves a las 7 | los dos días, cada semana |
| limpiar el coche cada dos semanas el sábado | un sábado sí y otro no |
| alarma de lunes a viernes a las 7 | cada día laborable |
| ...avísame dos días antes | cambia la antelación del aviso |
| cancela la cena del viernes | la borra |
| ya no voy al gimnasio mañana / olvida lo del dentista | lo borra |
| borra lo último que he apuntado | lo más nuevo (y si se repetía, entero) |
| cancela todo lo de mañana | borra todo lo de ese día |
| cancela la clase de yoga | borra la próxima; las demás siguen |
| borra todas las clases de yoga | borra la serie entera |
| pasa la cena del viernes al sábado | la mueve de día, a la misma hora |
| retrasa la reunión una hora | la mueve una hora |
| la reunión es ahora a las 6 | la cambia a las 18:00 |
| alarga el viaje hasta el domingo / dos días | cambia dónde acaba |
| ¿qué tengo mañana? | te lo dice |
| ¿cuándo es el cumpleaños de Laura? | te lo dice |
| ¿cuál es mi próximo evento? | te lo dice |
| ¿estoy libre el viernes? / ¿qué tengo en octubre? | te lo dice |

Las horas sin más («a las cinco») se entienden como la tarde entre la una y
las siete, y como la mañana de las ocho en adelante, que es como se habla.
Con una cena, una fiesta o «esta noche» de por medio, «a las diez» son las
22:00. Para forzarlo: «de la mañana», «de la tarde».

Cuando dos eventos encajan por igual en lo que dices, pregunta cuál; es lo
único que pregunta. Si nada encaja, lo dice y ofrece apuntar la frase, por si
era eso.

## Planes de WhatsApp

Nébula no puede ver lo que contestas en WhatsApp, pero sí los mensajes que
te llegan, por sus notificaciones, si le das permiso (*Ajustes → Planes de
WhatsApp*, que lleva a «Acceso a notificaciones»). Cuando uno propone algo
con día u hora («¿quedamos el sábado a las 9 para cenar?»), sale un aviso:
«¿Lo apunto? Cenar con Marta · sáb 21:00», con **Apuntar** (lo guarda sin
abrir nada) y **Cambiar** (lo abre en el editor, ya relleno). Nada sale del
móvil y los mensajes no se guardan.

Mejor callar ante la duda: los mensajes que no proponen nada, los que
hablan de algo pasado («ayer quedamos…») y los que lo cancelan («no puedo
el sábado») no dan aviso. Con 31 mensajes de prueba: los 15 planes
detectados, ninguno de los 16 que no lo son.

Además, cualquier mensaje se puede **compartir** con Nébula (mantener
pulsado → Compartir): se abre el editor con el evento ya preparado.

## Estructura

```
app/src/main/java/com/calendarremember/
  MainActivity.kt          Pantalla principal y permisos
  datos/     Evento.kt      Un evento y su paso a JSON
             Almacen.kt     Todos los eventos en un fichero, y su guardado
             Preferencias.kt Los ajustes
  voz/       Interprete.kt  La frase dictada, convertida en una orden
             Ejecutor.kt    Hace la orden y dice qué ha hecho
             Buscador.kt    De qué evento habla una orden
             Series.kt      Lo que se repite
             Planes.kt      Los planes que llegan por WhatsApp
             Xiaomi.kt      Los permisos propios de MIUI
             EscuchaServicio.kt  Escucha «Nébula»
             VozActivity.kt El círculo de dictado
             ModeloVoz.kt   El modelo de voz sin conexión
             TileVoz.kt     El botón de los ajustes rápidos
  avisos/    Programador.kt Pone las alarmas en el sistema
             ReceptorAviso.kt  Lo que corre cuando salta una
             AlarmaActivity.kt La pantalla de la alarma
             Notificaciones.kt Canales y textos de los avisos
             OyenteWhatsApp.kt Lee los mensajes que llegan
  widget/    WidgetProximos.kt El widget de la pantalla de inicio
  ui/        Pantallas y diálogos
             FondoNebula.kt La nebulosa animada del fondo
```

Los eventos viven en `eventos.json`, dentro de la app. **Ajustes → Exportar
copia** lo saca entero para guardarlo o pasarlo a otro móvil. Lo que se
repite se guarda como eventos normales de una misma serie, creados por
adelantado (60 días lo diario, medio año lo semanal); la pasada de medianoche
los va alargando.

## Compilar

No hace falta en local: `.github/workflows/apk.yml` lo hace en cada push y
publica el APK en Releases. El modelo de voz (40 MB) no está en el
repositorio: lo descarga la compilación. En una máquina con JDK 17:

```bash
gradle :app:assembleRelease
```

Las pruebas corren antes de cada compilación. Además de las de cada regla,
`app/src/test/resources/frases.txt` guarda más de 300 frases con lo que
tiene que salir de cada una, revisado a mano, y `planes.txt` los mensajes
de WhatsApp: si un cambio altera cualquiera, la prueba lo dice con la frase.
Si algo se rompe, el APK no llega a generarse.

El icono lo genera `herramientas/icono.py` (necesita numpy y Pillow) con el
mismo ruido que el fondo animado de la app.

## Lo que no hace, y por qué

- **Escuchar con la pantalla apagada sin gastar.** Los asistentes de fábrica
  usan un chip de audio que solo pueden usar las apps del sistema. Nébula
  puede escuchar con la pantalla apagada, pero con el procesador: por eso va
  en un ajuste aparte.
- **Ver lo que contestas en WhatsApp.** WhatsApp no deja leer los chats;
  solo se ven los mensajes que llegan, y por eso Apuntar es un toque.
- **Widget en la pantalla de bloqueo.** No depende de la app: Android retiró
  los widgets de terceros del bloqueo y solo los ha devuelto a partir de la
  versión 14, en tablets. El widget ya se declara apto para esa ubicación.
  Mientras tanto, la notificación-agenda cumple esa función.
