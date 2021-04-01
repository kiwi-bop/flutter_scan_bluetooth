package com.kiwi.flutterscanbluetooth

import android.Manifest
import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_ADMIN
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class FlutterScanBluetoothPlugin
    : FlutterPlugin, ActivityAware, MethodCallHandler, PluginRegistry.ActivityResultListener,
        PluginRegistry.RequestPermissionsResultListener {

    companion object {
        private val TAG = FlutterScanBluetoothPlugin::class.java.name
        private const val REQUEST_BLUETOOTH = 7338
        private const val REQUEST_PERMISSION = 242346
        private const val ACTION_NEW_DEVICE = "action_new_device"
        private const val ACTION_START_SCAN = "action_start_scan"
        private const val ACTION_STOP_SCAN = "action_stop_scan"
        private const val ACTION_SCAN_STOPPED = "action_scan_stopped"
    }

    private lateinit var activityBinding: ActivityPluginBinding
    private lateinit var channel: MethodChannel

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

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(binding.binaryMessenger, "flutter_scan_bluetooth")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromActivity() {
        activityBinding.removeRequestPermissionsResultListener(this)
        onViewDestroy()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            (activityBinding.activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        } else {
            BluetoothAdapter.getDefaultAdapter()
        }
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding.removeRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private var adapter: BluetoothAdapter? = null

    fun onViewDestroy() {
        if (adapter!!.isDiscovering) {
            stopScan(null)
        }
    }

    private fun toMap(device: BluetoothDevice): Map<String, String> {
        val map = HashMap<String, String>()
        var name = device.name ?: device.address

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && !name.contains("-LE")) {
            name += if(device.type == DEVICE_TYPE_LE) "-LE" else ""
        }

        map["name"] = name
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
                if (resultCode == Activity.RESULT_OK) {
                    scan(pendingScanResult!!)
                } else {
                    pendingScanResult!!.error("error_bluetooth_disabled", "Bluetooth is disabled", null)
                    pendingScanResult = null
                }
                true
            }
            GpsUtils.GPS_REQUEST-> {
                if (GpsUtils(activityBinding.activity).isGpsEnabled) {
                    scan(pendingScanResult!!)
                } else {
                    pendingScanResult!!.error("error_no_gps", "Gps need to be turned on to scan BT devices", null)
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
        channel.invokeMethod(ACTION_SCAN_STOPPED, null)
        try {
            activityBinding.activity.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("Cannot stop Bluetooth scan before starting.")
        }
        result?.success(null)
    }

    private fun scan(result: Result, returnBondedDevices: Boolean = false) {
        if (adapter!!.isEnabled) {
            if (activityBinding.activity.checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PERMISSION_GRANTED && activityBinding.activity.checkCallingOrSelfPermission(BLUETOOTH_ADMIN)
                    == PERMISSION_GRANTED&& activityBinding.activity.checkCallingOrSelfPermission(BLUETOOTH)
                    == PERMISSION_GRANTED) {

                GpsUtils(activityBinding.activity).turnGPSOn {
                    if (it) {
                        if (adapter!!.isDiscovering) {
                            // Bluetooth is already in modo discovery mode, we cancel to restart it again
                            stopScan(null)
                        }
                        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                        activityBinding.activity.registerReceiver(receiver, filter)

                        adapter!!.startDiscovery()
                        var bondedDevices: List<Map<String, String>> = arrayListOf()
                        if (returnBondedDevices) {
                            bondedDevices = adapter!!.bondedDevices.mapNotNull {
                                toMap(it)
                            }
                        }
                        result.success(bondedDevices)
                    } else {
                        result.error("error_no_gps", "Gps need to be turned on to scan BT devices", null)
                    }
                    pendingScanResult = null
                }

                pendingScanResult = result
            } else {
                pendingScanResult = result
                ActivityCompat.requestPermissions(activityBinding.activity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, BLUETOOTH, BLUETOOTH_ADMIN), REQUEST_PERMISSION)
            }
        } else {
            val enableBT = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            pendingScanResult = result
            activityBinding.activity.startActivityForResult(enableBT, REQUEST_BLUETOOTH)
        }
    }
}
