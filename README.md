# Nébula

Calendario y recordatorios por voz para Android. Neón sobre una nebulosa
animada, todo dentro del móvil: sin cuentas, sin servidor y sin conexión.

- **Palabra clave** — di «Nébula» y te escucha: en el escritorio, dentro de
  otra app o con el móvil bloqueado. Si lo activas, también con la pantalla
  apagada, como «Oye Siri».
- **Se habla como con una persona** — contesta «Te escucho» y entonces
  hablas. Apunta, cancela, cambia de día u hora, cambia el nombre, la nota,
  el color o los avisos, y contesta lo que tienes. Sabe de qué evento se está
  hablando («cámbiale el nombre», «cancélalo»), entiende que te corrijas a
  mitad de frase («el martes, no, el miércoles») y, si le falta algo, te lo
  pregunta y escucha la respuesta.
- **Varios días** — «viaje del 27 al 3», «vacaciones del 1 al 15 de
  agosto», «de lunes a viernes», «todo el finde». El calendario lo pinta
  como una barra de principio a fin.
- **Repeticiones** — «todos los martes», «los martes y jueves», «cada dos
  semanas», «de lunes a viernes a las 7», «todos los años».
- **Compartir** — cualquier mensaje (de WhatsApp, un correo) se puede
  compartir con Nébula y se abre el editor con el evento ya preparado.
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

Al oírte dice **«Te escucho»** en voz alta: cuando acaba, hablas. Si el
sistema no le deja abrir el círculo, te atiende igual, solo con la voz. Nunca
hay que tocar nada para que escuche.

### Una conversación, no órdenes sueltas

- **Sabe de qué hablas.** Lo que acabas de apuntar, cambiar o preguntar es
  «el evento del que se habla» durante diez minutos: «¿cuándo es el
  dentista?» y luego «cancélalo»; «apunta la cena del sábado» y luego
  «cámbiale el nombre a cena con Ana», «ponle una nota que diga llevar
  vino», «ponlo en verde», «avísame dos días antes», «retrásalo media hora».
- **Te puedes corregir.** «Ponme el martes una quedada con Bernie, no, el
  miércoles» apunta «Quedada con Bernie» el miércoles. Vale lo último:
  «no», «digo», «perdón», «quiero decir», «mejor dicho». Y, justo después
  de apuntar algo, «no, a las 6» o «mejor el jueves» lo cambian.
- **Pregunta lo que falta.** «Ponme un evento para mañana» no apunta un
  «Recordatorio» a ciegas: pregunta «¿Qué apunto para mañana?» y escucha la
  respuesta. Si hay dos cenas y dices «cancela la cena», pregunta cuál y se
  le contesta hablando: «la de Luis», «la del viernes», «el primero». Si no
  encuentra lo que dices, ofrece apuntarlo: «sí» o «no».
- **No apunta dos veces** lo mismo el mismo día a la misma hora: te dice
  que ya lo tenías.

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
| ¿y el sábado? / y el lunes qué tengo | lo mismo, otro día |
| cambia el nombre de la cena a comida | le cambia el nombre |
| no es cena, es comida / cambia Marta por Ana | corrige el nombre |
| ponle una nota que diga llevar vino | se la añade |
| pon el dentista en azul | le cambia el color |
| avísame una hora antes del dentista | le cambia los avisos |
| el martes, no, el miércoles | vale el miércoles |
| la cena no es el martes, es el jueves | la mueve al jueves |

Las horas sin más («a las cinco») se entienden como la tarde entre la una y
las siete, y como la mañana de las ocho en adelante, que es como se habla.
Con una cena, una fiesta o «esta noche» de por medio, «a las diez» son las
22:00. Para forzarlo: «de la mañana», «de la tarde».

Los números pueden ir en cifras o en palabras («el día veintiocho», «a las
cinco y media de la tarde», «el treinta y uno de octubre»): el reconocedor
sin conexión, el que atiende con el móvil bloqueado, los escribe así.

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
             Planes.kt      Un mensaje compartido, convertido en evento
             Xiaomi.kt      Los permisos propios de MIUI
             EscuchaServicio.kt  Escucha «Nébula»
             VozActivity.kt El círculo de dictado
             ModeloVoz.kt   El modelo de voz sin conexión
             TileVoz.kt     El botón de los ajustes rápidos
  avisos/    Programador.kt Pone las alarmas en el sistema
             ReceptorAviso.kt  Lo que corre cuando salta una
             AlarmaActivity.kt La pantalla de la alarma
             Notificaciones.kt Canales y textos de los avisos
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

Las pruebas corren antes de cada compilación, y si algo se rompe el APK no
llega a generarse:

- `app/src/test/resources/frases.txt`: más de 400 frases revisadas a mano con
  lo que tiene que salir de cada una.
- `GeneradoTest`: más de 18.000 frases generadas combinando formas de pedirlo,
  nombres de eventos, días y horas (en cifras y en palabras), en todos los
  órdenes, para apuntar, cancelar, cambiar, preguntar, editar y corregirse a
  mitad de frase. Cada pieza sabe lo que significa, así que cada frase sabe
  lo que tiene que salir.
- `ConversacionTest`: conversaciones enteras, con el contexto y las
  preguntas de Nébula.

El icono lo genera `herramientas/icono.py` (necesita numpy y Pillow) con el
mismo ruido que el fondo animado de la app.

## Lo que no hace, y por qué

- **Escuchar con la pantalla apagada sin gastar.** Los asistentes de fábrica
  usan un chip de audio que solo pueden usar las apps del sistema. Nébula
  puede escuchar con la pantalla apagada, pero con el procesador: por eso va
  en un ajuste aparte.
- **Apuntar solo los planes de WhatsApp.** Haría falta el acceso a las
  notificaciones, y MIUI no deja dárselo a una app instalada a mano. Queda
  compartir el mensaje con Nébula.
- **Widget en la pantalla de bloqueo.** No depende de la app: Android retiró
  los widgets de terceros del bloqueo y solo los ha devuelto a partir de la
  versión 14, en tablets. El widget ya se declara apto para esa ubicación.
  Mientras tanto, la notificación-agenda cumple esa función.
