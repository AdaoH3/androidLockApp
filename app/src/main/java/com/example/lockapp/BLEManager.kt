package com.example.lockapp

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

class BleManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }
    private val bluetoothLeScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private var scanning = false
    private val scanResults = mutableListOf<ScanResult>()
    private val handler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
    }
    private var connectionListener: ConnectionListener? = null

    fun setScanResultListener(listener: (ScanResult) -> Unit) {
        scanResultListener = listener
    }

    private var scanResultListener: ((ScanResult) -> Unit)? = null

    fun setConnectionListener(listener: ConnectionListener) {
        connectionListener = listener
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (!scanResults.any { it.device.address == result.device.address }) {
                scanResults.add(result)
                scanResultListener?.invoke(result)
                Log.d("BleManager", "Scan result: ${result.device.name} - ${result.device.address}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BleManager", "Scan failed with error: $errorCode")
        }
    }

    fun startScan() {
        if (scanning) return
        bluetoothLeScanner?.startScan(scanCallback)
        scanning = true
        Log.d("BleManager", "Started BLE scan")
    }

    fun stopScan() {
        if (!scanning) return
        bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
        Log.d("BleManager", "Stopped BLE scan")
    }

    fun connectToDevice(device: BluetoothDevice) {
        Log.d("BleManager", "Connecting to device: ${device.name}")
        stopScan()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnectCurrentDevice() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        scanResults.clear() // <-- Add this line
    }


    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("BleManager", "Connected to GATT server")
                    connectionListener?.onConnected()
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BleManager", "Disconnected from GATT server")
                    connectionListener?.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleManager", "Services discovered")
                // Subscribe to notifications if needed
                gatt?.services?.forEach { service ->
                    service.characteristics.forEach { char ->
                        if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            gatt.setCharacteristicNotification(char, true)
                            val descriptor = char.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))
                            descriptor?.let {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(it)
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { data ->
                val dataStr = data.toString(Charsets.UTF_8)
                Log.d("BleManager", "Characteristic changed: $dataStr")
                onDataReceived(dataStr)
            }
        }
    }

    companion object {
        private const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
    }
}
