package com.kiwi.flutterscanbluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Regression for KIWI-MERCHANT-42B: destroying MainActivity with no active BT
 * permission/scan flow must not NPE in onDetachedFromActivity.
 */
class FlutterScanBluetoothPluginDetachTest {

    private lateinit var plugin: FlutterScanBluetoothPlugin
    private lateinit var activityBinding: ActivityPluginBinding
    private lateinit var activity: Activity
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var adapter: BluetoothAdapter

    @Before
    fun setUp() {
        plugin = FlutterScanBluetoothPlugin()

        val messenger = mock<BinaryMessenger>()
        val engineBinding = mock<FlutterPlugin.FlutterPluginBinding> {
            on { binaryMessenger } doReturn messenger
        }
        plugin.onAttachedToEngine(engineBinding)

        adapter = mock()
        whenever(adapter.isDiscovering).thenReturn(false)

        bluetoothManager = mock()
        whenever(bluetoothManager.adapter).thenReturn(adapter)

        activity = mock()
        whenever(activity.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(bluetoothManager)

        activityBinding = mock()
        whenever(activityBinding.activity).thenReturn(activity)

        plugin.onAttachedToActivity(activityBinding)
    }

    @Test
    fun onDetachedFromActivity_withoutPermissionFlow_doesNotThrow() {
        plugin.onDetachedFromActivity()
    }

    @Test
    fun onDetachedFromActivity_whileDiscovering_stopsScanWithoutThrowing() {
        whenever(adapter.isDiscovering).thenReturn(true)
        whenever(activity.unregisterReceiver(any())).then { null }

        plugin.onDetachedFromActivity()
    }
}
