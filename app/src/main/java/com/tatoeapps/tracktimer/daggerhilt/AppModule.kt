package com.tatoeapps.tracktimer.daggerhilt

import android.content.Context
import com.tatoeapps.tracktimer.utils.PreferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object AppModule {

    @Singleton
    @Provides
    @Named("PreferencesDataStore")
    fun providePreferenceDataStore(@ApplicationContext context: Context):PreferencesDataStore{
        Timber.d("App module - preference data store")
      return PreferencesDataStore(context)
    }
}