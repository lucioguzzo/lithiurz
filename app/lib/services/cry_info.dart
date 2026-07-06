import 'package:flutter/material.dart';

/// Informazioni e consigli per ogni categoria di pianto (donateacry-corpus).
class CryInfo {
  final String key;
  final String nameIt;
  final IconData icon;
  final Color color;
  final String description;
  final List<String> tips;

  const CryInfo({
    required this.key,
    required this.nameIt,
    required this.icon,
    required this.color,
    required this.description,
    required this.tips,
  });

  static const Map<String, CryInfo> all = {
    'hungry': CryInfo(
      key: 'hungry',
      nameIt: 'Fame',
      icon: Icons.restaurant,
      color: Color(0xFFE67E22),
      description:
          'Il pianto da fame è spesso ritmico e insistente, cresce se non si risponde. '
          'Può essere accompagnato da suzione delle mani e movimenti di ricerca del seno.',
      tips: [
        'Offri il seno o il biberon: la fame è la causa più comune di pianto.',
        'Osserva i segnali precoci: mani alla bocca, movimenti di suzione, testa che cerca.',
        'Se ha appena mangiato, potrebbe aver bisogno di succhiare per conforto (ciuccio).',
        'Nei primi mesi le poppate frequenti (anche ogni 2 ore) sono normali.',
      ],
    ),
    'tired': CryInfo(
      key: 'tired',
      nameIt: 'Stanchezza / Sonno',
      icon: Icons.bedtime,
      color: Color(0xFF8E44AD),
      description:
          'Il pianto da sonno è spesso lamentoso e intermittente, con sbadigli, '
          'sguardo perso e stropicciamento degli occhi.',
      tips: [
        'Porta il bambino in un ambiente calmo, con luci basse e pochi stimoli.',
        'Prova il rumore bianco (c\'è nella sezione Suoni di questa app).',
        'Fascialo o tienilo in braccio con un dondolio lento e regolare.',
        'Un neonato troppo stanco fa più fatica ad addormentarsi: anticipa i segnali di sonno.',
      ],
    ),
    'discomfort': CryInfo(
      key: 'discomfort',
      nameIt: 'Disagio',
      icon: Icons.sentiment_dissatisfied,
      color: Color(0xFF16A085),
      description:
          'Pianto lamentoso e irregolare: pannolino sporco, caldo/freddo, '
          'posizione scomoda o vestiti che danno fastidio.',
      tips: [
        'Controlla il pannolino e cambialo se necessario.',
        'Verifica la temperatura: tocca il petto o la nuca (non mani/piedi).',
        'Controlla etichette, elastici o pieghe dei vestiti che possono dare fastidio.',
        'Cambia posizione o prendilo in braccio: a volte basta il contatto.',
      ],
    ),
    'belly_pain': CryInfo(
      key: 'belly_pain',
      nameIt: 'Dolore al pancino',
      icon: Icons.healing,
      color: Color(0xFFC0392B),
      description:
          'Pianto acuto e intenso, spesso a crisi, con gambe flesse sulla pancia, '
          'pugni chiusi e viso arrossato. Tipico di coliche e gas.',
      tips: [
        'Massaggia il pancino in senso orario con movimenti delicati.',
        'Prova la "bicicletta": muovi delicatamente le gambine verso la pancia.',
        'Tienilo a pancia in giù sul tuo avambraccio (posizione anti-colica).',
        'Se il pianto è inconsolabile, con febbre o vomito, contatta il pediatra.',
      ],
    ),
    'burping': CryInfo(
      key: 'burping',
      nameIt: 'Aria / Ruttino',
      icon: Icons.air,
      color: Color(0xFF2980B9),
      description:
          'Disagio da aria ingerita durante la poppata: il bambino si agita, '
          'inarca la schiena e piange poco dopo aver mangiato.',
      tips: [
        'Tienilo in posizione verticale contro la spalla e dai colpetti delicati sulla schiena.',
        'Prova la posizione seduta sulle tue ginocchia, sostenendo mento e petto.',
        'Fai pause per il ruttino anche durante la poppata, non solo alla fine.',
        'Dopo la poppata tienilo verticale 10-15 minuti prima di sdraiarlo.',
      ],
    ),
  };

  static CryInfo byKey(String key) =>
      all[key] ??
      const CryInfo(
        key: 'unknown',
        nameIt: 'Non riconosciuto',
        icon: Icons.help_outline,
        color: Colors.grey,
        description: 'Non è stato possibile classificare questo pianto.',
        tips: ['Riprova avvicinando il telefono al bambino, in un ambiente silenzioso.'],
      );
}
