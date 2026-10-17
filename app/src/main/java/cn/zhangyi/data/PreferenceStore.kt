package cn.zhangyi.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import cn.zhangyi.domain.Preferences
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
class PreferenceStore(context: Context) {
    private val store = context.settingsDataStore
    private val theme = stringPreferencesKey("theme")
    private val dynamic = booleanPreferencesKey("dynamic_color")
    private val currency = stringPreferencesKey("currency")
    val flow = store.data.map { Preferences(it[theme] ?: "SYSTEM", it[dynamic] ?: true, it[currency] ?: "CNY") }
    suspend fun write(p: Preferences) { store.edit { it[theme] = p.theme; it[dynamic] = p.dynamicColor; it[currency] = p.currency } }
}
