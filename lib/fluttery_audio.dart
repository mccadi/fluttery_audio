import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:fluttery_audio/src/_audio_player.dart';
import 'package:fluttery_audio/src/_audio_visualizer.dart';

export 'src/_audio_item.dart';
export 'src/_audio_player.dart';
export 'src/_audio_player_widgets.dart';
export 'src/_audio_visualizer.dart';
export 'src/_playlist.dart';
export 'src/_visualizer.dart';

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

class FlutteryAudio {
  static const MethodChannel _channel = const MethodChannel('fluttery_audio');

  static const MethodChannel _visualizerChannel = const MethodChannel('fluttery_audio_visualizer');

  static AudioPlayer audioPlayer() {
    return new AudioPlayer(
      playerId: 'demo_player',
      channel: _channel,
    );
  }

  static AudioVisualizer audioVisualizer() {
    return new AudioVisualizer(
      channel: _visualizerChannel,
    );
  }
}
