package com.jl.nxinfo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RomInfoViewModel : ViewModel() {

    private val _romInfo = MutableLiveData<SwitchRomInfo?>()
    val romInfo: LiveData<SwitchRomInfo?> = _romInfo

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun setRomInfo(info: SwitchRomInfo) {
        _romInfo.value = info
    }

    fun clearRomInfo() {
        _romInfo.value = null
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}
