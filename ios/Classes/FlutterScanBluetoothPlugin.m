#import "FlutterScanBluetoothPlugin.h"
#import <flutter_scan_bluetooth/flutter_scan_bluetooth-Swift.h>

@implementation FlutterScanBluetoothPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterScanBluetoothPlugin registerWithRegistrar:registrar];
}
@end
