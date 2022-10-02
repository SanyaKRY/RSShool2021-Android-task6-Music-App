package com.example.musicapp.di

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import com.example.musicapp.MyMediaPlaybackService
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Component(modules = [MediaBrowserModule::class])
interface MediaBrowserCompatComponent {
    fun mediaBrowserCompat(): MediaBrowserCompat
}

@Module
class MediaBrowserModule(val context: Context) {

    @Provides
    @Singleton
    fun providesMediaBrowserCompat(
        context: Context,
        componentName: ComponentName,
        connectionCallback: MediaBrowserCompatConnectionCallback
    ): MediaBrowserCompat = MediaBrowserCompat(context, componentName, connectionCallback, null)

    @Provides
    @Singleton
    fun providesApplicationContext(): Context = context


    @Provides
    fun providesMediaBrowserServiceComponentName(context: Context): ComponentName =
        ComponentName(context, MyMediaPlaybackService::class.java)
}

class MediaBrowserCompatConnectionCallback @Inject constructor(): MediaBrowserCompat.ConnectionCallback() {

    override fun onConnected() {
//        // Get the token for the MediaSession
//        mediaBrowser.sessionToken.also { token ->
//            // Create a MediaControllerCompat
//            val mediaController = MediaControllerCompat(
//                this@MainActivity, // Context
//                token
//            )
//            // Save the controller
//            MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
//        }
//        // Finish building the UI
//        buildTransportControls()
    }

    override fun onConnectionSuspended() {
        // The Service has crashed. Disable transport controls until it automatically reconnects
    }

    override fun onConnectionFailed() {
        // The Service has refused our connection
    }
}