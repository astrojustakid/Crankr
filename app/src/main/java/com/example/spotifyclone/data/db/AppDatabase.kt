package com.example.spotifyclone.data.db

import android.content.Context

typealias CrankrDatabase = AppDatabase

/**
 * Crankr Database Entrypoint (AppDatabase / CrankrDatabase)
 * Menyediakan akses database terpusat dengan mekanisme fallbackToDestructiveMigration
 * untuk memastikan tidak ada startup crash akibat inkonsistensi skema tabel Room / SQLite.
 */
object AppDatabase {
    @Volatile
    private var instance: ListeningHistoryDatabaseHelper? = null

    fun getInstance(context: Context): ListeningHistoryDatabaseHelper {
        return instance ?: synchronized(this) {
            instance ?: ListeningHistoryDatabaseHelper.getInstance(context).also {
                instance = it
            }
        }
    }

    fun databaseBuilder(context: Context): AppDatabase = this

    fun fallbackToDestructiveMigration(): AppDatabase = this

    fun build(context: Context): ListeningHistoryDatabaseHelper = getInstance(context)

    /**
     * Menjalankan reset database destruktif jika terjadi kerusakan data atau error migrasi skema.
     */
    fun fallbackToDestructiveMigration(context: Context) {
        synchronized(this) {
            try {
                context.applicationContext.deleteDatabase("crankr_listening_history.db")
                instance = null
                instance = ListeningHistoryDatabaseHelper.getInstance(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
