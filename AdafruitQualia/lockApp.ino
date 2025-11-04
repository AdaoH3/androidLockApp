#include <Arduino_GFX_Library.h>
#include <Adafruit_CST8XX.h>
#include <Adafruit_FT6206.h>

// Bluetooth libraries
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>

// UUIDs
#define SERVICE_UUID        "87abbb16-3e77-415f-9917-38a321e8997b"
#define CHARACTERISTIC_UUID "912177b2-f40e-45f1-91cb-4163cb64c2e8"

// Button Variables/Function
#define BUTTON_PIN A1
volatile bool buttonState = false;
unsigned long lastPressed = 0;
bool lockToggle = false;

void buttonISR() {
  // Simple debounce logic using millis()
  if (millis() - lastPressed > 200) {
    buttonState = !buttonState;  // Toggle state on button press
    Serial.println("Button Pressed");
    lastPressed = millis();
  }
}

// Define Server and Characteristic pointers
BLEServer *pServer = nullptr;
BLECharacteristic *pCharacteristic = nullptr;
bool deviceConnected = false;

// Display setup
Arduino_XCA9554SWSPI *expander = new Arduino_XCA9554SWSPI(
    PCA_TFT_RESET, PCA_TFT_CS, PCA_TFT_SCK, PCA_TFT_MOSI,
    &Wire, 0x3F);

Arduino_ESP32RGBPanel *rgbpanel = new Arduino_ESP32RGBPanel(
    TFT_DE, TFT_VSYNC, TFT_HSYNC, TFT_PCLK,
    TFT_R1, TFT_R2, TFT_R3, TFT_R4, TFT_R5,
    TFT_G0, TFT_G1, TFT_G2, TFT_G3, TFT_G4, TFT_G5,
    TFT_B1, TFT_B2, TFT_B3, TFT_B4, TFT_B5,
    1 /* hsync_polarity */, 50, 2, 44,
    1 /* vsync_polarity */, 16, 2, 18
);

Arduino_RGB_Display *gfx = new Arduino_RGB_Display(
    480, 480, rgbpanel, 0, true,
    expander, GFX_NOT_DEFINED, TL021WVC02_init_operations, sizeof(TL021WVC02_init_operations));

// Server Callback
class MyServerCallbacks : public BLEServerCallbacks {
  Arduino_RGB_Display *gfx;

public:
  MyServerCallbacks(Arduino_RGB_Display *gfxDisplay) {
    gfx = gfxDisplay;
  }

  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("BLE Client Connected");
    if (gfx != nullptr) {
      gfx->fillScreen(WHITE); // Turn screen white when a client connects
    }
  }

  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("BLE Client Disconnected");

    if (gfx != nullptr) {
      gfx->fillScreen(YELLOW);  // Return to idle yellow when disconnected
    }

    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->start();
  }
};

void setup() {
  delay(2000);  // Give USB time to initialize
  Serial.begin(9600);
  Serial.println("Serial started");

  expander->pinMode(PCA_TFT_BACKLIGHT, OUTPUT);
  expander->digitalWrite(PCA_TFT_BACKLIGHT, HIGH);

  gfx->begin();
  gfx->fillScreen(YELLOW);
  Serial.println("Display initialized");

  // BLE setup
  BLEDevice::init("QualiaBLE");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks(gfx));

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );

  pCharacteristic->setValue("idle");
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->start();

  Serial.println("BLE server is ready and advertising");


  // Button Pin
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(BUTTON_PIN), buttonISR, FALLING);  // FALLING for button press
}

void loop() {
  static bool lastButtonState = false;

  // Update screen and button state based on buttonState changes
  if (buttonState != lastButtonState) {
    lastButtonState = buttonState;
    if (buttonState) {
      gfx->fillScreen(RED);
      Serial.println("Locking Screen");

      if (deviceConnected) {
        pCharacteristic->setValue("up");
        pCharacteristic->notify();
        lockToggle = true;
      }
    } else {
      gfx->fillScreen(GREEN);  // Unlocking screen
      Serial.println("Unlocking Screen");

      if (deviceConnected) {
        pCharacteristic->setValue("down");
        pCharacteristic->notify();
        lockToggle = false;
      }
    }
    delay(200);  // Short delay to avoid multiple triggers (debouncing)
  }

  // Additional processing or display updates can go here
}
