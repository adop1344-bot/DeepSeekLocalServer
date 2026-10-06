package com.rikkahub.deepseeklocal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rikkahub.deepseeklocal.data.local.prefs.AppSettings
import com.rikkahub.deepseeklocal.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes live settings to MainActivity for theme wiring. */
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    repo: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}
