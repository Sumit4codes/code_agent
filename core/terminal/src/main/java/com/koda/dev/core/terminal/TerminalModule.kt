package com.koda.dev.core.terminal

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TerminalModule {

    @Provides
    @Singleton
    fun provideAlpineBootstrapManager(
        @ApplicationContext context: Context
    ): AlpineBootstrapManager = AlpineBootstrapManager(context)

    @Provides
    @Singleton
    fun provideUbuntuBootstrapManager(
        @ApplicationContext context: Context
    ): UbuntuBootstrapManager = UbuntuBootstrapManager(context)

    @Provides
    @Singleton
    fun provideNativeBinaryManager(
        @ApplicationContext context: Context,
        alpineBootstrapManager: AlpineBootstrapManager,
        ubuntuBootstrapManager: UbuntuBootstrapManager
    ): NativeBinaryManager = NativeBinaryManager(context, alpineBootstrapManager, ubuntuBootstrapManager)

    @Provides
    @Singleton
    fun providePosixTerminalExecutor(
        @ApplicationContext context: Context,
        nativeBinaryManager: NativeBinaryManager,
        alpineBootstrapManager: AlpineBootstrapManager,
        ubuntuBootstrapManager: UbuntuBootstrapManager
    ): PosixTerminalExecutor = PosixTerminalExecutor(context, nativeBinaryManager, alpineBootstrapManager, ubuntuBootstrapManager)

    @Provides
    @Singleton
    fun provideTerminalExecutor(impl: PosixTerminalExecutor): TerminalExecutor = impl

    @Provides
    @Singleton
    fun provideManagedTaskManager(
        posixTerminalExecutor: PosixTerminalExecutor
    ): ManagedTaskManager = ManagedTaskManager(posixTerminalExecutor)

    @Provides
    @Singleton
    fun provideTaskManager(impl: ManagedTaskManager): TaskManager = impl

    @Provides
    @Singleton
    fun provideTermuxSessionManager(
        @ApplicationContext context: Context,
        nativeBinaryManager: NativeBinaryManager,
        alpineBootstrapManager: AlpineBootstrapManager,
        ubuntuBootstrapManager: UbuntuBootstrapManager
    ): TermuxSessionManager = TermuxSessionManager(context, nativeBinaryManager, alpineBootstrapManager, ubuntuBootstrapManager)
}
