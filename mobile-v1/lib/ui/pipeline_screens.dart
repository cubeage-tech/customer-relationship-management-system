import 'package:flutter/material.dart';

import '../data/repository.dart';
import '../sync/sync_service.dart';
import 'common.dart';

/// FR-M.2: the leads assigned to me, and moving them through the pipeline.
class LeadsScreen extends StatelessWidget {
  const LeadsScreen({super.key, required this.repository, required this.sync});

  final CrmRepository repository;
  final SyncService sync;

  Future<void> _changeStage(BuildContext context, Json lead, VoidCallback reload) async {
    final current = leadStages.indexOf(lead['stage'] as String? ?? '');
    final stage = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => _StagePicker(
        title: lead['leadName'] as String? ?? 'Lead',
        stages: [for (final s in leadStages) (code: s, label: label(s))],
        current: lead['stage'] as String?,
      ),
    );
    if (stage == null || stage == lead['stage'] || !context.mounted) return;
    var force = false;
    // BR-1: skipping stages forward is an explicit override.
    if (leadStages.indexOf(stage) > current + 1) {
      force = await showDialog<bool>(
            context: context,
            builder: (context) => AlertDialog(
              title: const Text('Skip stages?'),
              content: Text('Move straight to ${label(stage)}, skipping the stages in between?'),
              actions: [
                TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
                FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Move')),
              ],
            ),
          ) ??
          false;
      if (!force) return;
    }
    await repository.changeLeadStage(lead, stage, force: force);
    reload();
  }

  @override
  Widget build(BuildContext context) => CachedList(
        load: repository.leads,
        reloadSignal: sync,
        empty: 'No leads assigned to you.',
        itemBuilder: (context, lead, reload) => ListTile(
          title: Text(lead['leadName'] as String? ?? ''),
          subtitle: Text([
            lead['companyName'],
            label(lead['stage'] as String?),
            if (lead['followUpDate'] != null) 'follow up ${formatDateTime(lead['followUpDate'])}',
          ].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
          trailing: lead['_pending'] == true ? const PendingChip() : const Icon(Icons.swap_vert),
          onTap: () => _changeStage(context, lead, reload),
        ),
      );
}

/// FR-M.2: the opportunities assigned to me, and their stage.
class OpportunitiesScreen extends StatelessWidget {
  const OpportunitiesScreen({super.key, required this.repository, required this.sync});

  final CrmRepository repository;
  final SyncService sync;

  Future<void> _changeStage(BuildContext context, Json deal, VoidCallback reload) async {
    final stages = (await repository.opportunityStages()).data.where((s) => s['active'] != false).toList();
    if (!context.mounted) return;
    final stage = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => _StagePicker(
        title: deal['customerName'] as String? ?? 'Opportunity',
        stages: [for (final s in stages) (code: s['code'] as String, label: s['label'] as String? ?? label(s['code'] as String))],
        current: deal['stage'] as String?,
      ),
    );
    if (stage == null || stage == deal['stage'] || !context.mounted) return;
    String? lossReason;
    if (stage == 'lost') {
      lossReason = await _askReason(context);
      if (lossReason == null) return;
    }
    await repository.changeOpportunityStage(deal, stage, lossReason: lossReason);
    reload();
  }

  Future<String?> _askReason(BuildContext context) {
    final controller = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Why was it lost?'),
        content: TextField(controller: controller, autofocus: true, maxLength: 500),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim().isEmpty ? null : controller.text.trim()),
            child: const Text('Mark lost'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) => CachedList(
        load: repository.opportunities,
        reloadSignal: sync,
        empty: 'No opportunities assigned to you.',
        itemBuilder: (context, deal, reload) => ListTile(
          title: Text(deal['customerName'] as String? ?? ''),
          subtitle: Text([
            deal['productService'],
            formatMoney(deal['dealValue']),
            label(deal['stage'] as String?),
          ].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
          trailing: deal['_pending'] == true ? const PendingChip() : const Icon(Icons.swap_vert),
          onTap: () => _changeStage(context, deal, reload),
        ),
      );
}

class _StagePicker extends StatelessWidget {
  const _StagePicker({required this.title, required this.stages, required this.current});

  final String title;
  final List<({String code, String label})> stages;
  final String? current;

  @override
  Widget build(BuildContext context) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            ListTile(title: Text('Move "$title" to', style: Theme.of(context).textTheme.titleMedium)),
            for (final s in stages)
              ListTile(
                title: Text(s.label),
                trailing: s.code == current ? const Icon(Icons.check) : null,
                onTap: () => Navigator.pop(context, s.code),
              ),
          ],
        ),
      );
}
