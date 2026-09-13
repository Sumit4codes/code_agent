package com.codeagent.core.files

class IgnoreRuleMatcher(
    private val rules: List<String>
) {
    private val compiledRules: List<CompiledRule> = rules.map { compileRule(it) }

    fun isIgnored(relativePath: String, isDirectory: Boolean): Boolean {
        for (rule in compiledRules) {
            if (rule.isDirOnly && !isDirectory) continue
            if (rule.pattern.matches(relativePath)) return true
        }
        return false
    }

    private data class CompiledRule(
        val pattern: Regex,
        val isDirOnly: Boolean
    )

    companion object {
        private fun compileRule(rule: String): CompiledRule {
            var pattern = rule.trim()
            if (pattern.startsWith("#") || pattern.isEmpty()) {
                return CompiledRule(Regex("(?!)"), isDirOnly = false)
            }

            val isDirOnly = pattern.endsWith("/")
            if (isDirOnly) pattern = pattern.dropLast(1)

            val anchored = pattern.startsWith("/")
            if (anchored) pattern = pattern.removePrefix("/")

            val sb = StringBuilder()
            sb.append("^")

            if (!anchored) sb.append("(?:.*/)?")

            // Split on ** and handle each segment
            val segments = pattern.split("**")
            for ((index, segment) in segments.withIndex()) {
                if (index > 0) {
                    // ** matches zero or more path components
                    // Strip leading / from next segment since ** absorbs it
                    sb.append("(?:.*/)?")
                }
                // Remove leading / from segment (absorbed by preceding **)
                val cleaned = if (index > 0 && segment.startsWith("/")) {
                    segment.removePrefix("/")
                } else {
                    segment
                }
                for (ch in cleaned) {
                    when (ch) {
                        '*' -> sb.append("[^/]*")
                        '?' -> sb.append("[^/]")
                        '.' -> sb.append("\\.")
                        '(' -> sb.append("\\(")
                        ')' -> sb.append("\\)")
                        '{' -> sb.append("\\{")
                        '}' -> sb.append("\\}")
                        '+' -> sb.append("\\+")
                        '^' -> sb.append("\\^")
                        '$' -> sb.append("\\$")
                        '|' -> sb.append("\\|")
                        '\\' -> sb.append("\\\\")
                        else -> sb.append(ch)
                    }
                }
            }

            sb.append("$")
            return CompiledRule(Regex(sb.toString()), isDirOnly)
        }

        fun fromGitignore(content: String): IgnoreRuleMatcher {
            val rules = content.lines().filter {
                it.isNotBlank() && !it.startsWith("#")
            }
            return IgnoreRuleMatcher(rules)
        }
    }
}
