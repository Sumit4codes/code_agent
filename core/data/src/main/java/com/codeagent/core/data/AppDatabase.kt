package com.codeagent.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        ProjectEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        PendingChangeEntity::class,
        ProviderConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun providerConfigDao(): ProviderConfigDao
}

@androidx.room.Dao
interface ProjectDao {
    @androidx.room.Query("SELECT * FROM projects ORDER BY lastOpened DESC")
    fun getAll(): Flow<List<ProjectEntity>>

    @androidx.room.Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @androidx.room.Delete
    suspend fun delete(project: ProjectEntity)
}

@androidx.room.Dao
interface SessionDao {
    @androidx.room.Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: String): Flow<List<SessionEntity>>

    @androidx.room.Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @androidx.room.Delete
    suspend fun delete(session: SessionEntity)
}

@androidx.room.Dao
interface MessageDao {
    @androidx.room.Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: String): Flow<List<MessageEntity>>

    @androidx.room.Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionList(sessionId: String): List<MessageEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @androidx.room.Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

@androidx.room.Dao
interface PendingChangeDao {
    @androidx.room.Query("SELECT * FROM pending_changes WHERE sessionId = :sessionId AND status = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingBySession(sessionId: String): Flow<List<PendingChangeEntity>>

    @androidx.room.Query("SELECT * FROM pending_changes WHERE sessionId = :sessionId AND status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingBySessionList(sessionId: String): List<PendingChangeEntity>

    @androidx.room.Query("SELECT * FROM pending_changes WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getAllBySession(sessionId: String): Flow<List<PendingChangeEntity>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(change: PendingChangeEntity)

    @androidx.room.Query("UPDATE pending_changes SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@androidx.room.Dao
interface ProviderConfigDao {
    @androidx.room.Query("SELECT * FROM provider_configs")
    fun getAll(): Flow<List<ProviderConfigEntity>>

    @androidx.room.Query("SELECT * FROM provider_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ProviderConfigEntity?

    @androidx.room.Query("SELECT * FROM provider_configs WHERE isDefault = 1 LIMIT 1")
    fun getDefaultFlow(): Flow<ProviderConfigEntity?>

    @androidx.room.Query("SELECT * FROM provider_configs WHERE id = :id")
    suspend fun getById(id: String): ProviderConfigEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ProviderConfigEntity)

    @androidx.room.Delete
    suspend fun delete(config: ProviderConfigEntity)
}
