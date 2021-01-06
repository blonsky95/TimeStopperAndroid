package com.tatoeapps.tracktimer.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.createDataStore
import com.tatoeapps.tracktimer.R
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * FIND EVERYTHING RELATED TO PREFERENCES HERE
 */

class PreferencesDataStore (context: Context) {

    companion object {
        //Volatile means that writes to this field are immediately made visible to other threads
        // - hence if there is already an instance in other thread, it would use that one
        @Volatile private var INSTANCE:PreferencesDataStore? = null


        //Synchronized makes sure the code inside the block cant be run from two multiple threads at the same time
        //its like a room is the code, and each time a thread accesses it, it locks the room behind it,
        // so until it finishes its job in the room, no other thread can access
        fun getInstance(context: Context): PreferencesDataStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: createClassInstance(context).also { INSTANCE = it }
            }

        private fun createClassInstance(context: Context) =
            PreferencesDataStore(context)


        //the following 2 functions still rely on shared prefernces instead of DataStore because
        //they are required instantly, they trigger an activity change so they should block UI aka
        // can be run on the UI thread
        //If used datastore I would have to use deferred or wait for coroutine to return something,
        // but there is no line of code I want to run after this one, I want it to be in UI thread,
        //so using flow which uses the suspend and coroutines is not an option atm
        fun isUserFirstTimer(context: Context): Boolean {
            val sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_first_time_key), Context.MODE_PRIVATE
            )
            val defaultValue = true
            return sharedPref.getBoolean(
                context.getString(R.string.preference_first_time_key),
                defaultValue
            )
        }

        fun updateUserFirstTimer(context: Context, isFirstTime: Boolean) {
            val sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_first_time_key), Context.MODE_PRIVATE
            )
            with(sharedPref.edit()) {
                putBoolean(context.getString(R.string.preference_first_time_key), isFirstTime)
                apply()
            }
        }
    }

    private var dataStore: DataStore<Preferences> = context.createDataStore(
        name = "tracktimer_preferences"
    )
    private val daysBetweenPrompt = 14
    private val MILLIS_IN_ONE_DAY = 86400000
   val numberVideosTimingFree = 1

    val PREF_HAS_REVIEWED = preferencesKey<Boolean>("preference_has_reviewed")
    val PREF_IS_SUBSCRIBED = preferencesKey<Boolean>("preference_is_subscribed")
    val PREF_IS_TIMING_FREE_ACTIVE = preferencesKey<Boolean>("preference_is_timing_free_active")
    val PREF_IS_USER_FIRST_TIME = preferencesKey<Boolean>("preference_is_user_first_time")
    val PREF_DATE_RATING_PROMPT = preferencesKey<Long>("preference_date_rating_prompt")
    val PREF_DOY_LAST_TRIAL = preferencesKey<Int>("preference_doy_last_trial")
    val PREF_COUNT_FREE_TIMING_VIDEOS = preferencesKey<Int>("preference_count_free_timing_videos")

    suspend fun shouldShowRatingPrompt(currentSystemTimeInMillis: Long): Boolean {
        if (hasUserReviewedApp()) {
            return false
        }

        val preference = dataStore.data.first()
        val dateRatingPrompt = preference[PREF_DATE_RATING_PROMPT]?:-1L

        return if (dateRatingPrompt > 0 && currentSystemTimeInMillis >= dateRatingPrompt + (daysBetweenPrompt * MILLIS_IN_ONE_DAY)) {
            //14 or more days have passed since update or first install
            updateTimeOfLastPrompt(currentSystemTimeInMillis)
            true
        } else {
            if (dateRatingPrompt <= 0) {
                updateTimeOfLastPrompt(currentSystemTimeInMillis)
            }
            false
        }
    }

    private suspend fun updateTimeOfLastPrompt(systemTimeInMillis: Long) {
        dataStore.edit { prefs ->
            prefs[PREF_DATE_RATING_PROMPT] = systemTimeInMillis
        }
    }

    suspend fun hasUserReviewedApp(): Boolean {
        val userReviewedBooleanPreference = dataStore.data.first()
        return userReviewedBooleanPreference[PREF_HAS_REVIEWED]?:false
    }

    suspend fun updateHasUserReviewedApp(isSubscribed: Boolean) {
        dataStore.edit { prefs ->
            prefs[PREF_HAS_REVIEWED] = isSubscribed
        }
    }

    suspend fun isUserSubscribed(): Boolean {
        val preference = dataStore.data.first()
        return preference[PREF_IS_SUBSCRIBED]?:false
    }

    suspend fun updateIsUserSubscribed(isSubscribed: Boolean) {
        dataStore.edit { prefs ->
            prefs[PREF_IS_SUBSCRIBED] = isSubscribed
        }
    }

    suspend fun getIsTimingFreeActive(): Boolean {
        val preference = dataStore.data.first()
        return preference[PREF_IS_TIMING_FREE_ACTIVE]?:false
    }

    suspend fun updateIsTimingFreeActive(isActive: Boolean) {
        dataStore.edit { prefs ->
            prefs[PREF_IS_TIMING_FREE_ACTIVE] = isActive
        }
    }

    private suspend fun getPrefDayOfYearTrial(): Int {
        val preference = dataStore.data.first()
        return preference[PREF_DOY_LAST_TRIAL]?:-1

    }

    suspend fun updatePrefDayOfYearLastTiming(int: Int) {
        dataStore.edit {
            it[PREF_DOY_LAST_TRIAL]=int
        }
    }

    suspend fun getCountOfFreeDailyTiming(): Int {
        val preference = dataStore.data.first()
        return preference[PREF_COUNT_FREE_TIMING_VIDEOS]?:0
    }

    suspend fun addOneToCountOfFreeDailyTiming(
        countBeforeUpdate: Int,
        resetCounter: Boolean = false
    ) {

        dataStore.edit {
            it[PREF_COUNT_FREE_TIMING_VIDEOS]= if (resetCounter) {
                0
            } else {
                countBeforeUpdate+1
            }
        }
    }

    //these 2 functions are still done
    suspend fun isUserFirstTimer(context: Context): Boolean {
        val preference = dataStore.data.first()
        return preference[PREF_IS_USER_FIRST_TIME]?:true
    }

    suspend fun updateUserFirstTimer(isFirstTime: Boolean) {
        dataStore.edit {
            it[PREF_IS_USER_FIRST_TIME] = isFirstTime
        }
    }

    suspend fun canStartTimingTrial(): Boolean {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (dayOfYear == getPrefDayOfYearTrial()) {
            //so the user has done at least one video if the dates are matching
            if (getCountOfFreeDailyTiming() >= numberVideosTimingFree) {
                //too many videos for free trial, has expired
                //expired
                return false
            }
        } else {
            //its a different day from the last use, so reset count to 0, and return true later
            addOneToCountOfFreeDailyTiming(
                getCountOfFreeDailyTiming(),
                true
            )
        }
        return true
    }
}