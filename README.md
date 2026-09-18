# Nébula

Calendario y recordatorios por voz para Android. Negro con luces de neón,
todo dentro del móvil: sin cuentas, sin servidor y sin conexión.

- **Palabra clave** — con la pantalla encendida, di «Nébula» y te escucha,
  también sobre la pantalla de bloqueo.
- **Cuatro órdenes por voz** — apuntar, cancelar, cambiar de fecha u hora, y
  preguntar qué tienes. Sin confirmaciones: si está claro, lo hace y lo dice.
- **Repeticiones** — «todos los martes», «cada día», «todos los años».
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

**Diciendo «Nébula»** con la pantalla encendida, en el inicio o en el
bloqueo. Se activa en *Ajustes → Escuchar «Nébula»*, que guía por los
permisos que hacen falta (en Xiaomi, varios). Con la pantalla apagada no
escucha nada.

Al oírte abre el círculo de dictado. Si el sistema no le deja abrirlo —falta
el permiso de mostrarse sobre otras apps, o MIUI lo bloquea sin avisar—, te
atiende igual: suena un pitido, lo dices, y te contesta en voz alta. Nunca
hay que tocar nada para que escuche. Los ajustes dicen qué permiso falta para
tener el círculo.

### Cómo distingue «Nébula»

Está medido, no supuesto. El motor sin conexión no conoce la palabra; la oye
como «nebulosa». Si solo pudiera elegir entre «nebulosa» y nada, cualquier
cosa parecida («me mola», «me gusta») caería en «nebulosa», y con la misma
confianza que un «Nébula» de verdad. Por eso compite con 121 palabras
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
| del 1 al 15 de agosto | empieza el 1 |
| cena de Nochebuena | el 24 de diciembre, con la fiesta en el título |
| clase de yoga todos los martes a las 7 | todos los martes a las 19:00 |
| ...avísame dos días antes | cambia la antelación del aviso |
| cancela la cena del viernes | la borra |
| ya no voy al gimnasio mañana | lo borra |
| cancela todo lo de mañana | borra todo lo de ese día |
| cancela la clase de yoga | borra la próxima; las demás siguen |
| borra todas las clases de yoga | borra la serie entera |
| pasa la cena del viernes al sábado | la mueve de día, a la misma hora |
| retrasa la reunión una hora | la mueve una hora |
| ¿qué tengo mañana? | te lo dice |
| ¿cuándo es el cumpleaños de Laura? | te lo dice |
| ¿cuál es mi próximo evento? | te lo dice |

Las horas sin más («a las cinco») se entienden como la tarde entre la una y
las siete, y como la mañana de las ocho en adelante, que es como se habla.
Con una cena, una fiesta o «esta noche» de por medio, «a las diez» son las
22:00. Para forzarlo: «de la mañana», «de la tarde».

Cuando dos eventos encajan por igual en lo que dices, pregunta cuál; es lo
único que pregunta. Si nada encaja, lo dice y ofrece apuntar la frase, por si
era eso.

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

Las pruebas corren antes de cada compilación: unas 250 frases dictadas y lo
que cada orden hace en la agenda. Si algo se rompe, el APK no llega a
generarse.

## Lo que no hace, y por qué

- **Escuchar con la pantalla apagada.** La palabra clave solo funciona con
  la pantalla encendida, a propósito: oír toda la noche en el bolsillo gasta
  batería y no aporta nada a «enciendo el móvil y le hablo».
- **Widget en la pantalla de bloqueo.** No depende de la app: Android retiró
  los widgets de terceros del bloqueo y solo los ha devuelto a partir de la
  versión 14, en tablets. El widget ya se declara apto para esa ubicación.
  Mientras tanto, la notificación-agenda cumple esa función.
