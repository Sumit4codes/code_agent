package com.codeagent.core.common

import kotlinx.coroutines.Dispatchers

interface AppDispatchers {
    val io: kotlinx.coroutines.CoroutineDispatcher
    val main: kotlinx.coroutines.CoroutineDispatcher
    val default: kotlinx.coroutines.CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val io = Dispatchers.IO
    override val main = Dispatchers.Main
    override val default = Dispatchers.Default
}
