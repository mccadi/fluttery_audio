import 'package:flutter/widgets.dart';
import 'package:fluttery_audio/src/_audio_player.dart';
import 'package:fluttery_audio/src/_audio_player_widgets.dart';
import 'package:logging/logging.dart';
import 'package:meta/meta.dart';

final _log = new Logger('AudioPlaylist');

class AudioPlaylist extends StatefulWidget {
  final List<String> playlist;
  final int startPlayingFromIndex;
  final PlaybackState playbackState;
  final Function(BuildContext, Playlist, Widget child) playlistBuilder;
  final Widget child;
  final VoidCallback onSongEnd;

  AudioPlaylist({
    this.playlist = const [],
    this.onSongEnd,
    this.startPlayingFromIndex = 0,
    this.playbackState = PlaybackState.paused,
    this.playlistBuilder,
    this.child,
  });

  @override
  _AudioPlaylistState createState() => new _AudioPlaylistState();
}

class _AudioPlaylistState extends State<AudioPlaylist> with Playlist {
  static Playlist of(BuildContext context) {
    return context.ancestorStateOfType(new TypeMatcher<_AudioPlaylistState>()) as Playlist;
  }

  AudioPlayerState _prevState;
  AudioPlayer _audioPlayer;

  @override
  AudioPlayer get audioPlayer => _audioPlayer;

  @override
  int get activeIndex {
    return widget.startPlayingFromIndex;
  }

  @override
  Widget build(BuildContext context) {
    _log.fine('Building with active index: ${widget.startPlayingFromIndex}');
    return new Audio(
      audioUrl: widget.playlist[widget.startPlayingFromIndex],
      playbackState: widget.playbackState,
      callMe: [
        WatchableAudioProperties.audioPlayerState,
      ],
      buildMe: [
        WatchableAudioProperties.audioPlayerState,
      ],
      playerCallback: (BuildContext context, AudioPlayer player) {
        if (_prevState != player.state) {
          if (player.state == AudioPlayerState.completed) {
            _log.fine('Reached end of audio. Trying to play next clip.');
            // Playback has completed. Go to next song.
            widget.onSongEnd();
          }

          _prevState = player.state;
        }
      },
      playerBuilder: (BuildContext context, AudioPlayer player, Widget child) {
        _audioPlayer = player;

        return new _InheritedPlaylist(
          activeIndex: activeIndex,
          child: widget.playlistBuilder != null ? widget.playlistBuilder(context, this, widget.child) : widget.child,
        );
      },
    );
  }
}

class _InheritedPlaylist extends InheritedWidget {
  static _InheritedPlaylist of(BuildContext context) {
    return context.inheritFromWidgetOfExactType(_InheritedPlaylist) as _InheritedPlaylist;
  }

  final int activeIndex;
  final Widget child;

  _InheritedPlaylist({
    @required this.activeIndex,
    this.child,
  }) : super(child: child);

  @override
  bool updateShouldNotify(_InheritedPlaylist oldWidget) {
    return oldWidget.activeIndex != activeIndex;
  }
}

class AudioPlaylistComponent extends StatefulWidget {
  final Function(BuildContext, Playlist, Widget child) playlistBuilder;
  final Widget child;

  AudioPlaylistComponent({
    this.playlistBuilder,
    this.child,
  });

  @override
  _AudioPlaylistComponentState createState() => new _AudioPlaylistComponentState();
}

class _AudioPlaylistComponentState extends State<AudioPlaylistComponent> {
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _InheritedPlaylist.of(context);
  }

  @override
  Widget build(BuildContext context) {
    return widget.playlistBuilder != null
        ? widget.playlistBuilder(
            context,
            _AudioPlaylistState.of(context),
            widget.child,
          )
        : widget.child;
  }
}

abstract class Playlist {
  AudioPlayer get audioPlayer;
  int get activeIndex;
}
