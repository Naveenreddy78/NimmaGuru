package com.mindmatrix.nimmaguru

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GuruViewModel : ViewModel() {
    private val repository = FirebaseRepository()

    private val _gurus = MutableStateFlow<List<Guru>>(emptyList())
    val gurus: StateFlow<List<Guru>> = _gurus

    val gurusList : List<Guru> = gurus.value

    init {
        fetchGurus()
    }

    private fun fetchGurus() {
        viewModelScope.launch {
            Log.d("NimmaGuru","fetchGurus")
            val result = repository.fetchGurus()
            Log.d("NimmaGuru","fetchGurus $result")

            _gurus.value = result
        }
    }
    public fun getGurus() : List<Guru>{
        Log.d("NimmaGuru","getGurus $gurusList")

        return gurusList
    }
}