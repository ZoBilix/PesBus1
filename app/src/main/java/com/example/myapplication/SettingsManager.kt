package com.example.myapplication

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsManager {
    private val THEME_KEY = intPreferencesKey("theme")

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_SYSTEM = 2

    suspend fun saveTheme(context: Context, theme: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    fun getThemeFlow(context: Context): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[THEME_KEY] ?: THEME_SYSTEM
        }
    }

    // ✅ Применяем тему БЕЗ корутин
    fun applyTheme(theme: Int) {
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    suspend fun getTheme(context: Context): Int {
        return context.dataStore.data
            .map { preferences -> preferences[THEME_KEY] ?: THEME_SYSTEM }
            .first()
    }
}