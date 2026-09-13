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

    private suspend fun getDelegate(): AiProvider {
        val config = settingsRepository.getActiveProvider() ?: return FakeAiProvider()
        val apiKey = settingsRepository.getApiKey(config.id) ?: return FakeAiProvider()
        return when (config.providerType) {
            ProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(
                baseUrl = config.baseUrl,
                apiKey = apiKey,
                client = client,
                sseFactory = sseFactory
            )
            ProviderType.ANTHROPIC -> FakeAiProvider()
        }
    }

    override suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent> {
        return getDelegate().streamChat(request)
    }

    override suspend fun listModels(): List<ModelInfo> {
        return getDelegate().listModels()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAiProvider(dynamicAiProvider: DynamicAiProvider): AiProvider = dynamicAiProvider
}
