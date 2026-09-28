package com.codeagent.app

import com.codeagent.core.ai.AiProvider
import com.codeagent.core.ai.ChatRequest
import com.codeagent.core.ai.ChatStreamEvent
import com.codeagent.core.ai.FakeAiProvider
import com.codeagent.core.ai.ModelInfo
import com.codeagent.core.ai.OpenAiCompatibleProvider
import com.codeagent.core.data.SecureKeyStore
import com.codeagent.core.data.SettingsRepository
import com.codeagent.core.model.ProviderType
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
            ProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(
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
