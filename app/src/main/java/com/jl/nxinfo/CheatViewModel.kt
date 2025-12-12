package com.jl.nxinfo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CheatViewModel : ViewModel() {

    private val _cheatInfo = MutableLiveData<CheatInfo?>()
    val cheatInfo: LiveData<CheatInfo?> = _cheatInfo

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun setCheatInfo(info: CheatInfo) {
        _cheatInfo.value = info
    }

    fun clearCheatInfo() {
        _cheatInfo.value = null
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}
