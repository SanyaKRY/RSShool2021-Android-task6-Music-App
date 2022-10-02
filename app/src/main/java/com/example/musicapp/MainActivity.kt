package com.example.musicapp

import android.content.ComponentName
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.musicapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var binding: ActivityMainBinding

    private val myViewModel: MyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MyMediaPlaybackService::class.java),
            connectionCallbacks,
            null // optional Bundle
        )

        myViewModel.tracks.observe(this, Observer { track ->
            Glide.with(this).load(track.bitmapUri)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .override(800,800)
                .into(binding.imageViewBitmapOfTrack)
            binding.textViewTitleOfTrack.text = track.title
        })

        myViewModel.states.observe(this, Observer { state ->
            when (state) {
                PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                PlaybackStateCompat.STATE_PLAYING -> {
                    binding.buttonPlay.isEnabled = false
                    binding.buttonNext.isEnabled = true
                    binding.buttonPause.isEnabled = true
                    binding.buttonPrev.isEnabled = true
                    binding.buttonStop.isEnabled = true
                }
                PlaybackStateCompat.STATE_PAUSED -> {
                    binding.buttonPlay.isEnabled = true
                    binding.buttonNext.isEnabled = true
                    binding.buttonPause.isEnabled = false
                    binding.buttonPrev.isEnabled = true
                    binding.buttonStop.isEnabled = true
                }
                PlaybackStateCompat.STATE_STOPPED -> {
                    binding.buttonPlay.isEnabled = true
                    binding.buttonNext.isEnabled = true
                    binding.buttonPause.isEnabled = false
                    binding.buttonPrev.isEnabled = true
                    binding.buttonStop.isEnabled = false
                }
            }
        })
    }

    public override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    public override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata == null) {
                return
            }
            var title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            var artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            var displayIconUri =
                metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)
            var mediaUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
            var duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            var track = Track(title, artist, displayIconUri, mediaUri, duration.toInt())
            myViewModel.tracks.value = track
        }

        // когда плеер играет, состояние isPlaying true, кнопки 4 неактивны, изменилось состояние, только play
        // неактивно  становится, тоесть наоборот
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            if (state == null) {
                return
            }
            myViewModel.states.value = state.state
        }
    }

    public override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {

            // Get the token for the MediaSession
            mediaBrowser.sessionToken.also { token ->

                // Create a MediaControllerCompat
                val mediaController = MediaControllerCompat(
                    this@MainActivity, // Context
                    token
                )

                // Save the controller
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
            }

            // Finish building the UI
            buildTransportControls()
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
        }
    }

    fun buildTransportControls() {
        val mediaController = MediaControllerCompat.getMediaController(this@MainActivity)

        // Grab the view for the play/pause button
        binding.buttonPlay.apply {
            setOnClickListener {
                mediaController.transportControls.play()
            }
        }

        binding.buttonPause.apply {
            setOnClickListener {
                mediaController.transportControls.pause()
            }
        }

        binding.buttonStop.apply {
            setOnClickListener {
                mediaController.transportControls.stop()
            }
        }

        binding.buttonNext.apply {
            setOnClickListener {
                mediaController.transportControls.skipToNext()
            }
        }

        binding.buttonPrev.apply {
            setOnClickListener {
                mediaController.transportControls.skipToPrevious()
            }
        }

        // Display the initial state
        val metadata = mediaController.metadata
        val pbState = mediaController.playbackState

        // Register a Callback to stay in sync
        mediaController.registerCallback(controllerCallback)
    }
}