import 'package:flutter/material.dart';

import '../../app_services.dart';
import '../../domain/note.dart';
import '../theme.dart';
import '../widgets/app_footer.dart';
import '../widgets/common.dart';
import 'home_screen.dart' show NoteTile;
import 'result_screen.dart';

/// Cronologia locale, con ricerca ed eliminazione.
class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  final TextEditingController _search = TextEditingController();
  List<Note> _notes = const <Note>[];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final AppServices s = ServicesScope.of(context);
    final List<Note> notes = await s.notes.list(query: _search.text);
    if (mounted) {
      setState(() {
        _notes = notes;
        _loading = false;
      });
    }
  }

  Future<void> _deleteOne(Note note) async {
    final bool ok = await confirm(
      context,
      title: 'Eliminare?',
      message: 'La nota "${note.title}" verrà eliminata definitivamente '
          'da questo dispositivo.',
      confirmLabel: 'Elimina',
      destructive: true,
    );
    if (!ok || !mounted) return;
    await ServicesScope.of(context).notes.delete(note.id!);
    await _load();
  }

  Future<void> _deleteAll() async {
    final bool ok = await confirm(
      context,
      title: 'Cancellare tutta la cronologia?',
      message: 'Verranno eliminate ${_notes.length} elaborazioni e gli '
          'eventuali audio conservati. L\'operazione non è reversibile.',
      confirmLabel: 'Cancella tutto',
      destructive: true,
    );
    if (!ok || !mounted) return;
    await ServicesScope.of(context).notes.clear();
    await _load();
    if (mounted) showMessage(context, 'Cronologia cancellata');
  }

  @override
  Widget build(BuildContext context) {
    final bool historyOff =
        !ServicesScope.of(context).settings.value.historyEnabled;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Cronologia'),
        actions: <Widget>[
          if (_notes.isNotEmpty)
            IconButton(
              tooltip: 'Cancella tutto',
              icon: const Icon(Icons.delete_sweep_outlined),
              onPressed: _deleteAll,
            ),
        ],
      ),
      bottomNavigationBar: const BottomBar(),
      body: SafeArea(
        child: Column(
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
              child: TextField(
                controller: _search,
                onChanged: (_) => _load(),
                decoration: InputDecoration(
                  hintText: 'Cerca nei testi…',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _search.text.isEmpty
                      ? null
                      : IconButton(
                          icon: const Icon(Icons.close),
                          onPressed: () {
                            _search.clear();
                            _load();
                          },
                        ),
                ),
              ),
            ),
            if (historyOff)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Card(
                  color: Theme.of(context).colorScheme.secondaryContainer,
                  child: const Padding(
                    padding: EdgeInsets.all(12),
                    child: Text(
                      'La cronologia è disattivata: le nuove elaborazioni non '
                      'vengono salvate.',
                    ),
                  ),
                ),
              ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _notes.isEmpty
                      ? const EmptyState(
                          icon: Icons.inbox_outlined,
                          title: 'Nessuna elaborazione',
                          message:
                              'Condividi un audio con l\'app per iniziare.',
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                          itemCount: _notes.length,
                          separatorBuilder: (BuildContext context, int index) => Gaps.h8,
                          itemBuilder: (BuildContext context, int i) {
                            final Note note = _notes[i];
                            return NoteTile(
                              note: note,
                              onDelete: () => _deleteOne(note),
                              onTap: () async {
                                await Navigator.of(context).push(
                                  MaterialPageRoute<void>(
                                    builder: (_) => ResultScreen(note: note),
                                  ),
                                );
                                await _load();
                              },
                            );
                          },
                        ),
            ),
          ],
        ),
      ),
    );
  }
}
