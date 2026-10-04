package com.rikkahub.deepseeklocal.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rikkahub.deepseeklocal.data.repository.LogRepository
import com.rikkahub.deepseeklocal.domain.model.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Streams and filters logs for the Logs screen. */
@HiltViewModel
class LogsViewModel @Inject constructor(private val repo: LogRepository) : ViewModel() {
    val filter = MutableStateFlow("")
    val level = MutableStateFlow<String?>(null)

    val logs: StateFlow<List<LogEntry>> = combine(repo.observe(), filter, level) { list, q, lv ->
        list.filter { entry ->
            (lv == null || entry.level.name == lv) &&
                (q.isBlank() || entry.message.contains(q, ignoreCase = true) || entry.tag.contains(q, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clear() = viewModelScope.launch { repo.clear() }
    fun rotate() = viewModelScope.launch { repo.rotate(7) }
}
