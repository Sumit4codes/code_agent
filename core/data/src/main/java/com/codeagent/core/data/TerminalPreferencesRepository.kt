package com.codeagent.core.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface TerminalPreferencesRepository {
    val fontSizeSpFlow: Flow<Int>
    fun getFontSizeSp(): Int
    suspend fun setFontSizeSp(sizeSp: Int)

    companion object {
        const val DEFAULT_FONT_SIZE_SP = 14
        const val MIN_FONT_SIZE_SP = 9
        const val MAX_FONT_SIZE_SP = 28
    }
}

@Singleton
class DefaultTerminalPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : TerminalPreferencesRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("terminal_preferences", Context.MODE_PRIVATE)
    }

    private val _fontSizeFlow = MutableStateFlow(loadFontSize())
    override val fontSizeSpFlow: Flow<Int> = _fontSizeFlow.asStateFlow()

    private fun loadFontSize(): Int {
        val stored = prefs.getInt(KEY_FONT_SIZE_SP, TerminalPreferencesRepository.DEFAULT_FONT_SIZE_SP)
        return stored.coerceIn(
            TerminalPreferencesRepository.MIN_FONT_SIZE_SP,
            TerminalPreferencesRepository.MAX_FONT_SIZE_SP
        )
    }

    override fun getFontSizeSp(): Int = _fontSizeFlow.value

    override suspend fun setFontSizeSp(sizeSp: Int) {
        val clamped = sizeSp.coerceIn(
            TerminalPreferencesRepository.MIN_FONT_SIZE_SP,
            TerminalPreferencesRepository.MAX_FONT_SIZE_SP
        )
        prefs.edit().putInt(KEY_FONT_SIZE_SP, clamped).apply()
        _fontSizeFlow.value = clamped
    }

    companion object {
        private const val KEY_FONT_SIZE_SP = "terminal_font_size_sp"
    }
}
