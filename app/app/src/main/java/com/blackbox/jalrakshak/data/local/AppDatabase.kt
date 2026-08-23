package com.blackbox.jalrakshak.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * AppDatabase — SQLite (Room) ka entry point.
 *
 * KYUN Room, plain SharedPreferences nahi: alerts ki LIST hai, shelters ki LIST hai,
 * aur unpe query karni hai (is gaon ke alerts, latest pehle). Prefs mein JSON string
 * thoos ke rakhna kaam to karta par har baar poora parse karna padta aur query nahi hoti.
 */
@Database(
    entities = [CachedVillage::class, CachedAlert::class, CachedShelter::class],
    // v2: CachedVillage mein hoursToDanger jud gaya (home screen ka "To rise" metric).
    // fallbackToDestructiveMigration set hai aur ye sirf CACHE hai — purana data ud
    // jaayega par sab kuch server se dobara aa jaata hai, to koi nuksan nahi.
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * get() — thread-safe singleton.
         * KYUN singleton: Room ka instance bhaari hai (SQLite connection kholta hai).
         * Do instance banne se "database is locked" errors aate hain.
         */
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jalrakshak.db",
                )
                    /**
                     * Schema badle to purana cache uda do, migration mat likho.
                     *
                     * KYUN ye yahan SAFE hai (aam taur pe ye bura practice hai):
                     * is DB mein sirf CACHE hai — har cheez server se dobara aa jaati hai.
                     * Koi user-generated data yahan nahi (SOS seedha server jaata hai,
                     * locally save nahi hota). Isliye kuch kho nahi sakta.
                     */
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
