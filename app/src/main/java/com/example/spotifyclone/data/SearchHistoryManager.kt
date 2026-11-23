package com.example.spotifyclone.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class SearchHistoryManager(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "search_history.json")

    private val maxItems = 15

    suspend fun getSearchHistory(): List<String> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            val content = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val q = jsonArray.optString(i)?.trim().orEmpty()
                if (q.isNotBlank() && !list.any { it.equals(q, ignoreCase = true) }) {
                    list.add(q)
                }
            }
            list.take(maxItems)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addSearchQuery(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext getSearchHistory()
        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val result = current.take(maxItems)
        saveList(result)
        result
    }

    suspend fun removeSearchQuery(query: String): List<String> = withContext(Dispatchers.IO) {
        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        saveList(current)
        current
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        if (file.exists()) {
            file.delete()
        }
    }

    private suspend fun saveList(items: List<String>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            for (item in items) {
                jsonArray.put(item)
            }
            file.writeText(jsonArray.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
