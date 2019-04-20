package io.fluttery.flutteryaudio;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutteryAudioPlugin
 */
public class FlutteryAudioPlugin implements MethodCallHandler {
    private static final String TAG = "FlutteryAudioPlugin";

    private static final Pattern METHOD_NAME_MATCH = Pattern.compile("audioplayer/([^/]+)/([^/]+)");
    private static final Pattern VISUALIZER_METHOD_NAME_MATCH = Pattern.compile("audiovisualizer/([^/]+)");

    private static MethodChannel channel;
    private static MethodChannel visualizerChannel;
    private Registrar registrar;
    private MediaSessionCompat mediaSessionCompat;
    private PlaybackStateCompat.Builder playbackStateBuilder;
    private MediaMetadataCompat.Builder mediaMetaBuilder;
    private String audioUrl = "";
    private String audioTitle = "";
    private String audioMediaCoverUrl = "";
    private String audioArtist = "";
    private boolean isDurationSet = false;
    private AudioPlayer player; // TODO: support multiple players.

    public FlutteryAudioPlugin(Registrar registrar) {
        this.playbackStateBuilder = new PlaybackStateCompat.Builder();
        this.mediaMetaBuilder = new MediaMetadataCompat.Builder();

        this.registrar = registrar;
        final MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setWakeMode(registrar.activeContext(), PowerManager.PARTIAL_WAKE_LOCK);

        WifiManager.WifiLock wifiLock = ((WifiManager) registrar.activity()
                .getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE))

                .createWifiLock(WifiManager.WIFI_MODE_FULL, "fluttery_audio");

        //mediaSessionCompat.

        player = new AudioPlayer(mediaPlayer, wifiLock);

        player.addListener(new AudioPlayer.Listener() {

            private int lastKnownPosition = 0;

            @Override
            public void onAudioLoading() {
                isDurationSet = false;
                setMediaStateLoading();
                Log.d(TAG, "Android -> Flutter: onAudioLoading()");
                channel.invokeMethod("onAudioLoading", null);
            }

            @Override
            public void onBufferingUpdate(int percent) {
                Log.d(TAG, "Android -> Flutter: onBufferingUpdate()");
                channel.invokeMethod("onBufferingUpdate", null);
            }

            @Override
            public void onAudioReady() {

                setMediaMetaDataLength(player.audioLength());

                long length = player.audioLength();

                Log.d(TAG, "Android -> Flutter: onAudioReady() Length: " + length);
                Map<String, Object> args = new HashMap<>();
                args.put("audioLength", length);
                channel.invokeMethod("onAudioReady", args);
            }

            @Override
            public void onPlayerPlaying() {
                setMediaStatePlaying(0);
                Log.d(TAG, "Android -> Flutter: onPlayerPlaying()");
                channel.invokeMethod("onPlayerPlaying", null);
            }

            @Override
            public void onPlayerPlaybackUpdate(int position, int audioLength) {
                lastKnownPosition = position;
                setMediaStatePlaying(position);
                setMediaMetaDataLength(audioLength);
//        Log.d(TAG, "Android -> Flutter: onPlayerPlaybackUpdate()");
                Map<String, Object> args = new HashMap<>();
                args.put("position", position);
                args.put("audioLength", audioLength);
                channel.invokeMethod("onPlayerPlaybackUpdate", args);
            }

            @Override
            public void onPlayerPaused() {
                setMediaStatePaused(lastKnownPosition);
                Log.d(TAG, "Android -> Flutter: onPlayerPaused()");
                channel.invokeMethod("onPlayerPaused", null);
            }

            @Override
            public void onPlayerStopped() {
                setMediaStateStopped(lastKnownPosition);
                Log.d(TAG, "Android -> Flutter: onPlayerStopped()");
                channel.invokeMethod("onPlayerStopped", null);
            }

            @Override
            public void onPlayerCompleted() {
                setMediaStateStopped(lastKnownPosition);
                Log.d(TAG, "Android -> Flutter: onPlayerCompleted()");
                channel.invokeMethod("onPlayerCompleted", null);
            }

            @Override
            public void onSeekStarted() {
                Log.d(TAG, "Android -> Flutter: onSeekStarted()");
                channel.invokeMethod("onSeekStarted", null);
            }

            @Override
            public void onSeekCompleted() {
                Log.d(TAG, "Android -> Flutter: onSeekCompleted()");

                // We send the new seek position over the channel with the
                // onSeekCompleted call because clients will likely need to
                // know immediately after seeking what the position is. If we
                // don't send that information with this call then a client will
                // have to call back and ask, and due to the asynchronous nature
                // of channels, there will be a noticeable time gap between when
                // seeking ends and when clients are able to synchronize with the
                // new playback position which will lead to visual artifacts in the UI.
                Map<String, Object> args = new HashMap<>();
                args.put("position", player.playbackPosition());

                channel.invokeMethod("onSeekCompleted", args);
            }
        });


        initializeMediaSessionCompat(player);
    }

    private void setMediaStatePaused(long position) {
        PlaybackStateCompat playbackStateCompat = this.playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_PAUSED, position, 1.0f)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_FAST_FORWARD |
                                PlaybackStateCompat.ACTION_REWIND
                ).build();
        mediaSessionCompat.setPlaybackState(playbackStateCompat);
    }

    private void setMediaStateStopped(long position) {
        PlaybackStateCompat playbackStateCompat = this.playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_STOPPED, position, 1.0f)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_FAST_FORWARD |
                                PlaybackStateCompat.ACTION_REWIND
                ).build();
        mediaSessionCompat.setPlaybackState(playbackStateCompat);
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        channel = new MethodChannel(registrar.messenger(), "fluttery_audio");
        channel.setMethodCallHandler(new FlutteryAudioPlugin(registrar));

        visualizerChannel = new MethodChannel(registrar.messenger(), "fluttery_audio_visualizer");
        visualizerChannel.setMethodCallHandler(new FlutteryAudioVisualizerPlugin());
    }

    private void setMediaMetaDataLength(int audioLength) {
        if (audioLength > 0 && !isDurationSet) {
            MediaMetadataCompat mediaMetadataCompat = this.mediaMetaBuilder
                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, audioMediaCoverUrl)
                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, audioMediaCoverUrl)
                    .putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, audioArtist)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, audioTitle)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, audioLength)
                    .build();

            mediaSessionCompat.setMetadata(mediaMetadataCompat);
            isDurationSet = true;
        }
    }

    private void initializeMediaSessionCompat(final AudioPlayer player) {

        MediaSessionCompat.Callback mediaCallback = new MediaSessionCompat.Callback() {

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                MediaButtonReceiver.handleIntent(mediaSessionCompat, mediaButtonEvent);
                return super.onMediaButtonEvent(mediaButtonEvent);
            }

            @Override
            public void onPrepare() {
                super.onPrepare();
            }

            @Override
            public void onPlay() {
                super.onPlay();
                player.play();
            }

            @Override
            public void onPause() {
                super.onPause();
                player.pause();
            }

            @Override
            public void onFastForward() {
                super.onFastForward();
                player.seek(player.playbackPosition() + 10000);
            }

            @Override
            public void onRewind() {
                super.onRewind();
                player.seek(player.playbackPosition() - 10000);
            }

            @Override
            public void onStop() {
                super.onStop();
                player.stop();
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
                player.seek((int) pos);
            }
        };

        PlaybackStateCompat playbackStateCompat = this.playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .build();

        mediaSessionCompat = new MediaSessionCompat(registrar.activeContext(), "fluttery_audio");
        mediaSessionCompat.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSessionCompat.setPlaybackState(playbackStateCompat);
        mediaSessionCompat.setCallback(mediaCallback);
        mediaSessionCompat.setActive(true);
    }

    private void makeNotification(
    ) {


        // Get the session's metadata
        MediaControllerCompat controller = mediaSessionCompat.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = mediaMetadata.getDescription();


        Notification notification = new NotificationCompat.Builder(registrar.activeContext(), "fluttery_audio")
                // Show controls on lock screen even when user hides sensitive content.
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_pause, "Pause",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(registrar.activeContext(),
                                PlaybackStateCompat.ACTION_PLAY_PAUSE)))
                // Add media control buttons that invoke intents in your media service
//                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent) // #0
//                .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)  // #1
//                .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)     // #2
                // Apply the media style template
                .setStyle(new MediaStyle()
                        .setShowActionsInCompactView(0 /* #1: pause button */)
                        .setMediaSession(mediaSessionCompat.getSessionToken())
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(registrar.activeContext(),
                                PlaybackStateCompat.ACTION_STOP))
                )
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription())
                .setLargeIcon(description.getIconBitmap())
                .setContentIntent(controller.getSessionActivity())
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(registrar.activeContext(),
                        PlaybackStateCompat.ACTION_STOP))
                .setColor(ContextCompat.getColor(registrar.activeContext(), android.R.color.background_dark))
                .build();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(registrar.activeContext());
        notificationManager.notify(1, notification);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        Log.d(TAG, "Flutter -> Android: " + call.method);
        try {
            AudioPlayerCall playerCall = parseMethodName(call.method);
            Log.d(TAG, playerCall.toString());

            // TODO: account for player ID

            switch (playerCall.command) {
                case "load":
                    Log.d(TAG, "Loading new audio.");
                    String audioUrl = call.argument("audioUrl");
                    String audioTitle = call.argument("audioTitle");
                    String audioMediaCoverUrl = call.argument("audioMediaCoverUrl");
                    String audioArtist = call.argument("audioArtist");
                    player.load(audioUrl, audioTitle, audioMediaCoverUrl, audioArtist);
                    this.setMediaMetaData(audioUrl, audioTitle, audioMediaCoverUrl, audioArtist);
                    try {
                        makeNotification();
                    } catch (Exception e) {
                        Log.e(TAG, "onMethodCall load", e);
                    }

                    break;
                case "play":
                    Log.d(TAG, "Playing audio");
                    player.play();
                    break;
                case "pause":
                    Log.d(TAG, "Pausing audio");
                    player.pause();
                    break;
                case "stop":
                    Log.d(TAG, "Stopping audio");
                    player.stop();
                    break;
                case "seek":
                    Log.d(TAG, "Seeking audio");
                    int seekPositionInMillis = call.argument("seekPosition");
                    player.seek(seekPositionInMillis);
                    break;
            }

            result.success(null);
        } catch (IllegalArgumentException e) {
            result.notImplemented();
        }
    }

    private void setMediaStatePlaying(long position) {
        PlaybackStateCompat playbackStateCompat = this.playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_PLAYING, position, 1.0f)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_FAST_FORWARD |
                                PlaybackStateCompat.ACTION_REWIND
                ).build();
        mediaSessionCompat.setPlaybackState(playbackStateCompat);
    }

    private void setMediaMetaData(String audioUrl, String audioTitle, String audioMediaCoverUrl, String audioArtist) {

        this.audioUrl = audioUrl;
        this.audioTitle = audioTitle;
        this.audioMediaCoverUrl = audioMediaCoverUrl;
        this.audioArtist = audioArtist;

        MediaMetadataCompat mediaMetadataCompat = this.mediaMetaBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, audioMediaCoverUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, audioMediaCoverUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, audioArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, audioTitle)
                .build();

        mediaSessionCompat.setMetadata(mediaMetadataCompat);
    }

    private void setMediaStateLoading() {
        PlaybackStateCompat playbackStateCompat = this.playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_BUFFERING, 0, 1.0f)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_FAST_FORWARD |
                                PlaybackStateCompat.ACTION_REWIND
                ).build();
        mediaSessionCompat.setPlaybackState(playbackStateCompat);
    }

    private AudioPlayerCall parseMethodName(@NonNull String methodName) {
        Matcher matcher = METHOD_NAME_MATCH.matcher(methodName);

        if (matcher.matches()) {
            String playerId = matcher.group(1);
            String command = matcher.group(2);
            return new AudioPlayerCall(playerId, command);
        } else {
            Log.d(TAG, "Match not found");
            throw new IllegalArgumentException("Invalid audio player message: " + methodName);
        }
    }

    private static class AudioPlayerCall {
        public final String playerId;
        public final String command;

        private AudioPlayerCall(@NonNull String playerId, @NonNull String command) {
            this.playerId = playerId;
            this.command = command;
        }

        @Override
        public String toString() {
            return String.format("AudioPlayerCall - Player ID: %s, Command: %s", playerId, command);
        }
    }

    private static class FlutteryAudioVisualizerPlugin implements MethodCallHandler {

        private AudioVisualizer visualizer = new AudioVisualizer();

        @Override
        public void onMethodCall(MethodCall call, Result result) {
//      Log.d(TAG, "Flutter -> Android: " + call.method);
            try {
                AudioVisualizerPlayerCall playerCall = parseMethodName(call.method);
                Log.d(TAG, playerCall.toString());

                switch (playerCall.command) {
                    case "activate_visualizer":
                        Log.d(TAG, "Activating visualizer");
                        if (visualizer.isActive()) {
                            Log.d(TAG, "Visualizer is already active. Ignoring.");
                            return;
                        }

                        // TODO: support media player specification for visualizer
                        // TODO: support requested sample rate and buffer size
                        // TODO: support selection of FFT vs waveform
                        visualizer.activate(new Visualizer.OnDataCaptureListener() {
                            @Override
                            public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                                Map<String, Object> args = new HashMap<>();
                                args.put("waveform", waveform);

                                visualizerChannel.invokeMethod("onWaveformVisualization", args);
                            }

                            @Override
                            public void onFftDataCapture(Visualizer visualizer, byte[] sharedFft, int samplingRate) {
                                byte[] fft = Arrays.copyOf(sharedFft, sharedFft.length);

                                Map<String, Object> args = new HashMap<>();
                                args.put("fft", fft);

                                visualizerChannel.invokeMethod("onFftVisualization", args);
                            }
                        });
                        break;
                    case "deactivate_visualizer":
                        Log.d(TAG, "Deactivating visualizer");
                        visualizer.deactivate();
                        break;
                }

                result.success(null);
            } catch (IllegalArgumentException e) {
                result.notImplemented();
            }
        }

        private AudioVisualizerPlayerCall parseMethodName(@NonNull String methodName) {
            Matcher matcher = VISUALIZER_METHOD_NAME_MATCH.matcher(methodName);

            if (matcher.matches()) {
                String command = matcher.group(1);
                return new AudioVisualizerPlayerCall(command);
            } else {
                Log.d(TAG, "Match not found");
                throw new IllegalArgumentException("Invalid audio visualizer message: " + methodName);
            }
        }

        private static class AudioVisualizerPlayerCall {
            public final String command;

            private AudioVisualizerPlayerCall(@NonNull String command) {
                this.command = command;
            }

            @Override
            public String toString() {
                return String.format("AudioVisualizerPlayerCall - Command: %s", command);
            }
        }
    }
}
