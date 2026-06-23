package com.superencrypter.model

enum class VirusTotalStatus {
    CLEAN,
    SUSPICIOUS,
    MALICIOUS,
    UNKNOWN,
    NOT_CHECKED;

    companion object {
        fun fromBackend(value: String?): VirusTotalStatus = when (value?.lowercase()) {
            "clean" -> CLEAN
            "suspicious" -> SUSPICIOUS
            "malicious" -> MALICIOUS
            "unknown" -> UNKNOWN
            else -> NOT_CHECKED
        }
    }
}
