import 'package:flutter/material.dart';

import '../data/repository.dart';
import '../sync/sync_service.dart';
import 'common.dart';

const _outcomes = {'positive': 'Positive', 'neutral': 'Neutral', 'negative': 'Negative', 'no_show': 'No-show'};

/// FR-M.3: my recent and upcoming meetings; after a meeting, file its report.
class MeetingsScreen extends StatelessWidget {
  const MeetingsScreen({super.key, required this.repository, required this.sync});

  final CrmRepository repository;
  final SyncService sync;

  @override
  Widget build(BuildContext context) => CachedList(
        load: repository.meetings,
        reloadSignal: sync,
        empty: 'No meetings in the last two weeks or coming up.',
        itemBuilder: (context, meeting, reload) {
          final starts = DateTime.tryParse(meeting['startsAt'] as String? ?? '');
          final started = starts != null && starts.isBefore(DateTime.now());
          final reported = meeting['reportOutcome'] != null;
          return ListTile(
            title: Text(meeting['title'] as String? ?? ''),
            subtitle: Text([
              formatDateTime(meeting['startsAt']),
              meeting['customerName'] ?? meeting['leadName'],
              if (reported) 'Report: ${_outcomes[meeting['reportOutcome']] ?? meeting['reportOutcome']}',
            ].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
            trailing: meeting['_pending'] == true
                ? const PendingChip()
                : started
                    ? Icon(reported ? Icons.task_alt : Icons.edit_note)
                    : const Icon(Icons.event),
            onTap: started
                ? () async {
                    final saved = await Navigator.of(context).push<bool>(
                      MaterialPageRoute(builder: (_) => MeetingReportForm(repository: repository, meeting: meeting)),
                    );
                    if (saved == true) {
                      reload();
                      if (context.mounted) showMessage(context, 'Meeting report saved');
                    }
                  }
                : () => showMessage(context, 'You can report on a meeting once it has started.'),
          );
        },
      );
}

class MeetingReportForm extends StatefulWidget {
  const MeetingReportForm({super.key, required this.repository, required this.meeting});

  final CrmRepository repository;
  final Json meeting;

  @override
  State<MeetingReportForm> createState() => _MeetingReportFormState();
}

class _MeetingReportFormState extends State<MeetingReportForm> {
  final _form = GlobalKey<FormState>();
  late final _notes = TextEditingController(text: widget.meeting['reportNotes'] as String?);
  late final _nextSteps = TextEditingController(text: widget.meeting['nextSteps'] as String?);
  late String _outcome = widget.meeting['reportOutcome'] as String? ?? 'positive';
  bool _saving = false;

  @override
  void dispose() {
    _notes.dispose();
    _nextSteps.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _saving = true);
    await widget.repository.submitMeetingReport(widget.meeting,
        outcome: _outcome, notes: _notes.text.trim(), nextSteps: _nextSteps.text.trim().isEmpty ? null : _nextSteps.text.trim());
    if (mounted) Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Meeting report')),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(widget.meeting['title'] as String? ?? '', style: Theme.of(context).textTheme.titleMedium),
            Text(formatDateTime(widget.meeting['startsAt'])),
            const SizedBox(height: 16),
            SegmentedButton<String>(
              segments: [for (final e in _outcomes.entries) ButtonSegment(value: e.key, label: Text(e.value))],
              selected: {_outcome},
              onSelectionChanged: (s) => setState(() => _outcome = s.first),
            ),
            const SizedBox(height: 16),
            TextFormField(
              key: const Key('reportNotes'),
              controller: _notes,
              maxLength: 5000,
              maxLines: 6,
              decoration: const InputDecoration(labelText: 'Notes', hintText: 'What was discussed', border: OutlineInputBorder()),
              validator: (v) => v == null || v.trim().isEmpty ? 'Add a few notes' : null,
            ),
            const SizedBox(height: 8),
            TextFormField(
              controller: _nextSteps,
              maxLength: 2000,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Next steps', hintText: 'e.g. Send quotation by Friday', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 8),
            FilledButton(onPressed: _saving ? null : _save, child: Text(_saving ? 'Saving…' : 'Save report')),
          ],
        ),
      ),
    );
  }
}
