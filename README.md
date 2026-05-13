# BioAsistencia - Sistema de Asistencia de Catedraticos

Sistema de control de asistencia biometrica para catedraticos universitarios.
Sensor Arduino AS608 + App Android (MVVM) + API REST + MySQL.

## Arquitectura

```
App Android (MVVM + LiveData + Coroutines + Retrofit2)
    |
    | JWT Bearer Token
    v
API REST (puerto 8000)
    |
    v
MySQL 8.0 (Docker, puerto 3306)

Arduino AS608 --> POST /api/v1/sensor/dato (token de dispositivo)
App Android   --> GET  /api/v1/sensor/ultimo-evento (polling cada 2 seg)
```

## Requisitos

- Android Studio Hedgehog (2023.1.1) o superior
- Android SDK 34 / minSdk 26
- Docker Desktop
- Arduino IDE con soporte ESP8266/ESP32

## Configuracion rapida

### 1. Base de datos (Docker)

```bash
cd docker/
docker-compose up -d
```

- MySQL: localhost:3306  (biouser / bio1234)
- phpMyAdmin: http://localhost:8080
- API: http://localhost:8000

### 2. App Android

Abrir en Android Studio:
- File > Open > seleccionar la carpeta BioAsistencia2

Cambiar la URL del servidor segun tu entorno en app/build.gradle:

```gradle
// Emulador Android Studio
buildConfigField "String", "BASE_URL", '"http://10.0.2.2:8000/api/v1/"'

// Dispositivo fisico (misma red WiFi)
buildConfigField "String", "BASE_URL", '"http://192.168.1.XXX:8000/api/v1/"'

// Dispositivo via USB (adb reverse tcp:8000 tcp:8000)
buildConfigField "String", "BASE_URL", '"http://localhost:8000/api/v1/"'
```

### 3. Arduino

Editar arduino/bioasistencia_sensor.ino:
- WIFI_SSID y WIFI_PASSWORD
- SERVER_URL (IP del servidor)
- SENSOR_TOKEN (debe coincidir con el de la BD)

## Usuarios por defecto

| Correo                         | Contrasena | Rol          |
|--------------------------------|------------|--------------|
| admin@bioasistencia.edu        | admin1234  | admin        |
| coordinador@bioasistencia.edu  | admin1234  | coordinador  |

## Estructura del proyecto

```
BioAsistencia2/
- app/src/main/
  - java/com/bioasistencia/
    - data/
      - api/       ApiService, RetrofitClient
      - models/    Catedratico, Asistencia, Departamento, ...
      - repository/ AppRepository (sealed Result<T>)
    - ui/
      - login/     LoginActivity + ViewModel
      - dashboard/ DashboardFragment + Adapter
      - biometric/ BiometricFragment (polling Arduino)
      - historial/ HistorialFragment + Adapter
      - reportes/  ReportesFragment (MPAndroidChart)
      - perfil/    PerfilFragment
      - menu/      MenuFragment
    - utils/       SessionManager, Extensions, DateUtils
  - res/
    - layout/      activity_*.xml, fragment_*.xml, item_*.xml
    - navigation/  nav_graph.xml (SafeArgs)
    - drawable/    badges, shapes, selectors
    - values/      colors, strings, themes, dimens
- docker/
  - docker-compose.yml
  - init.sql         (schema completo + datos de prueba)
- arduino/
  - bioasistencia_sensor.ino (ESP8266/ESP32 + AS608)
```
