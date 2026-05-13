/*
 * BioAsistencia - ESP32 + Sensor AS608/Biovo C3 + LCD I2C 16x2
 * Proyecto universitario
 *
 * MODO AUTOMATICO: el ESP32 ya no necesita MODO_REGISTRO = true.
 * Cuando desde la app se registra un catedratico nuevo y se toca
 * "Registrar huella", la app avisa a la API y el ESP32 detecta
 * la solicitud automaticamente y entra en modo registro solo.
 *
 * Librerias necesarias:
 *   - Adafruit Fingerprint Sensor Library
 *   - ArduinoJson (v6)
 *   - LiquidCrystal I2C (by Frank de Brabander)
 */

#include <Adafruit_Fingerprint.h>
#include <ArduinoJson.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <LiquidCrystal_I2C.h>

// CONFIGURACION WIFI

const char* WIFI_SSID     = "S23+";
const char* WIFI_PASSWORD = "santhiagomun017";

// Direccion IPv4
const char* IP_SERVIDOR  = "10.92.149.161";
const int   PUERTO       = 8000;

const char* SENSOR_TOKEN = "tok_sensor_bioasistencia_2026_secret_abc123xyz";
const int   SENSOR_ID    = 1;

// Cada cuantos milisegundos consulta si hay solicitudes pendientes de la app
const long  INTERVALO_POLLING = 3000;

// HARDWARE

HardwareSerial mySerial(2);
Adafruit_Fingerprint finger = Adafruit_Fingerprint(&mySerial);

// LCD I2C
LiquidCrystal_I2C lcd(0x27, 16, 2);

// VARIABLES INTERNAS

int  ultimoSlot    = -1;
long ultimoTiempo  = 0;
long tiempoPolling = 0;

// SETUP

void setup() {
  Serial.begin(115200);
  while (!Serial);
  delay(500);

  lcd.init();
  lcd.backlight();
  lcdMsg("BioAsistencia", "Iniciando...");
  Serial.println("\n--- BioAsistencia ---");

  // Sensor
  mySerial.begin(57600, SERIAL_8N1, 16, 17);
  finger.begin(57600);

  if (finger.verifyPassword()) {
    Serial.println("Sensor AS608 OK");
    lcdMsg("Sensor OK", "Conectando WiFi");
  } else {
    Serial.println("ERROR: Sensor no encontrado");
    lcdMsg("ERROR sensor", "Revisa cables");
    while (1) delay(1);
  }

  // WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 20) {
    delay(500); Serial.print("."); intentos++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi OK: " + WiFi.localIP().toString());
    lcdMsg("WiFi OK", WiFi.localIP().toString().c_str());
    delay(1500);
  } else {
    Serial.println("\nERROR WiFi");
    lcdMsg("ERROR WiFi", "Revisa config");
    while (1) delay(1);
  }

  lcdMsg("Listo!", "Coloca el dedo");
  Serial.println("Listo para registrar asistencias.");
  Serial.println("Esperando huellas o solicitudes de la app...");
}

// LOOP PRINCIPAL

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    WiFi.reconnect(); delay(2000); return;
  }

  // Cada INTERVALO_POLLING ms consulta si la app quiere registrar una huella
  long ahora = millis();
  if (ahora - tiempoPolling >= INTERVALO_POLLING) {
    tiempoPolling = ahora;
    verificarSolicitudRegistro();
  }

  // Modo normal: detectar huellas para asistencia
  int slot = buscarHuella();
  if (slot <= 0) return;

  // Anti-duplicado: mismo dedo en menos de 10 segundos
  if (slot == ultimoSlot && (millis() - ultimoTiempo) < 10000) {
    Serial.println("Duplicado ignorado"); return;
  }
  ultimoSlot   = slot;
  ultimoTiempo = millis();

  Serial.print("Huella reconocida! Slot: "); Serial.print(slot);
  Serial.print("  Confianza: "); Serial.println(finger.confidence);

  lcdMsg("Procesando...", "Espera");

  if (enviarAsistencia(slot)) {
    Serial.println("Asistencia registrada OK");
    delay(2500);
  } else {
    Serial.println("Error al registrar");
    lcdMsg("Error", "Intenta de nuevo");
    delay(2000);
  }

  lcdMsg("Listo!", "Coloca el dedo");
}

// VERIFICAR SI LA APP SOLICITO REGISTRAR UNA HUELLA

void verificarSolicitudRegistro() {
  HTTPClient http;
  String url = "http://" + String(IP_SERVIDOR) + ":" + String(PUERTO) +
               "/api/v1/sensor/pendiente-registro?id_sensor=" + String(SENSOR_ID);

  http.begin(url);
  http.addHeader("X-Sensor-Token", SENSOR_TOKEN);
  int codigo = http.GET();

  if (codigo != 200) { http.end(); return; }

  String respuesta = http.getString();
  http.end();

  StaticJsonDocument<512> doc;
  if (deserializeJson(doc, respuesta) != DeserializationError::Ok) return;

  // Si data es null, no hay solicitud pendiente
  if (doc["data"].isNull()) return;

  int idEvento      = doc["data"]["id_evento"]      | 0;
  int slot          = doc["data"]["sensor_slot"]    | 0;
  int idCatedratico = doc["data"]["id_catedratico"] | 0;

  if (idEvento == 0 || slot == 0 || idCatedratico == 0) return;

  Serial.println("Solicitud de registro recibida desde la app");
  Serial.print("Catedratico ID: "); Serial.print(idCatedratico);
  Serial.print("  Slot: "); Serial.println(slot);

  registrarHuella(idEvento, slot, idCatedratico);
}

// ================================================================
// REGISTRAR HUELLA (disparado desde la app)
// ================================================================

void registrarHuella(int idEvento, int slot, int idCatedratico) {
  int p = -1;

  // Primera captura
  lcdMsg("PASO 1:", "Coloca el dedo");
  Serial.println("PASO 1: Catedratico coloca el dedo...");

  while (p != FINGERPRINT_OK) {
    p = finger.getImage();
    if      (p == FINGERPRINT_OK)       Serial.println("Imagen 1 OK");
    else if (p == FINGERPRINT_NOFINGER) delay(50);
    else { Serial.println("Error imagen"); }
  }

  if (finger.image2Tz(1) != FINGERPRINT_OK) {
    Serial.println("Error procesando imagen 1");
    lcdMsg("Error", "Intenta de nuevo");
    delay(2000); lcdMsg("Listo!", "Coloca el dedo");
    return;
  }

  // Retirar dedo
  lcdMsg("OK!", "Retira el dedo");
  Serial.println("Retira el dedo...");
  delay(1500);
  while (finger.getImage() != FINGERPRINT_NOFINGER) delay(50);

  // Segunda captura
  p = -1;
  lcdMsg("PASO 2:", "Mismo dedo");
  Serial.println("PASO 2: Coloca el mismo dedo de nuevo...");

  while (p != FINGERPRINT_OK) {
    p = finger.getImage();
    if      (p == FINGERPRINT_OK)       Serial.println("Imagen 2 OK");
    else if (p == FINGERPRINT_NOFINGER) delay(50);
  }

  if (finger.image2Tz(2) != FINGERPRINT_OK) {
    Serial.println("Error procesando imagen 2");
    lcdMsg("Error", "Intenta de nuevo");
    delay(2000); lcdMsg("Listo!", "Coloca el dedo");
    return;
  }

  // Crear modelo
  lcdMsg("Procesando...", "Espera");
  if (finger.createModel() != FINGERPRINT_OK) {
    Serial.println("Las huellas no coinciden");
    lcdMsg("No coinciden", "Intenta de nuevo");
    delay(2000); lcdMsg("Listo!", "Coloca el dedo");
    return;
  }

  // Guardar en el sensor
  if (finger.storeModel(slot) != FINGERPRINT_OK) {
    Serial.println("Error guardando en sensor");
    lcdMsg("Error sensor", "Intenta de nuevo");
    delay(2000); lcdMsg("Listo!", "Coloca el dedo");
    return;
  }

  Serial.println("Huella guardada en sensor OK");
  lcdMsg("Guardado OK", "Confirmando...");

  confirmarRegistro(idEvento, slot, idCatedratico);
}

// ================================================================
// CONFIRMAR REGISTRO A LA API
// ================================================================

void confirmarRegistro(int idEvento, int slot, int idCatedratico) {
  HTTPClient http;
  String url = "http://" + String(IP_SERVIDOR) + ":" + String(PUERTO) + "/api/v1/sensor/confirmar-registro";

  http.begin(url);
  http.addHeader("Content-Type",   "application/json");
  http.addHeader("X-Sensor-Token", SENSOR_TOKEN);

  String cuerpo = "{\"id_evento\":"       + String(idEvento) +
                  ",\"id_sensor\":"       + String(SENSOR_ID) +
                  ",\"sensor_slot\":"     + String(slot) +
                  ",\"id_catedratico\":" + String(idCatedratico) + "}";

  int codigo = http.POST(cuerpo);
  String resp = http.getString();
  http.end();

  Serial.print("Confirmacion API: "); Serial.println(codigo);

  if (codigo == 200 || codigo == 201) {
    StaticJsonDocument<512> doc;
    if (!deserializeJson(doc, resp)) {
      String nombre   = doc["catedratico"]["nombre"]   | "Catedratico";
      String apellido = doc["catedratico"]["apellido"] | "";
      String linea    = nombre + " " + apellido;
      if (linea.length() > 16) linea = linea.substring(0, 16);
      lcdMsg(linea.c_str(), "Huella lista!");
      Serial.println("Registro completo: " + linea);
    }
    delay(3000);
  } else {
    Serial.println("Error confirmando en API");
    lcdMsg("Error en BD", "Revisa Docker");
    delay(2000);
  }

  lcdMsg("Listo!", "Coloca el dedo");
}

// ================================================================
// ENVIAR ASISTENCIA
// ================================================================

bool enviarAsistencia(int slot) {
  if (WiFi.status() != WL_CONNECTED) return false;

  HTTPClient http;
  String url = "http://" + String(IP_SERVIDOR) + ":" + String(PUERTO) + "/api/v1/sensor/dato";

  http.begin(url);
  http.addHeader("Content-Type",   "application/json");
  http.addHeader("X-Sensor-Token", SENSOR_TOKEN);

  String cuerpo = "{\"id_sensor\":"   + String(SENSOR_ID) +
                  ",\"sensor_slot\":" + String(slot) +
                  ",\"timestamp\":"   + String(millis() / 1000) + "}";

  int codigo = http.POST(cuerpo);
  String resp = http.getString();
  http.end();

  Serial.print("Asistencia API: "); Serial.println(codigo);

  if (codigo == 200 || codigo == 201) {
    StaticJsonDocument<512> doc;
    if (!deserializeJson(doc, resp)) {
      String nombre   = doc["catedratico"]["nombre"]   | "Catedratico";
      String apellido = doc["catedratico"]["apellido"] | "";
      String linea    = nombre + " " + apellido;
      if (linea.length() > 16) linea = linea.substring(0, 16);
      lcdMsg(linea.c_str(), "Presente OK");
    }
    return true;
  }
  return false;
}

// ================================================================
// BUSCAR HUELLA
// ================================================================

int buscarHuella() {
  if (finger.getImage()     != FINGERPRINT_OK) return -1;
  if (finger.image2Tz()     != FINGERPRINT_OK) return -1;

  // Guardar resultado para no llamar fingerSearch() dos veces
  uint8_t resultado = finger.fingerSearch();
  if (resultado == FINGERPRINT_OK) return finger.fingerID;

  if (resultado == FINGERPRINT_NOTFOUND) {
    Serial.println("Huella no reconocida");
    lcdMsg("No reconocida", "Intenta de nuevo");
    delay(1500);
    lcdMsg("Listo!", "Coloca el dedo");
  }
  return -1;
}

// ================================================================
// LCD HELPER
// ================================================================

void lcdMsg(const char* l1, const char* l2) {
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print(l1);
  lcd.setCursor(0, 1); lcd.print(l2);
}

void lcdMsg(String l1, String l2) { lcdMsg(l1.c_str(), l2.c_str()); }
