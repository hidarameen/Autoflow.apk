package com.autoflow.app.engine

/**
 * Per-execution scratch space. Variables written by SetVariable / ExtractRegex /
 * ReadClipboard live here and are readable as `{{name}}` by every later step, which is what
 * lets one rule pull a code out of a message and paste it somewhere else.
 */
class RunContext(val event: TriggerEvent) {

    private val variables = mutableMapOf<String, String>()

    fun set(name: String, value: String) {
        if (name.isNotBlank()) variables[name.trim()] = value
    }

    fun get(name: String): String? = variables[name]

    fun snapshot(): Map<String, String> = variables.toMap()
}
