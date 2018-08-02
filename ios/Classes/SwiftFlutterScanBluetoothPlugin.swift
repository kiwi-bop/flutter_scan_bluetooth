import Flutter
import UIKit
import CoreBluetooth

extension SwiftFlutterScanBluetoothPlugin: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        bluetoothState = central.state
        switch central.state {
        case .unknown:
            print("central.state is .unknown")
        case .resetting:
            print("central.state is .resetting")
        case .unsupported:
            print("central.state is .unsupported")
        case .unauthorized:
            print("central.state is .unauthorized")
        case .poweredOff:
            print("central.state is .poweredOff")
        case .poweredOn:
            print("central.state is .poweredOn")
        }
    }
    
    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        print("Discovered \(peripheral.name ?? "unknown") : \(peripheral.identifier.uuidString)")
        channel.invokeMethod("action_new_device", arguments: toMap(peripheral))
    }
    
    private func toMap(_ device: CBPeripheral) -> [String:String] {
        return ["name": device.name ?? device.identifier.uuidString, "address": device.identifier.uuidString]
    }
}

public class SwiftFlutterScanBluetoothPlugin: NSObject, FlutterPlugin {
    var centralManager: CBCentralManager! = CBCentralManager(delegate: nil, queue: nil)
    var bluetoothState: CBManagerState = .unknown
    let channel: FlutterMethodChannel
    var scanTimer: Timer?
    
    init(_ channel: FlutterMethodChannel) {
        self.channel = channel
        super.init()
        centralManager.delegate = self
    }
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_scan_bluetooth", binaryMessenger: registrar.messenger())
        let instance = SwiftFlutterScanBluetoothPlugin(channel)
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        if(bluetoothState == .unsupported) {
            return result(FlutterError.init(code: "error_no_bt", message: nil, details: nil))
        }
        else if(bluetoothState == .poweredOff) {
            return result(FlutterError.init(code: "error_bt_off", message: nil, details: nil))
        }
        print(call.method);
        switch call.method {
        case "action_start_scan":
            if(centralManager.isScanning) {
                stopScan()
            }
            centralManager.scanForPeripherals(withServices: nil, options: nil)
            
            let bondedDevices = centralManager.retrieveConnectedPeripherals(withServices: [])
            var res = [Dictionary<String, String>]()
            if(call.arguments as! Bool) {
                for device in bondedDevices {
                    res.append(toMap(device))
                }
            }
            scanTimer = Timer.scheduledTimer(withTimeInterval: 15, repeats: false, block: {_ in self.stopScan() })
            result(res)
            break;
        case "action_stop_scan":
            stopScan()
            result(nil)
            break;
        default:
            result(FlutterMethodNotImplemented);
        }
    }
    
    func stopScan() {
        scanTimer?.invalidate()
        centralManager.stopScan()
        channel.invokeMethod("action_scan_stopped", arguments: nil)
    }
}
