package com.example.spotifyclone.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider

/**
 * ExoPlayerDownloadsOpenHelper:
 * SQLiteOpenHelper lokal ("ExoPlayerDownloads.db") untuk menyimpan data unduhan
 * ExoPlayer DownloadManager secara persisten sehingga tidak hilang saat aplikasi ditutup.
 */
class ExoPlayerDownloadsOpenHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        // Skema tabel internal dikelola oleh ExoPlayer DefaultDownloadIndex dan VersionTable
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Dikelola otomatis oleh migrasi internal ExoPlayer
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Mencegah crash jika terjadi perbedaan versi database
    }

    companion object {
        const val DATABASE_NAME = "ExoPlayerDownloads.db"
        const val DATABASE_VERSION = 1
    }
}

/**
 * ExoPlayerDownloadsDatabaseProvider:
 * Implementasi resmi DatabaseProvider Media3 berbasis SQLite database lokal "ExoPlayerDownloads.db".
 */
@OptIn(UnstableApi::class)
class ExoPlayerDownloadsDatabaseProvider private constructor(context: Context) : DatabaseProvider {
    private val helper = ExoPlayerDownloadsOpenHelper(context.applicationContext)

    override fun getWritableDatabase(): SQLiteDatabase {
        return helper.writableDatabase
    }

    override fun getReadableDatabase(): SQLiteDatabase {
        return helper.readableDatabase
    }

    companion object {
        @Volatile
        private var instance: ExoPlayerDownloadsDatabaseProvider? = null

        fun getInstance(context: Context): ExoPlayerDownloadsDatabaseProvider {
            return instance ?: synchronized(this) {
                instance ?: ExoPlayerDownloadsDatabaseProvider(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
