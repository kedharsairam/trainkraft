package com.trainkraft.app.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Generic factory for AndroidViewModels that take only Application.
 * Usage: ViewModelFactory(application, ::MyViewModel)
 */
class ViewModelFactory(
    private val application: Application,
    private val creator: (Application) -> AndroidViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return creator(application) as T
    }
}
