import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_scan_bluetooth/flutter_scan_bluetooth.dart';

void main() => runApp(new MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _data = '';
  bool _scanning = false;

  @override
  void initState() {
    super.initState();

    FlutterScanBluetooth.devices.listen((device) {
      setState(() {
        _data += device.name+'\n';
      });
    });
    FlutterScanBluetooth.scanStopped.listen((device) {
      setState(() {
        _scanning = false;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: new Scaffold(
        appBar: new AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: <Widget>[
            Expanded(child: Text(_data)),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Center(
                child: RaisedButton(child: Text(_scanning ? 'Stop scan' : 'Start scan'), onPressed: () async {
                  try {
                    if(_scanning) {
                      await FlutterScanBluetooth.stopScan;
                      debugPrint("scanning stoped");
                    }
                    else {
                      await FlutterScanBluetooth.startScan(pairedDevices: true);
                      debugPrint("scanning started");
                    }
                    setState(() {
                      if(!_scanning) {
                        _data = '';
                      }
                      _scanning = !_scanning;
                    });
                  } on PlatformException catch (e) {
                    debugPrint(e.toString());
                  }
                }),
              ),
            )
          ],
        ),
      ),
    );
  }
}
