import 'package:flutter/material.dart';

import 'app_state.dart';
import 'auth/session.dart';
import 'push/push_service.dart';
import 'storage/local_store.dart';
import 'ui/home_shell.dart';
import 'ui/login_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final firebaseReady = await PushService.initFirebase();
  final session = Session(SecureSecretStore());
  await session.restore();
  runApp(SmartCrmApp(app: AppState(session: session, store: FileStore(), firebaseReady: firebaseReady)));
}

/// SmartCRM field app (SRS §6.11): visits, assigned leads and deals, meeting reports,
/// notifications and attendance — usable offline, syncing automatically.
class SmartCrmApp extends StatefulWidget {
  const SmartCrmApp({super.key, required this.app});

  final AppState app;

  @override
  State<SmartCrmApp> createState() => _SmartCrmAppState();
}

class _SmartCrmAppState extends State<SmartCrmApp> {
  final _pushTick = ValueNotifier(0);
  final _openInbox = ValueNotifier(0);
  bool _started = false;

  @override
  void initState() {
    super.initState();
    widget.app.session.addListener(_onSession);
    widget.app.api.onUnauthorized = () {
      if (widget.app.session.signedIn) widget.app.signOut();
    };
    if (widget.app.session.signedIn) {
      _start();
      _started = true;
    }
  }

  void _start() => widget.app.startSession(onPush: () => _pushTick.value++, onPushOpened: () => _openInbox.value++);

  @override
  void dispose() {
    widget.app.session.removeListener(_onSession);
    super.dispose();
  }

  void _onSession() {
    final signedIn = widget.app.session.signedIn;
    if (signedIn && !_started) _start();
    setState(() => _started = signedIn);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SmartCRM',
      theme: ThemeData(colorSchemeSeed: const Color(0xFF7C3AED), useMaterial3: true),
      darkTheme: ThemeData(colorSchemeSeed: const Color(0xFF7C3AED), brightness: Brightness.dark, useMaterial3: true),
      home: _started
          ? HomeShell(app: widget.app, pushTick: _pushTick, openInbox: _openInbox)
          : LoginScreen(session: widget.app.session, api: widget.app.api),
    );
  }
}
