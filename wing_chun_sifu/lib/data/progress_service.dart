import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Tiene traccia di cosa l'allievo ha gia' studiato.
///
/// Nessun account, nessuna rete: i progressi restano sul dispositivo. Un'app
/// di allenamento deve funzionare in una palestra senza campo.
class ProgressService extends ChangeNotifier {
  ProgressService._();

  static final ProgressService instance = ProgressService._();

  static const _keyStudied = 'studiati';
  static const _keyLastLesson = 'ultima_lezione';
  static const _keyIntroSeen = 'intro_vista';

  SharedPreferences? _prefs;
  Set<String> _studied = <String>{};
  String? _lastLesson;
  bool _introSeen = false;

  Set<String> get studied => Set.unmodifiable(_studied);
  String? get lastLesson => _lastLesson;
  bool get introSeen => _introSeen;

  Future<void> load() async {
    _prefs = await SharedPreferences.getInstance();
    _studied = (_prefs?.getStringList(_keyStudied) ?? const <String>[]).toSet();
    _lastLesson = _prefs?.getString(_keyLastLesson);
    _introSeen = _prefs?.getBool(_keyIntroSeen) ?? false;
    notifyListeners();
  }

  bool isStudied(String id) => _studied.contains(id);

  Future<void> toggleStudied(String id) async {
    if (!_studied.remove(id)) _studied.add(id);
    await _prefs?.setStringList(_keyStudied, _studied.toList());
    notifyListeners();
  }

  Future<void> markStudied(String id) async {
    if (_studied.add(id)) {
      await _prefs?.setStringList(_keyStudied, _studied.toList());
      notifyListeners();
    }
  }

  Future<void> setLastLesson(String id) async {
    if (_lastLesson == id) return;
    _lastLesson = id;
    await _prefs?.setString(_keyLastLesson, id);
    notifyListeners();
  }

  Future<void> setIntroSeen() async {
    if (_introSeen) return;
    _introSeen = true;
    await _prefs?.setBool(_keyIntroSeen, true);
    notifyListeners();
  }

  /// Quanti elementi di [ids] risultano studiati.
  int countIn(Iterable<String> ids) =>
      ids.where(_studied.contains).length;

  Future<void> reset() async {
    _studied.clear();
    _lastLesson = null;
    await _prefs?.remove(_keyStudied);
    await _prefs?.remove(_keyLastLesson);
    notifyListeners();
  }
}
