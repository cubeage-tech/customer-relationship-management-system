import 'package:flutter/material.dart';

import '../data/repository.dart';
import '../location/location.dart';
import '../sync/sync_service.dart';
import 'common.dart';
import 'visit_form.dart';

/// FR-M.5 attendance (check in / out) and FR-M.1 field visits.
class TodayScreen extends StatefulWidget {
  const TodayScreen({super.key, required this.repository, required this.sync});

  final CrmRepository repository;
  final SyncService sync;

  @override
  State<TodayScreen> createState() => _TodayScreenState();
}

class _TodayScreenState extends State<TodayScreen> {
  Cached<Json?>? _attendance;
  Cached<List<Json>>? _visits;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    widget.sync.addListener(_fromCache);
    _load(refresh: true);
  }

  @override
  void dispose() {
    widget.sync.removeListener(_fromCache);
    super.dispose();
  }

  void _fromCache() {
    if (!widget.sync.running) _load(refresh: false);
  }

  Future<void> _load({required bool refresh}) async {
    final attendance = await widget.repository.attendance(refresh: refresh);
    final visits = await widget.repository.recentVisits(refresh: refresh);
    if (!mounted) return;
    setState(() {
      _attendance = attendance;
      _visits = visits;
    });
  }

  Future<void> _toggleAttendance() async {
    setState(() => _busy = true);
    final position = await currentPosition();
    final lat = position != null ? round6(position.latitude) : null;
    final lng = position != null ? round6(position.longitude) : null;
    final checkedIn = _attendance?.data != null;
    try {
      if (checkedIn) {
        await widget.repository.checkOut(latitude: lat, longitude: lng);
      } else {
        await widget.repository.checkIn(latitude: lat, longitude: lng);
      }
      if (mounted) {
        showMessage(context, '${checkedIn ? 'Checked out' : 'Checked in'}${position == null ? ' (no location)' : ''}');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
      await _load(refresh: false);
    }
  }

  Future<void> _logVisit() async {
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => VisitForm(repository: widget.repository)),
    );
    if (saved == true) {
      await _load(refresh: false);
      if (mounted) showMessage(context, 'Visit saved');
    }
  }

  @override
  Widget build(BuildContext context) {
    final current = _attendance?.data;
    final theme = Theme.of(context);
    return RefreshIndicator(
      onRefresh: () async {
        await widget.sync.flush();
        await _load(refresh: true);
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Attendance', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Text(_attendance == null
                      ? 'Loading…'
                      : current != null
                          ? 'Checked in since ${formatDateTime(current['checkInAt'])}'
                          : 'Not checked in'),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    key: const Key('attendanceButton'),
                    onPressed: _busy || _attendance == null ? null : _toggleAttendance,
                    icon: Icon(current != null ? Icons.logout : Icons.login),
                    label: Text(current != null ? 'Check out' : 'Check in'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(child: Text('Recent visits', style: theme.textTheme.titleMedium)),
              FilledButton.tonalIcon(onPressed: _logVisit, icon: const Icon(Icons.add_location_alt), label: const Text('Log visit')),
            ],
          ),
          if (_visits?.fromCache == true) ...[const SizedBox(height: 8), OfflineNote(fetchedAt: _visits!.fetchedAt)],
          const SizedBox(height: 8),
          if (_visits != null && _visits!.data.isEmpty) const Padding(padding: EdgeInsets.all(16), child: Text('No visits in the last two weeks.')),
          for (final v in _visits?.data ?? const <Json>[])
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.place_outlined),
              title: Text(v['_pending'] == true ? (v['label'] as String? ?? 'Visit') : '${v['customerName'] ?? v['leadName'] ?? 'Visit'} · ${v['purpose'] ?? ''}'),
              subtitle: Text([formatDateTime(v['occurredAt']), if (v['outcome'] != null) v['outcome']].join(' — ')),
              trailing: v['_pending'] == true ? PendingChip(failed: v['_error'] != null) : null,
            ),
        ],
      ),
    );
  }
}
