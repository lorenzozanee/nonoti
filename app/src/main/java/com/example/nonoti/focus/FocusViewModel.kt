package com.example.nonoti.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nonoti.data.FocusBlockRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

fun interface FocusSchedulePort {
    fun reconcile()

    fun cancel(block: DailyFocusBlock) = Unit
}

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val repository: FocusBlockRepository,
    private val schedule: FocusSchedulePort = FocusSchedulePort {},
) : ViewModel() {
    val blocks: StateFlow<List<DailyFocusBlock>> = repository.blocks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun save(block: DailyFocusBlock) {
        viewModelScope.launch {
            if (repository.save(block) == null) schedule.reconcile()
        }
    }

    fun delete(block: DailyFocusBlock) {
        viewModelScope.launch {
            repository.delete(block)
            schedule.cancel(block)
            schedule.reconcile()
        }
    }
}
