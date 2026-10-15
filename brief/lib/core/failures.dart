/// Errore applicativo con messaggio già pronto per l'utente (in italiano).
class AppFailure implements Exception {
  const AppFailure(this.message, {this.cause, this.recoverable = true});

  final String message;
  final Object? cause;

  /// `true` se ritentare può avere senso (rete, servizio occupato…).
  final bool recoverable;

  @override
  String toString() => 'AppFailure($message${cause == null ? '' : ', $cause'})';
}

/// Sollevata quando l'utente annulla una elaborazione in corso.
class CancelledException implements Exception {
  const CancelledException();
  @override
  String toString() => 'CancelledException';
}

/// Token di annullamento cooperativo condiviso fra i passi della pipeline.
class CancellationToken {
  bool _cancelled = false;
  final List<void Function()> _listeners = <void Function()>[];

  bool get isCancelled => _cancelled;

  void cancel() {
    if (_cancelled) return;
    _cancelled = true;
    for (final void Function() l in List<void Function()>.of(_listeners)) {
      l();
    }
  }

  void addListener(void Function() listener) => _listeners.add(listener);

  /// Interrompe il flusso se è stato richiesto l'annullamento.
  void throwIfCancelled() {
    if (_cancelled) throw const CancelledException();
  }
}
