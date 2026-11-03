# Balsa Roja POS para Android

Aplicación Android complementaria del sistema de escritorio Balsa Roja POS. Permite al responsable de la tienda recibir y responder solicitudes de comprobación de pagos virtuales enviadas desde caja. La aplicación contempla Yape, Net Empresa, CPF y Bradesco, muestra solicitudes e historial y puede detectar avisos de Yape cuando se le concede acceso a las notificaciones.

La respuesta del teléfono sirve de apoyo para la revisión del cajero. La venta se confirma en el POS; la aplicación Android no registra ni confirma ventas por sí sola. El aviso de una notificación tampoco sustituye la comprobación del pago en el servicio correspondiente.

## Uso

1. Instala la aplicación en un teléfono con Android 6.0 o posterior y con acceso a Internet.
2. En el POS de escritorio, abre **Ajustes > POS** y utiliza la opción para vincular un teléfono.
3. Abre Balsa Roja POS en el teléfono, pulsa **Iniciar** e introduce el código que muestra el POS. Comprueba que ambos dispositivos queden vinculados.
4. Para detectar avisos de Yape, concede a Balsa Roja POS el acceso a notificaciones solicitado por Android. En versiones recientes, permite también las notificaciones de la aplicación cuando el sistema lo solicite.
5. Cuando caja envíe una solicitud, revisa el medio, el importe y el código en el teléfono y registra la respuesta que corresponda. El cajero revisa la respuesta y completa la venta en el POS.
6. La aplicación permite consultar las solicitudes por medio e historial, y desvincular el teléfono. Si se desvincula desde el teléfono, revisa también el estado de la vinculación en los ajustes del POS.

La recepción de avisos depende de la conexión, de los permisos del dispositivo, de la configuración de Firebase y de las restricciones de batería del teléfono. Comprueba el flujo con los servicios y dispositivos reales antes de utilizarlo en la tienda.

## Tecnología y requisitos de desarrollo

- Android nativo en Java, con Gradle Wrapper 9.6.0 y Android Gradle Plugin 9.0.0.
- JDK 17 y Android Studio con el SDK de Android requerido por el proyecto: `compileSdk` y `targetSdk` 37; `minSdk` 23.
- Firebase Authentication con acceso anónimo y Firebase Realtime Database, además del plugin Google Services. El POS y la aplicación deben apuntar al mismo proyecto de Firebase.
- Android Studio y Gradle necesitan acceso a Google Maven, Maven Central y Gradle Plugin Portal para descargar las dependencias.

La versión propia de la aplicación Android se encuentra en `app/build.gradle.kts`. Puede tener una numeración distinta de la versión del sistema de escritorio.

## Configuración del entorno

1. Clona este repositorio y ábrelo desde Android Studio con **Open**. Permite que Gradle sincronice el proyecto e instala los componentes del SDK que solicite el IDE.
2. Registra una aplicación Android con el identificador `com.balsaroja.yapecompanion` en el proyecto de Firebase correspondiente. Descarga su archivo `google-services.json` y colócalo en `app/google-services.json`.
3. Activa el proveedor **Anónimo** en Firebase Authentication y configura Realtime Database. Publica las reglas del sistema de escritorio que correspondan al mismo entorno y verifica sus permisos antes de operar con datos reales.
4. Configura el POS de escritorio con las variables `VITE_FIREBASE_*` de su `.env.example`, incluida la URL de Realtime Database, y vuelve a compilar su interfaz cuando cambien esas variables.
5. Conecta un dispositivo Android o inicia un emulador compatible y ejecuta la configuración `app` desde Android Studio. La detección de notificaciones de Yape debe comprobarse en un teléfono que pueda recibirlas.

`local.properties`, `app/google-services.json`, las claves de firma y los archivos generados son específicos del entorno y no se incluyen en el repositorio. No publiques contraseñas ni archivos de firma.

## Compilación y distribución

Desde la raíz del proyecto Android, en Windows:

```powershell
.\gradlew.bat assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/`. Para Linux o macOS, usa `./gradlew assembleDebug`. También se puede ejecutar desde Android Studio mediante **Run**.

Para preparar un APK de distribución, copia `keystore.properties.example` como `keystore.properties` y completa la ruta y credenciales de la llave de firma en tu equipo. Conserva la llave original: Android exige la misma identidad de firma para instalar una actualización sobre una versión anterior.

```powershell
.\gradlew.bat assembleRelease
```

El resultado se encuentra en `app/build/outputs/apk/release/`. Comprueba que el APK esté firmado con la llave prevista antes de distribuirlo. El proyecto habilita esquemas V1, V2, V3 y V4 cuando se configura la firma; V1 permite compatibilidad con Android 6. Para distribución manual, utiliza un APK universal compatible con los dispositivos de destino.

## Estructura

- `app/src/main/java/com/balsaroja/yapecompanion/`: pantallas, vinculación, solicitudes y detección de notificaciones.
- `app/src/main/AndroidManifest.xml`: actividades, servicios y permisos de Android.
- `app/src/main/res/`: recursos visuales y textos.
- `app/build.gradle.kts`: versión, SDK, dependencias y firma de la aplicación.
- `gradle/wrapper/`: Gradle Wrapper del proyecto.

El código del sistema de escritorio se mantiene en su repositorio independiente; no se incluye en este repositorio Android.
