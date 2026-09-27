package com.codeagent.core.terminal

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
    fun provideNativeBinaryManager(
        @ApplicationContext context: Context,
        alpineBootstrapManager: AlpineBootstrapManager
    ): NativeBinaryManager = NativeBinaryManager(context, alpineBootstrapManager)

    @Provides
    @Singleton
    fun providePosixTerminalExecutor(
        @ApplicationContext context: Context,
        nativeBinaryManager: NativeBinaryManager,
        alpineBootstrapManager: AlpineBootstrapManager
    ): PosixTerminalExecutor = PosixTerminalExecutor(context, nativeBinaryManager, alpineBootstrapManager)

    @Provides
    @Singleton
    fun provideTerminalExecutor(impl: PosixTerminalExecutor): TerminalExecutor = impl
}
