package com.blackbox.jalrakshak.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * CacheDao — offline cache padhne/likhne ke saare operations.
 *
 * Flow return karte hain: DB badle to UI apne aap update ho jaata hai, manually
 * "dobara padho" bolne ki zaroorat nahi. Yahi wajah hai ki app pehle CACHE dikhati hai
 * (turant) aur network ka jawaab aane pe screen khud refresh ho jaati hai —
 * user ko kabhi khaali screen nahi dikhti.
 */
@Dao
interface CacheDao {

    // ---------------- village ----------------

    @Query("SELECT * FROM cached_village WHERE id = :villageId LIMIT 1")
    fun observeVillage(villageId: Int): Flow<CachedVillage?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVillage(village: CachedVillage)

    // ---------------- alerts ----------------

    @Query("SELECT * FROM cached_alert WHERE villageId = :villageId ORDER BY id DESC")
    fun observeAlerts(villageId: Int): Flow<List<CachedAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlerts(alerts: List<CachedAlert>)

    @Query("DELETE FROM cached_alert WHERE villageId = :villageId")
    suspend fun clearAlerts(villageId: Int)

    /**
     * replaceAlerts() — purane hata ke naye daalo, ek hi transaction mein.
     *
     * KYUN transaction: delete aur insert ke beech agar app band ho jaaye to citizen ke
     * paas ZERO alerts bachte — theek us waqt jab usse alert chahiye. Transaction se
     * ya dono hote hain ya koi nahi.
     *
     * KYUN replace (sirf insert nahi): officer alert delete kar sakta hai / server pe
     * list badal sakti hai. Sirf insert karte rehne se app mein aisa alert reh jaata
     * jo ab valid hi nahi.
     */
    @Transaction
    suspend fun replaceAlerts(villageId: Int, alerts: List<CachedAlert>) {
        clearAlerts(villageId)
        saveAlerts(alerts)
    }

    // ---------------- shelters ----------------

    @Query("SELECT * FROM cached_shelter WHERE villageId = :villageId ORDER BY id")
    fun observeShelters(villageId: Int): Flow<List<CachedShelter>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShelters(shelters: List<CachedShelter>)

    @Query("DELETE FROM cached_shelter WHERE villageId = :villageId")
    suspend fun clearShelters(villageId: Int)

    @Transaction
    suspend fun replaceShelters(villageId: Int, shelters: List<CachedShelter>) {
        clearShelters(villageId)
        saveShelters(shelters)
    }
}
