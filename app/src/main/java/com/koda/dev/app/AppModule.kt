package com.koda.dev.app

import com.koda.dev.core.ai.AiProvider
import com.koda.dev.core.ai.ChatRequest
import com.koda.dev.core.ai.ChatStreamEvent
import com.koda.dev.core.ai.FakeAiProvider
import com.koda.dev.core.ai.ModelInfo
import com.koda.dev.core.ai.OpenAiCompatibleProvider
import com.koda.dev.core.data.SecureKeyStore
import com.koda.dev.core.data.SettingsRepository
import com.koda.dev.core.model.ProviderType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.sse.EventSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicAiProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient,
    private val sseFactory: EventSource.Factory
) : AiProvider {

    private suspend fun getDelegate(): AiProvider = withContext(Dispatchers.IO) {
        val config = settingsRepository.getActiveProvider() ?: return@withContext FakeAiProvider()
        val apiKey = settingsRepository.getApiKey(config.id) ?: ""
        when (config.providerType) {
            ProviderType.OPENAI_COMPATIBLE, ProviderType.ANTIROUTE -> OpenAiCompatibleProvider(
                baseUrl = config.baseUrl,
                apiKey = apiKey,
                client = client,
                sseFactory = sseFactory
            )
            ProviderType.ANTHROPIC -> FakeAiProvider()
        }
    }

    override suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent> = withContext(Dispatchers.IO) {
        getDelegate().streamChat(request)
    }

    override suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        getDelegate().listModels()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAiProvider(dynamicAiProvider: DynamicAiProvider): AiProvider = dynamicAiProvider

    @Provides
    fun provideIoDispatcher(): kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
}
