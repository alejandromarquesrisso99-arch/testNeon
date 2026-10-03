# Neon Steps

Contador de pasos para Android ambientado en el mercado nocturno de **District 9**: una calle
cyberpunk renderizada en vivo, con lluvia, neones que fallan y un koi holográfico, y tus pasos
del día como título en neón.

<p>
  <img src="docs/main.jpg" width="270" alt="Pantalla principal">
  <img src="docs/history.jpg" width="270" alt="Historial de 7 días">
  <img src="docs/goal.jpg" width="270" alt="Meta cumplida: Torre 61 encendida y fuegos artificiales">
</p>

## Instalar

1. Descarga [`dist/NeonSteps.apk`](dist/NeonSteps.apk) en el móvil (Android 8.0 o superior).
2. Ábrela y permite «Instalar apps de origen desconocido» cuando lo pida.
3. Al abrir la app, concede **Actividad física** (necesario para contar pasos) y
   **Notificaciones** (para ver el contador en la barra de estado).

La APK está firmada con una clave de depuración: si más adelante instalas una versión
compilada en otro ordenador, desinstala antes la anterior.

## Qué hace

- **Pasos de hoy** como título gigante con aberración cromática, más un tubo de neón con el
  progreso hacia la meta.
- **HUD**: hora local, distancia, calorías y barras de «señal» con el % de la meta.
- **META**: toca para cambiar la meta diaria (4.000 – 20.000 pasos).
- **HISTORIAL**: últimos 7 días con la línea de meta; toca una columna para ver su valor.
- **LLUVIA**: AUTO (llueve más fuerte cuanto más rápido caminas), llovizna, lluvia o aguacero.
- **SONIDO**: lluvia, zumbido de neones, truenos tras cada rayo y los efectos de la ciudad,
  todo generado por la app (desactivado por defecto).
- **Perfil**: toca el panel de datos para poner tu altura y peso; la distancia (zancada = 41,4 %
  de la altura) y las calorías (0,75 kcal por kg y km) se ajustan a ti.
- **Rachas**: días seguidos cumpliendo la meta. District 9 crece con ellas y conserva lo ganado
  mientras dure la racha: letrero «ファイト» (2 días), puesto de dango (3), farolillos (5), un
  segundo koi (7), un dirigible con tu racha (14) y, a los 30, la «L» del HOTEL arreglada para siempre.
- **Encargo del día**: cada día el noticiero te da un encargo distinto (llegar a X pasos antes
  de una hora, superar los pasos de ayer, caminar 15 minutos a buen ritmo, recorrer X km...),
  ajustado a tu meta. Aparece en una tarjeta sobre el HUD con el progreso y la recompensa, y
  llega como notificación a partir de las 7:00 (y otra cuando lo cumples).
- **Colección de estilos**: cada encargo cumplido desbloquea una recompensa para District 9 y
  se pone al momento: lluvia cian, rosa o dorada, koi sakura, esmeralda o sombra, luna llena o
  una aurora sobre la ciudad. Toca la tarjeta del encargo para ver la colección y cambiar de
  estilo; con la colección completa, los encargos se pagan en fuegos artificiales.
- **Hora real**: el cielo sigue la hora local. Amanece de madrugada, de día el smog se vuelve
  gris y los neones pierden fuerza, atardece en naranja y violeta, y de noche se apagan
  ventanas a medida que el distrito se va a dormir.
- **Tráfico aéreo**: coches voladores en tres carriles a distinta profundidad, con faros que
  cortan la lluvia y estelas de luz. Hay atascos en hora punta y apenas pasan de madrugada;
  con el sonido activado se oyen pasar los más cercanos.
- **Widget** para la pantalla de inicio con tus pasos en neón; la Torre 61 se enciende y hay
  fuegos artificiales al cumplir la meta.
- **Desliza el dedo** de lado a lado para recorrer la calle: cada capa de edificios se mueve a
  su propia velocidad (las lejanas apenas, el mercado más que nada) y al soltar vuelve sola.
- Cada 1.000 pasos cae un relámpago. Toca la calle para invocar uno.
- **Al llegar a la meta**:
  - Doble relámpago y una salva de fuegos artificiales. Si la app estaba cerrada, la
    celebración se reproduce la primera vez que la abras ese día.
  - Llega un aviso «¡META CUMPLIDA!» con sonido, una sola vez al día (si tienes la app abierta,
    la celebración se ve en pantalla y no hace falta el aviso).
  - La Torre 61 se enciende planta a planta en neón y se queda iluminada.
  - Fuegos artificiales en el cielo, detrás de los edificios, hasta medianoche.
- El noticiero **D9 WIRE** comenta tu caminata (y el letrero del HOTEL, que sigue sin su «L»).
- Inclinar el móvil también desplaza un poco las capas.

<details>
<summary><b>Easter eggs</b> (spoilers)</summary>

- **El HOTEL tiene arreglo**: toca 5 veces seguidas la «L» apagada. Chisporrotea, falla... y
  al quinto toque prende con una lluvia de chispas. Dura hasta medianoche; al día siguiente
  vuelve a fallar, claro.
- **Koi dorado**: toca 3 veces el koi holográfico. Se escapa del haz de la Torre 61, se vuelve
  dorado y vuela por todo el cielo durante un minuto dejando purpurina («SEÑAL PERDIDA»).
- **Apagón**: mantén pulsado el número de pasos (el móvil vibra mientras se va la luz).
  District 9 se queda a oscuras (solo brillan la
  lluvia, los paraguas y tus pasos) y luego vuelve la luz a trozos, con los neones arrancando
  a trompicones.

- **Kage Bunshin**: toca 3 veces el letrero RAMEN. Parpadea y se convierte en 一楽, un ninja sale
  corriendo del puesto con los brazos hacia atrás y, ¡puf!, la acera se llena de clones entre
  nubes de humo (el móvil vibra con cada «puf»).
- **Ramen holográfico**: toca el puesto amarillo. El proyector de la Torre 61 cambia el koi por un
  cuenco de ramen en neón, con vapor y un narutomaki girando, durante 30 segundos.
- **Cuencos de ramen**: el noticiero cuenta tus calorías en cuencos de ramen. Dattebayo.

El noticiero D9 WIRE da la exclusiva de cada uno.
</details>

## Cómo cuenta

- Usa el **podómetro de hardware** (`TYPE_STEP_COUNTER`) desde un servicio en primer plano
  con notificación, así sigue contando con la pantalla apagada y gasta muy poca batería.
- El total sobrevive a reinicios del móvil y a que el sistema cierre la app: el contador de
  hardware es acumulativo y la app recupera la diferencia al volver.
- Los pasos se cuentan desde la instalación; el día cambia a medianoche (hora local).
- Si el móvil no tiene podómetro, usa el detector de pasos y, como último recurso, el
  acelerómetro (que solo cuenta con la pantalla encendida).
- Distancia y calorías son estimaciones: sin perfil se usan 0,75 m por paso y 70 kg.

## Compilar

Requiere JDK 17+ y el Android SDK (plataforma 36).

```sh
./gradlew assembleRelease          # APK en app/build/outputs/apk/release/
./gradlew testDebugUnitTest        # tests + capturas en app/build/screenshots/
```

Los tests usan Robolectric con gráficos nativos para renderizar la pantalla real en el JVM y
guardar capturas PNG, de modo que la escena se puede revisar sin dispositivo. GitHub Actions
compila la APK en cada push (artefacto `neon-steps-apk`).

## Estructura

| Ruta | Contenido |
|---|---|
| `data/StepRepository.kt` | Pasos por día, cambio de día, reinicios del sensor, ritmo, encargos y estilos |
| `data/Missions.kt` | Tipos de encargo y catálogo de estilos desbloqueables |
| `service/StepCounterService.kt` | Servicio en primer plano que mantiene el sensor activo |
| `ui/scene/` | La calle: skyline en paralaje, ciclo de día, tráfico aéreo, letreros de neón, koi, mercado, lluvia, reflejo mojado |
| `ui/hud/` | Cabecera, HUD, tarjeta del encargo, colección, botones, historial y noticiero |

## Créditos

Tipografías de Google Fonts: Roboto y Share Tech Mono, ambas bajo la SIL Open Font License 1.1.
