package com.example.musicapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.example.musicapp.repository.MusicRepository
import com.example.musicapp.util.Constants
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import javax.inject.Inject

class MyMediaPlaybackService @Inject constructor(): MediaBrowserServiceCompat() {

    init {
        Log.i("AppMusic", "class MyMediaPlaybackService, init{}")
    }

    private lateinit var exoPlayer: Player
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var musicRepository: MusicRepository
    private lateinit var metadataBuilder: MediaMetadataCompat.Builder

    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusRequested = false
    private var audioManager: AudioManager? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("AppMusic", "class MyMediaPlaybackService, onCreate()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            var notificationChannel: NotificationChannel =
                NotificationChannel(
                    "1992",
                    "NOTIFICATION_CHANNEL_NAME",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            var notificationManager: NotificationManager? =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            notificationManager?.createNotificationChannel(notificationChannel)

            var audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setAudioAttributes(audioAttributes)
                .build()
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        exoPlayer = ExoPlayer.Builder(this@MyMediaPlaybackService).build().apply {
            addListener(exoPlayerListener)
        }
        musicRepository = MusicRepository(applicationContext)
        metadataBuilder = MediaMetadataCompat.Builder()

        // "PlayerService" - просто tag для отладки
        // создаем MediaSessionCompat
        mediaSession = MediaSessionCompat(baseContext, "PlayerService").apply {

            // FLAG_HANDLES_MEDIA_BUTTONS - хотим получать события от аппаратных кнопок
            // (например, гарнитуры)
            // FLAG_HANDLES_TRANSPORT_CONTROLS - хотим получать события от кнопок
            // на окне блокировки
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // ...состояния плеера
            // Здесь мы указываем действия, которые собираемся обрабатывать в коллбэках.
            // Например, если мы не укажем ACTION_PAUSE,
            // то нажатие на паузу не вызовет onPause.
            // ACTION_PLAY_PAUSE обязателен, иначе не будет работать
            // управление с Android Wear!
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_STOP
                            or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            // Сообщаем новое состояние
//            setPlaybackState(stateBuilder.setState(
//                PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
//                1f
//            ).build())

            // отдаем наши коллбэки
            setCallback(callback)

            // Set the session's token so that client activities can communicate with it.
            setSessionToken(sessionToken)

            // Укажем activity, которую запустит система, если пользователь
            // заинтересуется подробностями данной сессии
            val appContext = applicationContext
            val activityIntent = Intent(appContext, MainActivity::class.java)
            setSessionActivity(PendingIntent.getActivity(appContext, 0, activityIntent, 0))

            val mediaButtonIntent = Intent(
                Intent.ACTION_MEDIA_BUTTON, null, appContext, MediaButtonReceiver::class.java
            )

            setMediaButtonReceiver(
                PendingIntent.getBroadcast(appContext, 0, mediaButtonIntent, 0)
            )
        }
    }

    private val callback = object : MediaSessionCompat.Callback() {

        private fun updateMetadataFromTrack(track: Track) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            metadataBuilder.putString(
                MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
                track.bitmapUri
            )
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, track.trackUri)
            metadataBuilder.putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                track.duration.toLong()
            )
            mediaSession?.setMetadata(metadataBuilder.build())
        }

        var currentState: Int = PlaybackStateCompat.STATE_STOPPED

        override fun onPlay() {

            // Заполняем данные о треке
            var track: Track = musicRepository.getCurrent()
            updateMetadataFromTrack(track)

            if (!audioFocusRequested) {
                audioFocusRequested = true

                var audioFocusResult: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusResult = audioManager!!.requestAudioFocus(audioFocusRequest!!)
                } else {
                    audioFocusResult = audioManager!!.requestAudioFocus(
                        audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                    )
                }
                if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    return
                }
            }

            // Указываем, что наше приложение теперь активный плеер и кнопки
            // на окне блокировки должны управлять именно нами
            mediaSession?.isActive = true

            // Сообщаем новое состояние
            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f
                ).build()
            )

            // Загружаем URL аудио-файла в ExoPlayer
            prepareToPlay(track.trackUri)

            currentState = PlaybackStateCompat.STATE_PLAYING
            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onPause() {
            Log.i("AppMusic", "class MyMediaPlaybackService, callback object, onPause()")

            // Останавливаем воспроизведение
            exoPlayer.playWhenReady = false

            // Сообщаем новое состояние
            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f
                ).build()
            )

            currentState = PlaybackStateCompat.STATE_PAUSED
            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onStop() {

            // Останавливаем воспроизведение
            exoPlayer.playWhenReady = false


            if (audioFocusRequested) {
                audioFocusRequested = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager!!.abandonAudioFocusRequest(audioFocusRequest!!)
                } else {
                    audioManager!!.abandonAudioFocus(audioFocusChangeListener)
                }
            }

            // Все, больше мы не "главный" плеер, уходим со сцены
            mediaSession?.isActive = false

            // Сообщаем новое состояние
            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_STOPPED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f
                ).build()
            )

            currentState = PlaybackStateCompat.STATE_STOPPED
            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onSkipToNext() {
            Log.i("AppMusic", "class MyMediaPlaybackService, callback object, onSkipToNext()")

            val track = musicRepository.getNext()
            updateMetadataFromTrack(track)

            // Останавливаем воспроизведение
//            exoPlayer.playWhenReady = true

            // Загружаем URL аудио-файла в ExoPlayer
            prepareToPlay(track.trackUri)

            // Сообщаем новое состояние
            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f
                ).build()
            )

            currentState = PlaybackStateCompat.STATE_SKIPPING_TO_NEXT
            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onSkipToPrevious() {
            Log.i("MyMediaPlaybackService", "callback object, onSkipToPrevious()")

            val track = musicRepository.getPrevious()
            updateMetadataFromTrack(track)

            // Останавливаем воспроизведение
//            exoPlayer.playWhenReady = true

            // Загружаем URL аудио-файла в ExoPlayer
            prepareToPlay(track.trackUri)

            // Сообщаем новое состояние
            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f
                ).build()
            )

            currentState = PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS
            refreshNotificationAndForegroundStatus(currentState)
        }

        private fun prepareToPlay(uriString: String?) {
            val uri = Uri.parse(uriString ?: "")
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            // Запускаем воспроизведение
            exoPlayer.playWhenReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ресурсы освобождать обязательно
        mediaSession?.release()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(Constants.MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {

        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        // Check if this is the root menu:
        if (Constants.MY_MEDIA_ROOT_ID == parentId) {
            // Build the MediaItem objects for the top level,
            // and put them in the mediaItems list...
        } else {
            // Examine the passed parentMediaId to see which submenu we're at,
            // and put the children of that menu in the mediaItems list...
        }
        result.sendResult(mediaItems)
    }

    private fun getNotification(playbackState: Int): Notification {
        val builder = MediaStyleHelper.from(this, mediaSession)
        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
        )

        if (playbackState == PlaybackStateCompat.STATE_PLAYING) builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
        ) else builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
        )

        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
        )

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
                .setMediaSession(mediaSession?.sessionToken)
        )
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.color = ContextCompat.getColor(
            this, R.color.purple_500
        )
        builder.setShowWhen(false)
        builder.priority = NotificationCompat.PRIORITY_HIGH
        builder.setOnlyAlertOnce(true)
        builder.setChannelId("1992")
        return builder.build()
    }

    private fun refreshNotificationAndForegroundStatus(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                Log.i("MyMediaPlaybackService", "refreshNotificationAndForegroundStatus()")
                startForeground(Constants.NOTIFICATION_ID, getNotification(playbackState))
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                NotificationManagerCompat.from(this).notify(
                    Constants.NOTIFICATION_ID,
                    getNotification(playbackState)
                )
                stopForeground(false)
            }
            else -> {
                stopForeground(true)
            }
        }
    }

    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener() { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> callback.onPlay()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> callback.onPause()
                else -> callback.onPause()
            }
    }

    private val exoPlayerListener = object : Player.Listener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                callback.onSkipToNext()
            }
        }
    }
}
