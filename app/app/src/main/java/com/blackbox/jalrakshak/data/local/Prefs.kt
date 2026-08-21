package com.blackbox.jalrakshak.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.blackbox.jalrakshak.core.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Prefs — do chhoti par zaroori cheezein yaad rakhta hai:
 *   1. user ne kaunsa gaon chuna
 *   2. user ne kaunsi bhasha chuni
 *
 * KYUN DataStore, SharedPreferences nahi: DataStore Flow deta hai (badalte hi UI update)
 * aur disk write background thread pe karta hai. SharedPreferences ka `apply()` bhi
 * kabhi-kabhi main thread block kar deta hai — purane phones pe wo jam dikhta hai.
 */
private val Context.dataStore by preferencesDataStore(name = "jalrakshak_prefs")

class Prefs(private val context: Context) {

    private object Keys {
        val VILLAGE_ID = intPreferencesKey("village_id")
        val VILLAGE_NAME = stringPreferencesKey("village_name")
        val LANG = stringPreferencesKey("lang")
        val TOKEN_SYNCED = booleanPreferencesKey("token_synced")
    }

    /** Chuna hua gaon. null = pehli baar app khuli hai -> village picker dikhega. */
    val villageId: Flow<Int?> = context.dataStore.data.map { it[Keys.VILLAGE_ID] }

    val villageName: Flow<String?> = context.dataStore.data.map { it[Keys.VILLAGE_NAME] }

    /**
     * Bhasha. Default HINDI.
     * KYUN Hindi default: app Assam ke gaon walon ke liye hai. English default rakhna
     * unke liye ek extra rukavat hai; jo English chahte hain wo toggle dabana jaante hain.
     */
    val lang: Flow<Lang> = context.dataStore.data.map {
        if (it[Keys.LANG] == "en") Lang.EN else Lang.HI
    }

    suspend fun setVillage(id: Int, name: String) {
        context.dataStore.edit {
            it[Keys.VILLAGE_ID] = id
            it[Keys.VILLAGE_NAME] = name
            // Gaon badla => FCM token dobara register karna hoga, warna purane gaon ke
            // alert aate rahenge. Ye flag Repository ko batata hai ki sync karna hai.
            it[Keys.TOKEN_SYNCED] = false
        }
    }

    suspend fun setLang(lang: Lang) {
        context.dataStore.edit { it[Keys.LANG] = if (lang == Lang.EN) "en" else "hi" }
    }

    suspend fun setTokenSynced(synced: Boolean) {
        context.dataStore.edit { it[Keys.TOKEN_SYNCED] = synced }
    }
}
