package com.example.nonoti.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nonoti.data.BoxRepository
import com.example.nonoti.notifications.BoxNotification
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BoxViewModel @Inject constructor(private val repository: BoxRepository) : ViewModel() {
    val notifications: StateFlow<List<BoxNotification>> = repository.notifications.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}
