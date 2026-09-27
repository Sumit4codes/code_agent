package com.codeagent.core.files

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FilesModule {

    @Binds
    @Singleton
    abstract fun bindProjectFileSystem(impl: FileProjectFileSystem): ProjectFileSystem
}
