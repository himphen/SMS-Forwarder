package com.himphen.playground.smsforwarder

data class TemplateValidation(
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = errorMessage == null
}

class InvalidTemplateException(message: String) : IllegalArgumentException(message)

object MessageFormatter {
    const val DEFAULT_TEMPLATE = "SMS from {from}:\n{body}"
    const val MAX_TELEGRAM_MESSAGE_LENGTH = 4096

    private const val TRUNCATION_SUFFIX = "\n[message truncated]"
    private val placeholderPattern = Regex("""\{([a-zA-Z][a-zA-Z0-9_]*)\}""")
    private val supportedPlaceholders = setOf("from", "body")

    fun validate(template: String): String? {
        if (template.isBlank()) {
            return "Message template cannot be empty."
        }

        val unknownPlaceholders = placeholderPattern
            .findAll(template)
            .map { it.groupValues[1] }
            .filterNot(supportedPlaceholders::contains)
            .distinct()
            .toList()

        if (unknownPlaceholders.isNotEmpty()) {
            return "Unsupported placeholder(s): ${unknownPlaceholders.joinToString(", ")}."
        }

        return null
    }

    fun format(template: String, from: String, body: String): String {
        validate(template)?.let { throw InvalidTemplateException(it) }

        val formatted = placeholderPattern.replace(template) { match ->
            when (match.groupValues[1]) {
                "from" -> from
                "body" -> body
                else -> match.value
            }
        }

        return if (formatted.length <= MAX_TELEGRAM_MESSAGE_LENGTH) {
            formatted
        } else {
            formatted.take(MAX_TELEGRAM_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length) +
                TRUNCATION_SUFFIX
        }
    }
}
