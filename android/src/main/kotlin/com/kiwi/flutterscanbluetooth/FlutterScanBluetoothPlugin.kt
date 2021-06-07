package com.kiwi.flutterscanbluetooth

import android.Manifest
import android.Manifest.permission.*
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
        private const val ACTION_REQUEST_PERMISSIONS = "action_request_permissions"
    }

    private lateinit var activityBinding: ActivityPluginBinding
    private lateinit var channel: MethodChannel

    private var adapter: BluetoothAdapter? = null

    private var onPermissionGranted: (() -> Unit)? = null
    private var onPermissionRefused: ((code: String, message: String) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    channel.invokeMethod(ACTION_NEW_DEVICE, toMap(device))
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == intent.action) {
                channel.invokeMethod(ACTION_SCAN_STOPPED, null)
            }
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "flutter_scan_bluetooth")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromActivity() {
        activityBinding.removeRequestPermissionsResultListener(this)
        activityBinding.removeActivityResultListener(this)
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
        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding.removeRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun onViewDestroy() {
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
                startPermissionValidation(onPermissionGranted!!, onPermissionRefused!!)

            } else {
                onPermissionRefused!!("error_no_permission",  "Permission must be granted")
            }
            true
        } else
            false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            REQUEST_BLUETOOTH -> {
                if (resultCode == Activity.RESULT_OK) {
                    startPermissionValidation(onPermissionGranted!!, onPermissionRefused!!)

                } else {
                    onPermissionRefused!!("error_bluetooth_disabled", "Bluetooth is disabled")
                }
                true
            }
            GpsUtils.GPS_REQUEST-> {
                if (GpsUtils(activityBinding.activity).isGpsEnabled) {
                    startPermissionValidation(onPermissionGranted!!, onPermissionRefused!!)

                } else {
                    onPermissionRefused!!("error_no_gps", "Gps need to be turned on to scan BT devices")
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
            ACTION_REQUEST_PERMISSIONS -> validatePermissions(result)
            else -> result.notImplemented()
        }
    }

    private fun validatePermissions(result: Result) {
        startPermissionValidation({
            result.success(null)

        }, {code: String, message: String ->
            result.error(code, message, null)
            onPermissionGranted = null
            onPermissionRefused = null
        })
    }

    private fun startPermissionValidation(onGranted: (() -> Unit), onRefused: ((code: String, message: String) -> Unit)) {

        if (adapter!!.isEnabled) {
            val activity = activityBinding.activity
            if (activity.checkCallingOrSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
                    && activity.checkCallingOrSelfPermission(BLUETOOTH_ADMIN) == PERMISSION_GRANTED
                    && activity.checkCallingOrSelfPermission(BLUETOOTH) == PERMISSION_GRANTED) {
                onPermissionGranted = onGranted
                onPermissionRefused = onRefused
                GpsUtils(activity).turnGPSOn {
                    if (it) {
                        onGranted()
                    } else {
                        onRefused("error_no_gps", "Gps need to be turned on to scan BT devices")
                    }
                    onPermissionGranted = null
                    onPermissionRefused = null
                }

            } else {
                onPermissionGranted = onGranted
                onPermissionRefused = onRefused
                ActivityCompat.requestPermissions(activityBinding.activity, arrayOf(ACCESS_FINE_LOCATION, BLUETOOTH, BLUETOOTH_ADMIN), REQUEST_PERMISSION)
            }

        } else {
            onPermissionGranted = onGranted
            onPermissionRefused = onRefused
            val enableBT = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activityBinding.activity.startActivityForResult(enableBT, REQUEST_BLUETOOTH)
        }
    }

    private fun stopScan(result: Result?) {
        adapter?.cancelDiscovery()
        channel.invokeMethod(ACTION_SCAN_STOPPED, null)
        try {
            activityBinding.activity.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, e)
            //throw RuntimeException("Cannot stop Bluetooth scan before starting.")
        }
        result?.success(null)
    }

    private fun scan(result: Result, returnBondedDevices: Boolean = false) {

        startPermissionValidation({

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
                bondedDevices = adapter!!.bondedDevices.mapNotNull { device ->
                    toMap(device)
                }
            }
            result.success(bondedDevices)

        }, { code: String, message: String ->
            result.error(code, message, null)
        })
    }
}
