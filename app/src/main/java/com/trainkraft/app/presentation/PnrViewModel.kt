package com.trainkraft.app.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.data.PnrApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PnrViewModel(
    application: Application,
    private val fetchCaptchaFn: suspend () -> Result<Bitmap> = { PnrApi.fetchCaptcha() },
    private val refreshCaptchaFn: suspend () -> Result<Bitmap> = { PnrApi.refreshCaptcha() },
    private val queryPnrFn: suspend (String, String) -> Result<PnrApi.PnrResult> = { p, c -> PnrApi.queryPnr(p, c) },
    private val resetSessionFn: () -> Unit = { PnrApi.resetSession() },
) : AndroidViewModel(application) {

    enum class Step { INPUT, CAPTCHA, RESULT }

    private val _step = MutableStateFlow(Step.INPUT)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _captchaBitmap = MutableStateFlow<Bitmap?>(null)
    val captchaBitmap: StateFlow<Bitmap?> = _captchaBitmap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pnrResult = MutableStateFlow<PnrApi.PnrResult?>(null)
    val pnrResult: StateFlow<PnrApi.PnrResult?> = _pnrResult.asStateFlow()

    private var currentPnr: String = ""

    fun loadCaptcha() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = fetchCaptchaFn()
            result
                .onSuccess { bitmap ->
                    _captchaBitmap.value = bitmap
                    _step.value = Step.CAPTCHA
                }
                .onFailure { e ->
                    _error.value = "Failed to load captcha: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = refreshCaptchaFn()
            result
                .onSuccess { bitmap ->
                    _captchaBitmap.value = bitmap
                }
                .onFailure { e ->
                    _error.value = "Failed to refresh captcha: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    fun submitPnr(pnr: String, captchaAnswer: String) {
        val trimmed = pnr.trim()
        if (trimmed.length != 10 || !trimmed.all { it.isDigit() }) {
            _error.value = "PNR must be exactly 10 digits"
            return
        }
        currentPnr = trimmed
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = queryPnrFn(trimmed, captchaAnswer)
            result
                .onSuccess { pnrData ->
                    _pnrResult.value = pnrData
                    _step.value = Step.RESULT
                }
                .onFailure { e ->
                    if (e is PnrApi.CaptchaMismatchException) {
                        _error.value = "Captcha didn't match. Refreshing..."
                        refreshCaptcha()
                    } else {
                        _error.value = e.message ?: "Something went wrong"
                    }
                }
            _isLoading.value = false
        }
    }

    fun goBack() {
        when (_step.value) {
            Step.CAPTCHA -> {
                _step.value = Step.INPUT
                _captchaBitmap.value = null
                _error.value = null
            }
            Step.RESULT -> {
                _step.value = Step.INPUT
                _pnrResult.value = null
                _error.value = null
            }
            Step.INPUT -> { /* already at root */ }
        }
    }

    fun reset() {
        _step.value = Step.INPUT
        _captchaBitmap.value = null
        _pnrResult.value = null
        _error.value = null
        _isLoading.value = false
        currentPnr = ""
        resetSessionFn()
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PnrViewModel(application) as T
        }
    }
}
