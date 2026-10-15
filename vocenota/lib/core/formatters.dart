/// Formattazioni testuali condivise (nessuna dipendenza da Flutter,
/// così restano verificabili con i test unitari).
class Fmt {
  /// `03:12` oppure `1:04:09` per durate oltre l'ora.
  static String duration(Duration d) {
    final int totalSeconds = d.inSeconds.abs();
    final int h = totalSeconds ~/ 3600;
    final int m = (totalSeconds % 3600) ~/ 60;
    final int s = totalSeconds % 60;
    final String mm = m.toString().padLeft(2, '0');
    final String ss = s.toString().padLeft(2, '0');
    return h > 0 ? '$h:$mm:$ss' : '$mm:$ss';
  }

  /// Dimensione leggibile: `4,2 MB`.
  static String bytes(int value) {
    const List<String> units = <String>['B', 'KB', 'MB', 'GB'];
    double v = value.toDouble();
    int unit = 0;
    while (v >= 1024 && unit < units.length - 1) {
      v /= 1024;
      unit++;
    }
    final String n = unit == 0
        ? v.toStringAsFixed(0)
        : v.toStringAsFixed(v >= 100 ? 0 : 1).replaceAll('.', ',');
    return '$n ${units[unit]}';
  }

  /// Data in formato italiano compatto: `15/10/2026 21:40`.
  static String dateTime(DateTime t) {
    String two(int v) => v.toString().padLeft(2, '0');
    return '${two(t.day)}/${two(t.month)}/${t.year} ${two(t.hour)}:${two(t.minute)}';
  }

  /// Titolo leggibile ricavato dal nome del file.
  ///
  /// Toglie il percorso, l'estensione e il prefisso temporale che l'app
  /// aggiunge quando copia il file nella propria cartella
  /// (`1760561234567_AUD-20260101-WA0007.opus`).
  static String titleFromFileName(String fileName) {
    String name = fileName.split('/').last;
    name = name.replaceFirst(RegExp(r'^\d{10,}_'), '');
    final int dot = name.lastIndexOf('.');
    // `dot == 0` significa che il nome è solo un'estensione (".opus").
    if (dot > 0) {
      name = name.substring(0, dot);
    } else if (dot == 0) {
      name = '';
    }
    name = name.replaceAll(RegExp(r'[_\-]+'), ' ');
    name = name.replaceAll(RegExp(r'\s+'), ' ').trim();
    // Un nome fatto di soli punti o separatori non è un titolo utile.
    if (name.replaceAll(RegExp(r'[.\s]'), '').isEmpty) return 'Audio';
    return name.length <= 60 ? name : '${name.substring(0, 57)}…';
  }

  /// Taglia un testo preservando le parole.
  static String preview(String text, {int max = 140}) {
    final String t = text.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (t.length <= max) return t;
    final int cut = t.lastIndexOf(' ', max);
    return '${t.substring(0, cut > 40 ? cut : max)}…';
  }
}
