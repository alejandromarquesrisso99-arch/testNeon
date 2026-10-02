# Proyecto: misma app de pasos, nueva estética

Esta carpeta es una **copia completa de Neon Steps** (la app de la raíz del repositorio), hecha para
rehacerla con **otra estética y otros Easter eggs**. La copia compila y pasa sus 33 pruebas tal cual.
La app de la raíz es la original: no la toques desde aquí.

El usuario habla español; la interfaz y los textos de la app están en español de España.

## Lo primero al empezar

1. **Cambia la identidad de la app** para que se pueda instalar junto a la original (si no, la
   sobrescribe): `applicationId` en `app/build.gradle.kts`, `app_name` en `res/values/strings.xml` y,
   si se quiere, el paquete (`namespace` y `com.district9.neonsteps` en los `.kt`, `AndroidManifest.xml`
   y `res/layout/*.xml`, que referencian las vistas por nombre completo).
2. **Pregunta al usuario** qué estética y qué Easter eggs quiere antes de rehacer la escena.

## Compilar en una sesión en la nube

- JDK 17+ y Android SDK con `platforms;android-36` y `build-tools;36.1.0`. Si no hay SDK:
  descarga `commandlinetools-linux-*_latest.zip` de `dl.google.com/android/repository/`, descomprímelo
  en `~/android-sdk/cmdline-tools/latest`, acepta licencias con `sdkmanager --licenses` e instala
  `"platform-tools" "platforms;android-36" "build-tools;36.1.0"`.
- Crea `local.properties` con `sdk.dir=/ruta/al/sdk` (está en `.gitignore`).
- Si Maven Central responde **429 Too Many Requests**, añade el mirror de Google solo en local, en
  `~/.gradle/init.d/central-mirror.gradle` (no en el proyecto):
  ```groovy
  def mirror = { RepositoryHandler repos ->
      def r = repos.maven { url 'https://maven-central.storage-download.googleapis.com/maven2/'; name 'CentralMirror' }
      repos.remove(r)
      repos.addFirst(r)
  }
  settingsEvaluated { settings ->
      mirror(settings.pluginManagement.repositories)
      mirror(settings.dependencyResolutionManagement.repositories)
  }
  ```
- Comandos (desde esta carpeta): `./gradlew testDebugUnitTest lintDebug assembleRelease`.
  La caché de Gradle puede saltarse las pruebas; usa `testDebugUnitTest --rerun` para forzarlas.
- La APK release se firma con la clave de depuración local (`signingConfigs.debug`) para poder
  instalarla directamente. Se entrega copiándola a `dist/` y enviándola al usuario.
- No hay emulador (sin KVM). **La forma de ver la app es Robolectric con gráficos nativos**: las
  pruebas renderizan la actividad real y guardan PNG en `app/build/screenshots/` (míralos con Read).
  El sintetizador escribe una muestra en `app/build/sounds/demo.wav`.

## Arquitectura

Kotlin, sin dependencias de runtime (sin AndroidX), minSdk 26, targetSdk 36. Todo el dibujo es Canvas.

**Independiente de la estética (reutilizable tal cual):**
- `data/StepRepository.kt` — pasos por día, cambio de día, reinicios del podómetro, ritmo, meta y la
  meta de cada día (para las rachas), rachas y racha perdida, perfil (altura/peso → zancada y kcal),
  marcas "una vez al día", preferencias (lluvia, sonido). Tiene pruebas en `data/StepRepositoryTest.kt`.
- `service/StepCounterService.kt` — servicio en primer plano tipo `health` con el podómetro
  (respaldo: detector de pasos y acelerómetro), notificación fija, aviso de meta cumplida una vez al
  día (canal propio), refresco del widget. `BootReceiver.kt` lo rearranca.
- `util/Format.kt` — formato español (6.482 · 4,86 KM).
- `MainActivity.kt` — permisos, ciclo de vida, une datos y vistas, modales (historial y perfil) con
  atrás predictivo, insets edge-to-edge, sensor de inclinación, titulares del noticiero (los textos
  son temáticos) y celebración de la meta.
- `widget/StepsWidget.kt` — widget con TextViews reales (texto nítido) y un fondo pequeño en
  bitmap (los widgets tienen límite de tamaño de transacción).
- `audio/AmbientSound.kt` (AudioTrack en su hilo) y `audio/AmbientSynth.kt` (sintetizador puro,
  probado offline). La escena le habla por la interfaz `ui/scene/SceneAudio.kt`.

**Específico de la estética actual (lo que hay que rehacer):**
- `ui/Neon.kt` — paleta y tipografías (`res/font/`: Roboto Black/Bold y Share Tech Mono).
- `ui/scene/` — la calle de District 9: `CityScene.kt` orquesta todo; `Skyline.kt` (capas en
  paralaje, ventanas, Torre 61), `NeonSign.kt` (letreros prerenderizados con fallos de neón y caracteres
  trazados a mano), `Hologram.kt` (koi y cuenco de ramen), `Market.kt` (puestos, vapor, peatones),
  `WetStreet.kt` (reflejo mojado), `Rain.kt`, `Lightning.kt`, `Fireworks.kt`, `Lanterns.kt`,
  `Airship.kt`, `Ninjas.kt`, `Sparks.kt`; `SceneView.kt` es la vista: bucle de animación, arrastre
  con inercia y muelle, toques y pulsación larga, vibraciones.
- `ui/hud/` — cabecera (número con aberración cromática), panel de datos, botones, noticiero,
  historial (gráfico de 7 días), perfil, scanlines. La lógica es reutilizable; el estilo no.
- Recursos: icono (`drawable/ic_launcher_*`, `mipmap-anydpi-v26`), `strings.xml` (muchos textos
  mencionan District 9 / neón), `widget_preview.png` (regenerarla desde `WidgetTest`).
- `data/StreakPerks.kt` — los premios de racha (letrero, puesto de dango, farolillos, segundo koi,
  dirigible, HOTEL arreglado) son de esta estética: la mecánica de rachas se queda, los premios cambian.

**Easter eggs actuales (a sustituir por los nuevos):** 5 toques en la «L» del HOTEL; 3 toques al koi
(koi dorado); pulsación larga en el número (apagón); 3 toques en RAMEN (Kage Bunshin); toque en el
toldo amarillo (cuenco de ramen holográfico); calorías en cuencos de ramen en el noticiero; toque en la
calle → rayo. Viven en `CityScene.tap()`, `SceneView` (gestos), `MainActivity.onEasterEgg()` y los
titulares; las pruebas están en `EasterEggTest.kt`. El README tiene una sección de spoilers.

## Funciones que el usuario espera conservar

Conteo fiable en segundo plano; meta configurable (4.000–20.000); HUD con hora, distancia, calorías y
% de meta; historial de 7 días; perfil de altura y peso (se abre tocando el HUD); rachas que hacen
crecer el escenario; celebración al llegar a la meta (rayos, edificio que se ilumina, fuegos hasta
medianoche, se repite al abrir si te la perdiste, aviso con sonido una vez al día); arrastrar para
paralaje (y la inclinación); modos de lluvia (AUTO sigue el ritmo al caminar); sonido ambiente opcional;
noticiero inferior; widget.

## Lecciones aprendidas (no repetir errores)

- `performHapticFeedback` no hace nada si el usuario desactivó la vibración al tocar: para efectos
  usa `Vibrator` con `VibrationAttributes.USAGE_MEDIA` (permiso `VIBRATE`).
- `BlurMaskFilter` y los `Xfermode` especiales solo en Canvas de software: prerenderiza en bitmaps al
  cambiar de tamaño y en cada fotograma solo compón (nada de asignaciones por fotograma).
- Las zonas de toque se calculan en `update()`, no en `draw()` (si no, quedan un fotograma atrás y
  en las pruebas no existen).
- Al permitir arrastrar, los bordes generados se ven: genera edificios con anchura completa hacia
  fuera de la composición hecha a mano, y que todos tengan ventanas.
- `mipmap-anydpi` (sin `-v26`) rompe la fusión de recursos de AGP 8.13 con iconos adaptativos.
- Android 13+ usa atrás predictivo: `OnBackInvokedCallback` (lint marca `onBackPressed` como error).
- A partir de Android 16 la orientación fija se ignora en pantallas grandes: la escena debe adaptarse.
- El horizonte se coloca según la parte superior del HUD (`SceneView.setStreetLimit`) para que la
  calle siempre se vea en cualquier proporción de pantalla.
- Antes de afirmar que algo funciona, renderízalo y míralo; el usuario lo prueba en un móvil real.
