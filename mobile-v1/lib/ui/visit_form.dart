import 'package:flutter/material.dart';

import '../data/repository.dart';
import '../location/location.dart';
import 'common.dart';

/// FR-M.1: log a customer visit — date and time, location (when allowed), purpose and outcome.
/// Works offline: the visit is saved on the phone and synced later.
class VisitForm extends StatefulWidget {
  const VisitForm({super.key, required this.repository});

  final CrmRepository repository;

  @override
  State<VisitForm> createState() => _VisitFormState();
}

class _VisitFormState extends State<VisitForm> {
  final _form = GlobalKey<FormState>();
  final _purpose = TextEditingController();
  final _outcome = TextEditingController();
  final _notes = TextEditingController();
  List<Json> _customers = const [];
  int? _customerId;
  DateTime _when = DateTime.now();
  bool _useLocation = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    widget.repository.customers().then((c) {
      if (mounted) setState(() => _customers = c.data);
    }).catchError((Object _) {});
  }

  @override
  void dispose() {
    _purpose.dispose();
    _outcome.dispose();
    _notes.dispose();
    super.dispose();
  }

  Future<void> _pickTime() async {
    final date = await showDatePicker(context: context, initialDate: _when, firstDate: DateTime.now().subtract(const Duration(days: 30)), lastDate: DateTime.now());
    if (date == null || !mounted) return;
    final time = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(_when));
    if (time == null) return;
    final picked = DateTime(date.year, date.month, date.day, time.hour, time.minute);
    setState(() => _when = picked.isAfter(DateTime.now()) ? DateTime.now() : picked);
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _saving = true);
    final position = _useLocation ? await currentPosition() : null;
    final customer = _customers.where((c) => c['id'] == _customerId).firstOrNull;
    await widget.repository.logVisit(
      customerId: _customerId,
      customerName: customer?['companyName'] as String?,
      purpose: _purpose.text.trim(),
      outcome: _outcome.text.trim(),
      notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
      occurredAt: _when,
      latitude: position != null ? round6(position.latitude) : null,
      longitude: position != null ? round6(position.longitude) : null,
    );
    if (mounted) Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Log a visit')),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            DropdownButtonFormField<int?>(
              initialValue: _customerId,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Customer (optional)', border: OutlineInputBorder()),
              items: [
                const DropdownMenuItem<int?>(value: null, child: Text('No customer (e.g. prospecting)')),
                for (final c in _customers)
                  DropdownMenuItem<int?>(value: (c['id'] as num).toInt(), child: Text(c['companyName'] as String? ?? '', overflow: TextOverflow.ellipsis)),
              ],
              onChanged: (v) => setState(() => _customerId = v),
            ),
            const SizedBox(height: 16),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.schedule),
              title: Text(formatDateTime(_when.toIso8601String())),
              subtitle: const Text('When'),
              trailing: TextButton(onPressed: _pickTime, child: const Text('Change')),
            ),
            TextFormField(
              key: const Key('purpose'),
              controller: _purpose,
              maxLength: 200,
              decoration: const InputDecoration(labelText: 'Purpose', hintText: 'e.g. Product demo', border: OutlineInputBorder()),
              validator: (v) => v == null || v.trim().isEmpty ? 'What was the visit for?' : null,
            ),
            const SizedBox(height: 8),
            TextFormField(
              key: const Key('outcome'),
              controller: _outcome,
              maxLength: 500,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Outcome', hintText: 'e.g. Wants a quotation', border: OutlineInputBorder()),
              validator: (v) => v == null || v.trim().isEmpty ? 'What came of it?' : null,
            ),
            const SizedBox(height: 8),
            TextFormField(
              controller: _notes,
              maxLength: 2000,
              maxLines: 4,
              decoration: const InputDecoration(labelText: 'Notes (optional)', border: OutlineInputBorder()),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: _useLocation,
              onChanged: (v) => setState(() => _useLocation = v),
              title: const Text('Record my location'),
            ),
            const SizedBox(height: 8),
            FilledButton(onPressed: _saving ? null : _save, child: Text(_saving ? 'Saving…' : 'Save visit')),
          ],
        ),
      ),
    );
  }
}
