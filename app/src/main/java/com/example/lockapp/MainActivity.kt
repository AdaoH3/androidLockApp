package com.example.lockapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lockapp.ui.theme.LockAppTheme

class MainActivity : ComponentActivity() {
    private lateinit var bleManager: BleManager

    // Compose states
    private val qualiaDevices = mutableStateListOf<BluetoothDevice>()
    private var selectedDevice by mutableStateOf<BluetoothDevice?>(null)
    private var isLocked by mutableStateOf(false)
    private var isConnected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LockAppTheme {
                val context = LocalContext.current
                val activity = context as Activity

                Scaffold { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                        if (!isConnected) {
                            // Show connection UI
                            ConnectScreen(
                                devices = qualiaDevices,
                                selectedDevice = selectedDevice,
                                onDeviceSelected = { device ->
                                    Log.d("MainActivity", "Connecting to ${device.name}")
                                    selectedDevice = device
                                    bleManager.connectToDevice(device)
                                }
                            )
                        } else {
                            // Show lockdown UI
                            LockdownScreen(
                                isLocked = isLocked,
                                onToggleLock = {
                                    try {
                                        if (!isLocked) activity.startLockTask()
                                        else activity.stopLockTask()
                                        isLocked = !isLocked
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Lock task failed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDisconnect = {
                                    bleManager.disconnectCurrentDevice()
                                    isConnected = false
                                    isLocked = false
                                    selectedDevice = null
                                    qualiaDevices.clear()
                                    bleManager.startScan()
                                }
                            )
                        }
                    }
                }
            }
        }

        requestPermissionsIfNeeded(this) {
            setupBleManagerAndScan(this)
        }
    }

    private fun setupBleManagerAndScan(activity: Activity) {
        bleManager = BleManager(activity, onDataReceived = { data ->
            // Example: react to BLE messages
            Log.d("MainActivity", "Data from BLE device: $data")
            when (data.lowercase()) {
                "up" -> {
                    if (!isLocked) {
                        activity.startLockTask()
                        isLocked = true
                    }
                }
                "down" -> {
                    if (isLocked) {
                        activity.stopLockTask()
                        isLocked = false
                    }
                }
            }
        })

        bleManager.setScanResultListener { result ->
            val device = result.device
            val name = device.name ?: ""
            if (name.startsWith("Qualia") && !qualiaDevices.contains(device)) {
                qualiaDevices.add(device)
                Log.d("MainActivity", "Added Qualia device: $name")
            }
        }

        bleManager.setConnectionListener(object : BleManager.ConnectionListener {
            override fun onConnected() {
                Log.d("MainActivity", "Device connected")
                isConnected = true
            }

            override fun onDisconnected() {
                Log.d("MainActivity", "Device disconnected")
                isConnected = false
                isLocked = false
                selectedDevice = null
                qualiaDevices.clear()
                bleManager.startScan()
            }
        })

        bleManager.startScan()
    }

    private fun requestPermissionsIfNeeded(activity: Activity, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            val notGranted = permissions.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }
            if (notGranted.isNotEmpty()) {
                ActivityCompat.requestPermissions(activity, notGranted.toTypedArray(), 100)
            } else {
                onGranted()
            }
        } else {
            onGranted()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            setupBleManagerAndScan(this)
        } else {
            Toast.makeText(this, "BLE permissions denied", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun ConnectScreen(
    devices: List<BluetoothDevice>,
    selectedDevice: BluetoothDevice?,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Qualia Device to Connect:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(devices) { device ->
                val isSelected = device == selectedDevice
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onDeviceSelected(device) },
                    colors = if (isSelected)
                        CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                    else
                        CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(device.name ?: "Unnamed")
                        Spacer(Modifier.weight(1f))
                        Text(device.address)
                    }
                }
            }
            if (devices.isEmpty()) {
                item {
                    Text("No Qualia devices found. Make sure your device is advertising.", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun LockdownScreen(
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (isLocked) "Phone Locked Down" else "Phone Unlocked", modifier = Modifier.padding(bottom = 32.dp))

        Button(onClick = onToggleLock) {
            Text(if (!isLocked) "Lockdown" else "Exit Lockdown")
        }

        Spacer(Modifier.height(32.dp))

        Button(onClick = onDisconnect, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) {
            Text("Disconnect Device")
        }
    }
}
