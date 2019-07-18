import 'package:flutter/widgets.dart';
import 'package:fluttery_audio/src/_audio_player.dart';
import 'package:fluttery_audio/src/_audio_player_widgets.dart';
import 'package:logging/logging.dart';
import 'package:meta/meta.dart';
import 'package:dio/dio.dart';

final _log = new Logger('AudioPlaylist');

class Radi8erPlaylist {
  Radi8erPlaylist() {
    songs = List<Song>();
  }
  List<Song> songs;
  static Radi8erPlaylist fromResponse(Response response) {
    Iterable l = response.data['data'];
    print(l.toString());
    return Radi8erPlaylist()..songs = l.map((model) => Song.fromJson(model)).toList();
  }
}

class Song {
  String artUrl;
  String artFullUrl;
  String artMediumUrl;
  String artist;
  String songTitle;
  String artThumbNailUrl;
  int artistId;
  String contribute;
  int id;
  String merch;
  bool seed;
  String url;
  String website;
  String albumName;

  static Song fromJson(json) {
    return Song()
      ..albumName = json["album"]
      ..artFullUrl = json["art_full"]
      ..artMediumUrl = json["art_mid"]
      ..artThumbNailUrl = json["art_thumb"]
      ..artUrl = json["art_url"]
      ..artist = json["artist"]
      ..artistId = json["artist_id"]
      ..songTitle = json["title"]
      ..contribute = json["contribute"]
      ..id = json["id"]
      ..merch = json["merch"]
      ..seed = json["seed"]
      ..url = json["url"]
      ..website = json["website"];
  }
}

class AudioPlaylist extends StatefulWidget {
  final List<String> playlist;
  final int startPlayingFromIndex;
  final PlaybackState playbackState;
  final Function(BuildContext, Playlist, Widget child) playlistBuilder;
  final Widget child;
  final VoidCallback onSongEnd;
  final Radi8erPlaylist radi8erPlaylist;

  AudioPlaylist({
    this.playlist = const [],
    this.onSongEnd,
    this.startPlayingFromIndex = 0,
    this.playbackState = PlaybackState.paused,
    this.playlistBuilder,
    this.child,
    @required this.radi8erPlaylist,
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
    final song = widget.radi8erPlaylist.songs[widget.startPlayingFromIndex];
    _log.fine('Building with active index: ${widget.startPlayingFromIndex}');
    return new Audio(
      audioUrl: widget.playlist[widget.startPlayingFromIndex],
      albumName: song.albumName,
      artistName: song.artist,
      coverArtwork: song.artThumbNailUrl,
      title: song.songTitle,
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
