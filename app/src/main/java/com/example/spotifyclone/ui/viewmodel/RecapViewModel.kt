package com.example.spotifyclone.ui.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyclone.data.db.CrankrRecapData
import com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper
import com.example.spotifyclone.service.share.RecapShareCardExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel untuk mengelola state Crankr Recap / Noise Tape dan ekspor poster 9:16.
 */
class RecapViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = ListeningHistoryDatabaseHelper.getInstance(application)

    private val _recapData = MutableStateFlow<CrankrRecapData?>(null)
    val recapData: StateFlow<CrankrRecapData?> = _recapData.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    init {
        loadRecap()
    }

    fun loadRecap() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val data = dbHelper.getRecapData()
                _recapData.value = data
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setCardIndex(index: Int) {
        _currentCardIndex.value = index.coerceIn(0, 3)
    }

    fun nextCard() {
        if (_currentCardIndex.value < 3) {
            _currentCardIndex.value += 1
        }
    }

    fun prevCard() {
        if (_currentCardIndex.value > 0) {
            _currentCardIndex.value -= 1
        }
    }

    fun shareToInstagramStory(context: Context) {
        val recap = _recapData.value ?: return
        if (_isExporting.value) return

        viewModelScope.launch {
            _isExporting.value = true
            try {
                Toast.makeText(context, "Menyiapkan poster Instagram Story...", Toast.LENGTH_SHORT).show()
                val bitmap = RecapShareCardExporter.generateRecapStoryBitmap(context, recap)
                val uri = RecapShareCardExporter.saveRecapToCache(context, bitmap)
                if (uri != null) {
                    withContext(Dispatchers.Main) {
                        RecapShareCardExporter.shareToInstagramStory(context, uri)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal membuat gambar poster", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Terjadi kesalahan saat mengekspor: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun shareGeneral(context: Context) {
        val recap = _recapData.value ?: return
        if (_isExporting.value) return

        viewModelScope.launch {
            _isExporting.value = true
            try {
                Toast.makeText(context, "Menyiapkan poster Crankr...", Toast.LENGTH_SHORT).show()
                val bitmap = RecapShareCardExporter.generateRecapStoryBitmap(context, recap)
                val uri = RecapShareCardExporter.saveRecapToCache(context, bitmap)
                if (uri != null) {
                    withContext(Dispatchers.Main) {
                        RecapShareCardExporter.shareToGeneral(context, uri)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal membuat gambar poster", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Terjadi kesalahan saat mengekspor: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isExporting.value = false
            }
        }
    }
}
