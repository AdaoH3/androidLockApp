# LockApp  
Have an external locking tool for my phone that completely hard locks my phone (no exiting the screen like other already made apps)  
Functions off of Android's Pin functionality -> Phone needs to grant Dev Permissions to the app   
App used in conjunction with Adafruit Qualia ESP32 with TFT
----
## Microcontroller Functionality  
Advertises BLE server defining a specific service and characteristic UUID  
#define SERVICE_UUID        "87abbb16-3e77-415f-9917-38a321e8997b"  
#define CHARACTERISTIC_UUID "912177b2-f40e-45f1-91cb-4163cb64c2e8"  
  
On connection (to app) switches TFT screen from yellow to white  
Microcontroller toggles between "up" and "down" state between button presses. "up" is to lock, "down" to unlock.  
Uses notify to send the characteristic value to connected centrals.  
----
## App Functionality  
Startup:
1. App launches MainActivity (declared in the manifest).  
2. MainActivity.onCreate requests runtime BLE/location permissions (for Android S+ it requests BLUETOOTH_SCAN, BLUETOOTH_CONNECT and ACCESS_FINE_LOCATION).
Once permissions are granted, setupBleManagerAndScan is called.  
3. BleManager.startScan() starts BLE LE scanning via BluetoothLeScanner and reports results via a ScanCallback.    
4. MainActivity registers a scan-result listener: it adds devices whose name starts with "Qualia" to qualiaDevices.  
When the user selects a device in the Connect UI, bleManager.connectToDevice(device) calls device.connectGatt(...) and stops scanning.  
5. GATT flow (in BleManager): onConnectionStateChange: notifies ConnectionListener.onConnected() / onDisconnected().  
6. App behavior when data arrives (MainActivity)  
When data comes in, it logs and uses simple text commands:  
"up" → call activity.startLockTask() and set isLocked = true.  
"down" → call activity.stopLockTask() and set isLocked = false.  
  
UI is done through Jetpack Compose:
ConnectScreen: lists discovered Qualia devices (click to connect).  
LockdownScreen: shows lock state, a button to toggle lock (also uses lockTask), and a disconnect button which disconnects and restarts scanning.  
Key manifest & permission notes  
Manifest includes BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_SCAN, BLUETOOTH_CONNECT and location permissions.  
Lock task (kiosk) behavior: startLockTask() only fully pins/locks the device if the app is device owner or whitelisted by device policy; otherwise the behavior may be limited. The app attempts to use startLockTask/stopLockTask. 
