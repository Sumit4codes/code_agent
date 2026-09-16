package com.codeagent.core.terminal

data class ParsedCommand(
    val executable: String,
    val rawArgs: List<String>,
    val args: List<String>,
    val flags: Set<Char>,
    val longFlags: Map<String, String?>
)

object CommandParser {

    fun splitTokens(commandLine: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var isEscaped = false

        for (ch in commandLine.trim()) {
            if (isEscaped) {
                current.append(ch)
                isEscaped = false
            } else if (ch == '\\' && !inSingleQuote) {
                isEscaped = true
            } else if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (ch.isWhitespace() && !inSingleQuote && !inDoubleQuote) {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    fun parse(commandLine: String): ParsedCommand? {
        val tokens = splitTokens(commandLine)
        if (tokens.isEmpty()) return null

        val executable = tokens[0]
        val rawArgs = tokens.drop(1)
        val args = mutableListOf<String>()
        val flags = mutableSetOf<Char>()
        val longFlags = mutableMapOf<String, String?>()

        var i = 1
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.startsWith("--") && token.length > 2) {
                val eqIdx = token.indexOf('=')
                if (eqIdx != -1) {
                    val key = token.substring(2, eqIdx)
                    val value = token.substring(eqIdx + 1)
                    longFlags[key] = value
                } else {
                    val key = token.substring(2)
                    longFlags[key] = null
                }
            } else if (token.startsWith("-") && token.length > 1 && !token.all { it.isDigit() || it == '-' }) {
                for (ch in token.drop(1)) {
                    flags.add(ch)
                }
            } else {
                args.add(token)
            }
            i++
        }

        return ParsedCommand(executable, rawArgs, args, flags, longFlags)
    }
}
