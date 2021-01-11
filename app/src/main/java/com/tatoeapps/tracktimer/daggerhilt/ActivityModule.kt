package com.tatoeapps.tracktimer.daggerhilt

import android.app.Application
import android.content.Context
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.tatoeapps.tracktimer.main.MainActivity
import com.tatoeapps.tracktimer.utils.PreferencesDataStore
import com.tatoeapps.tracktimer.utils.Utils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

//    @ActivityScoped
//    @Provides
//    @Named("mainActivity")
//    fun getMainActivityContext(@ActivityContext mainActivity: MainActivity) = mainActivity

    @ActivityScoped
    @Provides
    @Named("exoPlayer")
    fun getExoPlayerInstance(@ApplicationContext context: Context): SimpleExoPlayer {
        val myDefaultRenderersFactory =
            Utils.MyDefaultRendererFactory(
                context
            ).setEnableAudioTrackPlaybackParams(true)

        Timber.d("Activity module - exo player instance - the activity context")

        return SimpleExoPlayer.Builder(context, myDefaultRenderersFactory).build().apply { setSeekParameters(
            SeekParameters.EXACT) }
    }

    @ActivityScoped
    @Provides
    @Named("dataSourceFactory")
    fun getDataSourceFactoryInstance(
        @ApplicationContext context: Context,
        application: Application
    ): DataSource.Factory {
        Timber.d("Activity module - data source factory isntance")

        return DefaultDataSourceFactory(
            context,
            Util.getUserAgent(context, application.packageName)
        )
    }
}