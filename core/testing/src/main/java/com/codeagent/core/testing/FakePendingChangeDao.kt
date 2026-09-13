package com.codeagent.core.testing

import com.codeagent.core.data.PendingChangeDao
import com.codeagent.core.data.PendingChangeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePendingChangeDao : PendingChangeDao {
    private val changes = MutableStateFlow<List<PendingChangeEntity>>(emptyList())

    override fun getPendingBySession(sessionId: String): Flow<List<PendingChangeEntity>> =
        changes.map { list ->
            list.filter { it.sessionId == sessionId && it.status == "PENDING" }
        }

    override suspend fun getPendingBySessionList(sessionId: String): List<PendingChangeEntity> =
        changes.value.filter { it.sessionId == sessionId && it.status == "PENDING" }

    override fun getAllBySession(sessionId: String): Flow<List<PendingChangeEntity>> =
        changes.map { list ->
            list.filter { it.sessionId == sessionId }
        }

    override suspend fun upsert(change: PendingChangeEntity) {
        val current = changes.value.toMutableList()
        val index = current.indexOfFirst { it.id == change.id }
        if (index >= 0) {
            current[index] = change
        } else {
            current.add(change)
        }
        changes.value = current
    }

    override suspend fun updateStatus(id: String, status: String) {
        val current = changes.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(status = status)
            changes.value = current
        }
    }
}
