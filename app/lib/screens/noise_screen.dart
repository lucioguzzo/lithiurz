import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';

class _Sound {
  final String title;
  final String subtitle;
  final String asset;
  final IconData icon;
  const _Sound(this.title, this.subtitle, this.asset, this.icon);
}

class NoiseScreen extends StatefulWidget {
  const NoiseScreen({super.key});

  @override
  State<NoiseScreen> createState() => _NoiseScreenState();
}

class _NoiseScreenState extends State<NoiseScreen> {
  static const sounds = [
    _Sound('Rumore bianco', 'Come il phon: copre i rumori improvvisi',
        'sounds/white_noise.wav', Icons.blur_on),
    _Sound('Rumore rosa', 'Più morbido, simile alla pioggia',
        'sounds/pink_noise.wav', Icons.water_drop),
    _Sound('Battito cardiaco', 'Ricorda il suono nel grembo materno',
        'sounds/heartbeat.wav', Icons.favorite),
    _Sound('Shhh', 'Il classico "shhh" ritmico',
        'sounds/shush.wav', Icons.record_voice_over),
  ];

  final _player = AudioPlayer();
  int? _playing;
  double _volume = 0.8;

  @override
  void initState() {
    super.initState();
    _player.setReleaseMode(ReleaseMode.loop);
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  Future<void> _toggle(int i) async {
    if (_playing == i) {
      await _player.stop();
      setState(() => _playing = null);
    } else {
      await _player.stop();
      await _player.setVolume(_volume);
      await _player.play(AssetSource(sounds[i].asset));
      setState(() => _playing = i);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text('Suoni rilassanti',
              style: Theme.of(context).textTheme.headlineSmall),
        ),
        Expanded(
          child: ListView(
            children: [
              for (int i = 0; i < sounds.length; i++)
                Card(
                  margin:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                  child: ListTile(
                    leading: Icon(sounds[i].icon,
                        color: Theme.of(context).colorScheme.primary),
                    title: Text(sounds[i].title),
                    subtitle: Text(sounds[i].subtitle),
                    trailing: Icon(_playing == i
                        ? Icons.stop_circle
                        : Icons.play_circle_outline),
                    onTap: () => _toggle(i),
                  ),
                ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
          child: Row(
            children: [
              const Icon(Icons.volume_down),
              Expanded(
                child: Slider(
                  value: _volume,
                  onChanged: (v) {
                    setState(() => _volume = v);
                    _player.setVolume(v);
                  },
                ),
              ),
              const Icon(Icons.volume_up),
            ],
          ),
        ),
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 0, 16, 12),
          child: Text(
            'Tieni il volume moderato e il telefono ad almeno un metro dal bambino.',
            style: TextStyle(fontSize: 12, color: Colors.grey),
          ),
        ),
      ],
    );
  }
}
