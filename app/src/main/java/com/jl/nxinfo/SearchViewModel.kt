package com.jl.nxinfo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SearchViewModel : ViewModel() {

    private val _allGames = MutableLiveData<List<GameInfo>>(emptyList())
    val allGames: LiveData<List<GameInfo>> = _allGames

    private val _searchResults = MutableLiveData<List<GameInfo>>(emptyList())
    val searchResults: LiveData<List<GameInfo>> = _searchResults

    private val _searchQuery = MutableLiveData<String>("")
    val searchQuery: LiveData<String> = _searchQuery

    private val _isDatabaseLoaded = MutableLiveData<Boolean>(false)
    val isDatabaseLoaded: LiveData<Boolean> = _isDatabaseLoaded

    private val _isDownloading = MutableLiveData<Boolean>(false)
    val isDownloading: LiveData<Boolean> = _isDownloading

    private val _expandedPosition = MutableLiveData<Int>(-1)
    val expandedPosition: LiveData<Int> = _expandedPosition

    fun setAllGames(games: List<GameInfo>) {
        _allGames.value = games
    }

    fun setSearchResults(results: List<GameInfo>) {
        _searchResults.value = results
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDatabaseLoaded(loaded: Boolean) {
        _isDatabaseLoaded.value = loaded
    }

    fun setDownloading(downloading: Boolean) {
        _isDownloading.value = downloading
    }

    fun setExpandedPosition(position: Int) {
        _expandedPosition.value = position
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _expandedPosition.value = -1
    }
}
