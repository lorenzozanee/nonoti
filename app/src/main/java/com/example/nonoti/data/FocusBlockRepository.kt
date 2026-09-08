package com.example.nonoti.data

import com.example.nonoti.focus.DailyFocusBlock
import com.example.nonoti.focus.FocusBlockError
import com.example.nonoti.focus.FocusBlockRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FocusBlockRepository @Inject constructor(private val dao: NonotiDao) {
    val blocks: Flow<List<DailyFocusBlock>> = dao.observeFocusBlocks().map { entities ->
        entities.map { DailyFocusBlock(it.startMinute, it.endMinute) }
    }

    suspend fun save(block: DailyFocusBlock): FocusBlockError? {
        val existing = dao.focusBlocks().map { DailyFocusBlock(it.startMinute, it.endMinute) }
        val result = FocusBlockRules.validate(block, existing)
        if (result == null) dao.upsertFocusBlock(FocusBlockEntity(block.startMinute, block.endMinute))
        return result
    }

    suspend fun delete(block: DailyFocusBlock) {
        dao.deleteFocusBlock(block.startMinute, block.endMinute)
    }
}
