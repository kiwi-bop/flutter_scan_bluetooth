import 'dart:async';

import 'package:flutter/services.dart';

class BluetoothDevice {
  final String name;
  final String address;
  final bool paired;
  final bool nearby;

  const BluetoothDevice(this.name, this.address, {this.nearby = false, this.paired = false});

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is BluetoothDevice && runtimeType == other.runtimeType && name == other.name && address == other.address;

  @override
  int get hashCode => name.hashCode ^ address.hashCode;
}

class FlutterScanBluetooth {
  static MethodChannel _channel = const MethodChannel('flutter_scan_bluetooth')
    ..setMethodCallHandler((methodCall) {
      switch (methodCall.method) {
        case 'action_new_device':
          _newDevice(methodCall.arguments);
          break;
        case 'action_scan_stopped':
          _scanStopped.add(true);
          break;
      }
    });

  static List<BluetoothDevice> pairedDevices = [];

  static StreamController<BluetoothDevice> _controller = StreamController<BluetoothDevice>();
  static StreamController<bool> _scanStopped = StreamController<bool>();

  static Stream<BluetoothDevice> get devices => _controller.stream;
  static Stream<bool> get scanStopped => _scanStopped.stream;

  static Future<void> startScan({pairedDevices = false}) async {
    final bondedDevices = await _channel.invokeMethod('action_start_scan', pairedDevices);
    for (var device in bondedDevices) {
      final d = BluetoothDevice(device['name'], device['address'], paired: true);
      FlutterScanBluetooth.pairedDevices.add(d);
      _controller.add(d);
    }
  }

  static Future<void> get stopScan => _channel.invokeMethod('action_stop_scan');

  static void _newDevice(device) {
    _controller.add(BluetoothDevice(
      device['name'],
      device['address'],
      nearby: true,
      paired: pairedDevices.firstWhere((item) => item.address == device['address'], orElse: () => null) != null,
    ));
  }
}
