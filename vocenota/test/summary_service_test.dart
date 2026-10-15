import 'package:flutter_test/flutter_test.dart';
import 'package:vocenota/core/constants.dart';
import 'package:vocenota/core/failures.dart';
import 'package:vocenota/domain/summary.dart';
import 'package:vocenota/services/summary/summary_api_client.dart';
import 'package:vocenota/services/summary/summary_service.dart';

/// Client finto: nessuna rete, comportamento deciso dal test.
class FakeApiClient extends SummaryApiClient {
  FakeApiClient({this.response, this.error, this.configured = true})
      : super(baseUrl: 'https://esempio.test', deviceId: 'dispositivo-test');

  final SummaryBundle? response;
  final AppFailure? error;
  final bool configured;
  int calls = 0;
  SummaryStyle? lastStyle;

  @override
  bool get isConfigured => configured;

  @override
  Future<SummaryBundle> summarize({
    required String transcript,
    required SummaryStyle style,
    String language = 'it',
  }) async {
    calls++;
    lastStyle = style;
    if (error != null) throw error!;
    return response!;
  }
}

SummaryBundle cloudBundle({String text = 'Riassunto dal server.'}) =>
    SummaryBundle(
      style: SummaryStyle.standard,
      text: text,
      keyPoints: const <String>['Punto dal server'],
      actions: const <ActionItem>[ActionItem(text: 'Azione dal server')],
      engine: SummaryEngine.cloud,
      generatedAt: DateTime(2026, 10, 15),
    );

const String trascrizione =
    'Ciao, ti ricordo la consegna di venerdì. Dobbiamo rivedere il '
    'preventivo prima di mandarlo al cliente. Il margine è troppo basso.';

void main() {
  test('con consenso al cloud usa il server', () async {
    final FakeApiClient client = FakeApiClient(response: cloudBundle());
    final SummaryOutcome outcome = await SummaryService(client: client)
        .summarize(
      transcript: trascrizione,
      style: SummaryStyle.standard,
      cloudAllowed: true,
    );

    expect(client.calls, 1);
    expect(outcome.usedCloud, isTrue);
    expect(outcome.bundle.text, 'Riassunto dal server.');
    expect(outcome.fallbackReason, isNull);
  });

  test('senza consenso non contatta il server e riassume in locale', () async {
    final FakeApiClient client = FakeApiClient(response: cloudBundle());
    final SummaryOutcome outcome = await SummaryService(client: client)
        .summarize(
      transcript: trascrizione,
      style: SummaryStyle.standard,
      cloudAllowed: false,
    );

    expect(client.calls, 0);
    expect(outcome.usedCloud, isFalse);
    expect(outcome.bundle.engine, SummaryEngine.locale);
    expect(outcome.bundle.text, isNotEmpty);
  });

  test('se il server non è configurato resta in locale', () async {
    final FakeApiClient client =
        FakeApiClient(response: cloudBundle(), configured: false);
    final SummaryOutcome outcome = await SummaryService(client: client)
        .summarize(
      transcript: trascrizione,
      style: SummaryStyle.breve,
      cloudAllowed: true,
    );
    expect(client.calls, 0);
    expect(outcome.bundle.engine, SummaryEngine.locale);
  });

  test('un errore del server fa ripiegare sul locale spiegando il motivo',
      () async {
    final FakeApiClient client = FakeApiClient(
      error: const AppFailure('Hai raggiunto il limite giornaliero.'),
    );
    final SummaryOutcome outcome = await SummaryService(client: client)
        .summarize(
      transcript: trascrizione,
      style: SummaryStyle.standard,
      cloudAllowed: true,
    );

    expect(client.calls, 1);
    expect(outcome.usedCloud, isFalse);
    expect(outcome.bundle.text, isNotEmpty);
    expect(outcome.fallbackReason, 'Hai raggiunto il limite giornaliero.');
  });

  test('una risposta vuota del server fa ripiegare sul locale', () async {
    final FakeApiClient client = FakeApiClient(
      response: SummaryBundle(
        style: SummaryStyle.standard,
        text: '   ',
        keyPoints: const <String>[],
        actions: const <ActionItem>[],
        engine: SummaryEngine.cloud,
        generatedAt: DateTime(2026, 10, 15),
      ),
    );
    final SummaryOutcome outcome = await SummaryService(client: client)
        .summarize(
      transcript: trascrizione,
      style: SummaryStyle.standard,
      cloudAllowed: true,
    );
    expect(outcome.usedCloud, isFalse);
    expect(outcome.fallbackReason, contains('vuoto'));
  });

  test('senza client configurato funziona comunque in locale', () async {
    final SummaryOutcome outcome = await SummaryService(client: null)
        .summarize(
      transcript: trascrizione,
      style: SummaryStyle.dettagliato,
      cloudAllowed: true,
    );
    expect(outcome.bundle.engine, SummaryEngine.locale);
  });

  test('lo stile richiesto viene inoltrato al server', () async {
    final FakeApiClient client = FakeApiClient(response: cloudBundle());
    await SummaryService(client: client).summarize(
      transcript: trascrizione,
      style: SummaryStyle.dettagliato,
      cloudAllowed: true,
    );
    expect(client.lastStyle, SummaryStyle.dettagliato);
  });
}
