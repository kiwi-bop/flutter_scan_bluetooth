package com.kiwi.flutterscanbluetooth

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.support.v4.app.ActivityCompat
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar

class FlutterScanBluetoothPlugin(private val activity: Activity,
                                 private val channel: MethodChannel) : MethodCallHandler, PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener {

    companion object {
        private val TAG = FlutterScanBluetoothPlugin::class.java.name!!
        private const val REQUEST_BLUETOOTH = 7338
        private const val REQUEST_PERMISSION = 242346
        private const val ACTION_NEW_DEVICE = "action_new_device"
        private const val ACTION_START_SCAN = "action_start_scan"
        private const val ACTION_STOP_SCAN = "action_stop_scan"
        private const val ACTION_SCAN_STOPPED = "action_scan_stopped"

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_scan_bluetooth")
            val plugin = FlutterScanBluetoothPlugin(registrar.activity(), channel)
            registrar.addActivityResultListener(plugin)
            registrar.addRequestPermissionsResultListener(plugin)
            registrar.addViewDestroyListener {
                plugin.onViewDestroy()
                false
            }
            channel.setMethodCallHandler(plugin)
        }
    }

    private var adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var pendingScanResult: Result? = null
    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                channel.invokeMethod(ACTION_NEW_DEVICE, toMap(device))
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == intent.action) {
                channel.invokeMethod(ACTION_SCAN_STOPPED, null)
            }
        }
    }

    fun onViewDestroy() {
        if (adapter!!.isDiscovering) {
            stopScan(null)
        }
    }

    private fun toMap(device: BluetoothDevice): Map<String, String> {
        val map = HashMap<String, String>()
        map["name"] = device.name ?: device.address
        map["address"] = device.address
        return map
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        return if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                scan(pendingScanResult!!)
            } else {
                pendingScanResult!!.error("error_no_permission", "Permission must be granted", null)
                pendingScanResult = null
            }
            true
        } else
            false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            REQUEST_BLUETOOTH -> {
                if (requestCode == Activity.RESULT_OK) {
                    scan(pendingScanResult!!)
                } else {
                    pendingScanResult!!.error("error_bluetooth_disabled", "Bluetooth is disabled", null)
                    pendingScanResult = null
                }
                true
            }
            else -> {
                false
            }
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        Log.e(TAG, call.toString())
        if (adapter == null) {
            result.error("error_no_bt", "Bluetooth adapter is null, BT is not supported on this device", null)
            return
        }

        when (call.method) {
            ACTION_START_SCAN -> scan(result, call.arguments as Boolean)
            ACTION_STOP_SCAN -> stopScan(result)
            else -> result.notImplemented()
        }
    }

    private fun stopScan(result: Result?) {
        adapter?.cancelDiscovery()
        try {
            channel.invokeMethod(ACTION_SCAN_STOPPED, null)
            activity.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("Cannot stop Bluetooth scan before starting.")
        }
        result?.success(null)
    }

    private fun scan(result: Result, returnBondedDevices: Boolean = false) {
        if (adapter!!.isEnabled) {
            if (activity.checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                if (adapter!!.isDiscovering) {
                    // Bluetooth is already in modo discovery mode, we cancel to restart it again
                    stopScan(null)
                }
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                activity.registerReceiver(receiver, filter)

                adapter!!.startDiscovery()
                var bondedDevices: List<Map<String, String>> = arrayListOf()
                if (returnBondedDevices) {
                    bondedDevices = adapter!!.bondedDevices.mapNotNull {
                        toMap(it)
                    }
                }
                result.success(bondedDevices)
                pendingScanResult = null
            } else {
                pendingScanResult = result
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_PERMISSION)
            }
        } else {
            val enableBT = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            pendingScanResult = result
            activity.startActivityForResult(enableBT, REQUEST_BLUETOOTH)
        }
    }
}
