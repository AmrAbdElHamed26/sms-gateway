import 'package:flutter/material.dart';

import 'data/method_channel_gateway_repository.dart';
import 'presentation/dashboard_controller.dart';
import 'presentation/screens/home_shell.dart';

void main() {
  runApp(GatewayApp(controller: DashboardController(MethodChannelGatewayRepository())));
}

class GatewayApp extends StatelessWidget {
  const GatewayApp({super.key, required this.controller});

  final DashboardController controller;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Payment Gateway',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      darkTheme: ThemeData(colorSchemeSeed: Colors.teal, brightness: Brightness.dark, useMaterial3: true),
      home: HomeShell(controller: controller),
    );
  }
}
