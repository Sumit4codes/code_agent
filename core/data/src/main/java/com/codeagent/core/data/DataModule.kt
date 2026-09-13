package com.codeagent.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `sessions` (
                    `id` TEXT NOT NULL,
                    `projectId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `messages` (
                    `id` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `toolCallsJson` TEXT,
                    `toolCallId` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages`(`sessionId`)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `pending_changes` (
                    `id` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `filePath` TEXT NOT NULL,
                    `changeType` TEXT NOT NULL,
                    `originalContent` TEXT,
                    `proposedContent` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_changes_sessionId` ON `pending_changes`(`sessionId`)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `provider_configs` (
                    `id` TEXT NOT NULL,
                    `providerType` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `baseUrl` TEXT NOT NULL,
                    `temperature` REAL NOT NULL,
                    `maxTokens` INTEGER NOT NULL,
                    `systemPrompt` TEXT,
                    `isDefault` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "codeagent.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun providePendingChangeDao(db: AppDatabase): PendingChangeDao = db.pendingChangeDao()

    @Provides
    fun provideProviderConfigDao(db: AppDatabase): ProviderConfigDao = db.providerConfigDao()

    @Provides
    @Singleton
    fun provideApiKeyStorage(store: SecureKeyStore): ApiKeyStorage = store
}
