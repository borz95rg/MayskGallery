package com.maysk.gallery.ai

import com.maysk.gallery.data.MediaItem
import java.util.Locale

/**
 * Категории «AI Gallery». Модель ML Kit возвращает английские метки,
 * поэтому для русского поиска здесь есть словарь основ слов.
 */
enum class AiCategory(
    val title: String,
    val emoji: String,
    val labels: Set<String>,
    val ruStems: List<String>,
) {
    PEOPLE(
        "Люди", "📷",
        setOf("person", "selfie", "smile", "face", "facial expression", "beard", "glasses", "hair", "skin", "man", "woman", "girl", "boy"),
        listOf("человек", "люди", "людей", "лиц", "селфи", "мам", "пап", "улыб", "девушк", "парен", "друз", "женщин", "мужчин"),
    ),
    KIDS(
        "Дети", "👶",
        setOf("baby", "toddler", "child", "kid", "infant"),
        listOf("ребен", "ребён", "дет", "малыш", "девоч", "мальч"),
    ),
    ANIMALS(
        "Животные", "🐕",
        setOf("dog", "cat", "pet", "animal", "bird", "horse", "fish", "puppy", "kitten", "mammal", "fur", "snout", "whiskers", "cow", "rabbit"),
        listOf("собак", "пёс", "пес", "кош", "кот", "животн", "птиц", "лошад", "рыб", "щен", "котен", "котён", "питом"),
    ),
    CARS(
        "Машины", "🚗",
        setOf("car", "vehicle", "automotive", "wheel", "tire", "sports car", "truck", "motorcycle", "bicycle", "van", "bus", "supercar"),
        listOf("машин", "авто", "мотоцикл", "грузов", "велосипед", "автобус", "транспорт"),
    ),
    HOUSES(
        "Дома", "🏠",
        setOf("house", "building", "architecture", "home", "facade", "skyscraper", "tower", "castle", "roof", "window", "city"),
        listOf("дом", "здани", "архитект", "замок", "город", "небоскр"),
    ),
    NATURE(
        "Природа", "🌄",
        setOf("mountain", "sky", "tree", "forest", "lake", "sea", "beach", "sunset", "sunrise", "cloud", "plant", "nature", "landscape", "river", "grass", "snow", "ocean", "sand", "hill", "waterfall", "winter"),
        listOf("природ", "гор", "неб", "дерев", "лес", "озер", "озёр", "мор", "пляж", "закат", "рассвет", "облак", "пейзаж", "рек", "снег", "океан", "зим", "водопад"),
    ),
    DOCS(
        "Документы", "📄",
        setOf("text", "paper", "document", "font", "handwriting", "receipt", "poster", "book", "paper product", "screenshot", "number"),
        listOf("документ", "текст", "бумаг", "чек", "книг", "скриншот"),
    ),
    FOOD(
        "Еда", "🍔",
        setOf("food", "dish", "meal", "cuisine", "fruit", "vegetable", "dessert", "drink", "pizza", "cake", "coffee", "tableware", "baked goods"),
        listOf("еда", "еды", "блюд", "фрукт", "овощ", "десерт", "торт", "пицц", "кофе", "напит"),
    ),
    FLOWERS(
        "Цветы", "🌸",
        setOf("flower", "petal", "rose", "bouquet", "blossom", "flowering plant"),
        listOf("цвет", "роз", "букет", "лепест"),
    ),
    PARTY(
        "Праздники", "🎉",
        setOf("party", "birthday", "celebrating", "event", "fireworks", "balloon", "wedding", "fun", "ceremony"),
        listOf("праздник", "день рожд", "свадьб", "салют", "вечеринк", "торжеств"),
    ),
    TRAVEL(
        "Путешествия", "✈",
        setOf("travel", "tourism", "vacation", "airplane", "aircraft", "landmark", "hotel", "airline", "airport", "tourist attraction"),
        listOf("путешеств", "отпуск", "самолет", "самолёт", "отель", "туризм", "поездк");
    );

    fun matches(itemLabels: List<String>): Boolean =
        itemLabels.any { it.lowercase(Locale.ROOT) in labels }
}

object AiSearch {

    /** Слово начинается с основы и отличается от неё только окончанием. */
    private fun stemMatches(token: String, stem: String): Boolean {
        if (!token.startsWith(stem)) return false
        val extra = if (stem.length >= 5) 5 else 3
        return token.length <= stem.length + extra
    }

    /** Определяет категории, упомянутые в запросе (по-русски или по-английски). */
    fun categoriesIn(query: String): Pair<Set<AiCategory>, List<String>> {
        val tokens = query.lowercase(Locale("ru"))
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 2 }
        val cats = LinkedHashSet<AiCategory>()
        val rest = ArrayList<String>()
        for (t in tokens) {
            val hit = AiCategory.values().firstOrNull { c ->
                t in c.labels || c.ruStems.any { stem -> stemMatches(t, stem) }
            }
            if (hit != null) cats += hit else rest += t
        }
        return cats to rest
    }

    fun search(
        query: String,
        items: List<MediaItem>,
        labels: Map<String, List<String>>,
    ): List<MediaItem> {
        val q = query.trim()
        if (q.isEmpty()) return items
        val (cats, words) = categoriesIn(q)
        return if (cats.isNotEmpty()) {
            items.filter { item ->
                val l = labels[item.key].orEmpty()
                cats.all { it.matches(l) }
            }
        } else {
            // запрос не похож на категорию — ищем в названии файла и альбома
            items.filter { item ->
                val hay = (item.name + " " + item.bucket).lowercase(Locale("ru"))
                words.any { it.length >= 3 && it in hay }
            }
        }
    }
}
