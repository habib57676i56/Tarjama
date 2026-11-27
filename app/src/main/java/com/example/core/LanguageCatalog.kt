package com.example.core

data class AppLanguage(
    val code: String,
    val bcp47: String,
    val arabicName: String,
    val englishName: String,
    val flag: String,
    val isRtl: Boolean = false
)

enum class TranslationStyle(
    val id: String,
    val arabicLabel: String,
    val promptInstruction: String
) {
    NATURAL(
        id = "natural",
        arabicLabel = "فصحى طبيعية",
        promptInstruction = "Use clear, natural, modern standard phrasing suitable for live subtitles and everyday understanding."
    ),
    GULF(
        id = "gulf",
        arabicLabel = "لهجة خليجية",
        promptInstruction = "When translating into Arabic, use natural Gulf Arabic (خليجي) phrasing; when translating out of Arabic, preserve the exact tone naturally."
    ),
    EGYPTIAN(
        id = "egyptian",
        arabicLabel = "لهجة مصرية",
        promptInstruction = "When translating into Arabic, use natural Egyptian Arabic (مصري) phrasing; when translating out of Arabic, preserve the exact tone naturally."
    ),
    LEVANTINE(
        id = "levantine",
        arabicLabel = "لهجة شامية",
        promptInstruction = "When translating into Arabic, use natural Levantine Arabic (شامي) phrasing; when translating out of Arabic, preserve the exact tone naturally."
    ),
    PRECISE(
        id = "precise",
        arabicLabel = "دقيق وعلمي",
        promptInstruction = "Use highly accurate, formal, and terminologically faithful phrasing."
    )
}

object LanguageCatalog {
    val autoDetect = AppLanguage(
        code = "auto",
        bcp47 = "en-US",
        arabicName = "كشف تلقائي",
        englishName = "Auto Detect",
        flag = "✨"
    )

    val arabic = AppLanguage(
        code = "ar",
        bcp47 = "ar-SA",
        arabicName = "العربية",
        englishName = "Arabic",
        flag = "🇸🇦",
        isRtl = true
    )

    val english = AppLanguage(
        code = "en",
        bcp47 = "en-US",
        arabicName = "الإنجليزية",
        englishName = "English",
        flag = "🇺🇸"
    )

    val supportedLanguages: List<AppLanguage> = listOf(
        arabic,
        english,
        AppLanguage("fr", "fr-FR", "الفرنسية", "French", "🇫🇷"),
        AppLanguage("es", "es-ES", "الإسبانية", "Spanish", "🇪🇸"),
        AppLanguage("de", "de-DE", "الألمانية", "German", "🇩🇪"),
        AppLanguage("tr", "tr-TR", "التركية", "Turkish", "🇹🇷"),
        AppLanguage("ja", "ja-JP", "اليابانية", "Japanese", "🇯🇵"),
        AppLanguage("ko", "ko-KR", "الكورية", "Korean", "🇰🇷"),
        AppLanguage("zh", "zh-CN", "الصينية", "Chinese", "🇨🇳"),
        AppLanguage("ru", "ru-RU", "الروسية", "Russian", "🇷🇺"),
        AppLanguage("it", "it-IT", "الإيطالية", "Italian", "🇮🇹"),
        AppLanguage("pt", "pt-BR", "البرتغالية", "Portuguese", "🇧🇷"),
        AppLanguage("hi", "hi-IN", "الهندية", "Hindi", "🇮🇳"),
        AppLanguage("ur", "ur-PK", "الأردية", "Urdu", "🇵🇰", isRtl = true),
        AppLanguage("fa", "fa-IR", "الفارسية", "Persian", "🇮🇷", isRtl = true),
        AppLanguage("id", "id-ID", "الإندونيسية", "Indonesian", "🇮🇩"),
        AppLanguage("nl", "nl-NL", "الهولندية", "Dutch", "🇳🇱"),
        AppLanguage("pl", "pl-PL", "البولندية", "Polish", "🇵🇱"),
        AppLanguage("sv", "sv-SE", "السويدية", "Swedish", "🇸🇪"),
        AppLanguage("el", "el-GR", "اليونانية", "Greek", "🇬🇷"),
        AppLanguage("uk", "uk-UA", "الأوكرانية", "Ukrainian", "🇺🇦"),
        AppLanguage("vi", "vi-VN", "الفيتنامية", "Vietnamese", "🇻🇳"),
        AppLanguage("th", "th-TH", "التايلاندية", "Thai", "🇹🇭")
    )

    val sourceOptions: List<AppLanguage> = listOf(autoDetect) + supportedLanguages

    fun findByCode(code: String): AppLanguage {
        if (code == autoDetect.code) return autoDetect
        return supportedLanguages.firstOrNull { it.code == code } ?: english
    }
}
