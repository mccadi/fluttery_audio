class AudioItem {
  final String url;
  final String title;
  final String author;
  final String artworkUrl;

  AudioItem(this.url,
      [this.title = "", this.author = "", this.artworkUrl = ""]);
}
