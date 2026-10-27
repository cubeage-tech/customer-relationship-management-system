import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../auth/session.dart';

/// Sign in with the same email and password as the SmartCRM web app.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.session, required this.api});

  final Session session;
  final ApiClient api;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _code = TextEditingController();
  bool _busy = false;
  String? _error;
  // SEC-1c: set while the second sign-in step (the authenticator code) is shown.
  String? _mfaToken;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      if (_mfaToken != null) {
        await widget.session.verifyMfa(widget.api, _mfaToken!, _code.text);
      } else {
        final challenge = await widget.session.signIn(widget.api, _email.text, _password.text);
        if (challenge != null) setState(() => _mfaToken = challenge);
      }
    } on MfaSetupRequired catch (e) {
      setState(() => _error = e.toString());
    } on ApiException catch (e) {
      setState(() {
        _error = e.message;
        if (e.statusCode == 401 && _mfaToken != null && e.message.contains('expired')) _mfaToken = null;
      });
    } on NetworkException {
      setState(() => _error = 'Can\'t reach SmartCRM. Check your connection and try again.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: _form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('SmartCRM', style: Theme.of(context).textTheme.headlineMedium, textAlign: TextAlign.center),
                  const SizedBox(height: 4),
                  Text('Field app', style: Theme.of(context).textTheme.bodyMedium, textAlign: TextAlign.center),
                  const SizedBox(height: 32),
                  if (_mfaToken != null) ...[
                    Text('Enter the 6-digit code from your authenticator app, or a recovery code.',
                        style: Theme.of(context).textTheme.bodyMedium),
                    const SizedBox(height: 16),
                    TextFormField(
                      key: const Key('code'),
                      controller: _code,
                      autofocus: true,
                      autofillHints: const [AutofillHints.oneTimeCode],
                      decoration: const InputDecoration(labelText: 'Code', border: OutlineInputBorder()),
                      validator: (v) => v == null || v.trim().isEmpty ? 'Enter the code' : null,
                      onFieldSubmitted: (_) => _submit(),
                    ),
                  ] else ...[
                  TextFormField(
                    key: const Key('email'),
                    controller: _email,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.email],
                    decoration: const InputDecoration(labelText: 'Email', border: OutlineInputBorder()),
                    validator: (v) => v == null || !v.contains('@') ? 'Enter your email' : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    key: const Key('password'),
                    controller: _password,
                    obscureText: true,
                    autofillHints: const [AutofillHints.password],
                    decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder()),
                    validator: (v) => v == null || v.isEmpty ? 'Enter your password' : null,
                    onFieldSubmitted: (_) => _submit(),
                  ),
                  ],
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ],
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: _busy ? null : _submit,
                    child: _busy
                        ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : Text(_mfaToken != null ? 'Verify' : 'Sign in'),
                  ),
                  if (_mfaToken != null)
                    TextButton(
                      onPressed: _busy ? null : () => setState(() { _mfaToken = null; _code.clear(); _error = null; }),
                      child: const Text('Back'),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
