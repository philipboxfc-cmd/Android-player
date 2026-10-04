package app.fca.player.data.model

/**
 * «Вайб» плейлиста — настроение + цвет по умолчанию.
 * Цвет можно переопределить в самом плейлисте.
 */
enum class Vibe(val title: String, val emoji: String, val defaultColor: Long) {
    CHILL("Чилл", "🌊", 0xFF4FC3F7),
    ENERGY("Энергия", "⚡", 0xFFFF7043),
    FOCUS("Фокус", "🎯", 0xFF26A69A),
    NIGHT_DRIVE("Ночная поездка", "🌃", 0xFF8E24AA),
    PARTY("Вечеринка", "🎉", 0xFFFF4081),
    ROMANCE("Романтика", "🌹", 0xFFEC407A),
    MELANCHOLY("Меланхолия", "🌧", 0xFF7986CB),
    RAGE("Ярость", "🔥", 0xFFE53935),
    DREAMY("Мечты", "☁️", 0xFFB39DDB),
    SUNSET("Закат", "🌅", 0xFFFFA726),
    RETRO("Ретро", "📼", 0xFFFFCA28),
    DARK("Тьма", "🖤", 0xFF546E7A),
    WORKOUT("Тренировка", "💪", 0xFF00E676),
    SAD("Грусть", "💧", 0xFF5C6BC0),
    HAPPY("Радость", "☀️", 0xFFFFEE58),
    NATURE("Природа", "🌿", 0xFF66BB6A);

    companion object {
        /** Разбирает название вайба из JSON ИИ: допускаются имена enum, русские названия и близкие варианты. */
        fun parse(raw: String?): Vibe {
            if (raw.isNullOrBlank()) return CHILL
            val key = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
            entries.firstOrNull { it.name.lowercase() == key || it.title.lowercase() == raw.trim().lowercase() }
                ?.let { return it }
            return when {
                key.contains("chill") || key.contains("relax") || key.contains("calm") || key.contains("lofi") || key.contains("чил") -> CHILL
                key.contains("energ") || key.contains("hype") || key.contains("энерг") || key.contains("драйв") -> ENERGY
                key.contains("focus") || key.contains("study") || key.contains("work") && !key.contains("workout") || key.contains("фокус") || key.contains("учеб") -> FOCUS
                key.contains("night") || key.contains("drive") || key.contains("ноч") -> NIGHT_DRIVE
                key.contains("party") || key.contains("dance") || key.contains("club") || key.contains("танц") || key.contains("вечер") -> PARTY
                key.contains("roman") || key.contains("love") || key.contains("любов") || key.contains("роман") -> ROMANCE
                key.contains("melanch") || key.contains("меланх") -> MELANCHOLY
                key.contains("rage") || key.contains("angry") || key.contains("metal") || key.contains("agress") || key.contains("злост") || key.contains("ярост") -> RAGE
                key.contains("dream") || key.contains("ambient") || key.contains("мечт") -> DREAMY
                key.contains("sunset") || key.contains("summer") || key.contains("закат") || key.contains("лет") -> SUNSET
                key.contains("retro") || key.contains("old") || key.contains("80") || key.contains("ретро") -> RETRO
                key.contains("dark") || key.contains("goth") || key.contains("тьм") || key.contains("тёмн") || key.contains("темн") -> DARK
                key.contains("workout") || key.contains("gym") || key.contains("sport") || key.contains("трен") || key.contains("спорт") -> WORKOUT
                key.contains("sad") || key.contains("cry") || key.contains("груст") || key.contains("печал") -> SAD
                key.contains("happy") || key.contains("joy") || key.contains("fun") || key.contains("радост") || key.contains("весел") -> HAPPY
                key.contains("nature") || key.contains("forest") || key.contains("природ") -> NATURE
                else -> CHILL
            }
        }
    }
}
