package com.example.data.content

import com.example.R
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage

data class CardPairItem(
    val id: String,
    val targetText: String,
    val phonetic: String,
    val englishMeaning: String,
    val category: String
)

data class CalligraphyPromptItem(
    val id: String,
    val character: String,
    val phonetic: String,
    val meaning: String,
    val strokeHint: String
)

data class QuizQuestionItem(
    val id: String,
    val prompt: String,
    val targetSnippet: String,
    val options: List<String>,
    val correctIndex: Int,
    val grammarRule: String
)

data class VoiceChallengeItem(
    val id: String,
    val targetPhrase: String,
    val phonetic: String,
    val englishMeaning: String,
    val modeHint: String // "Repeat clearly" or "Answer in target language"
)

data class StoryScenarioSeed(
    val id: String,
    val language: TargetLanguage,
    val title: String,
    val setting: String,
    val drawableRes: Int,
    val openingNpcLine: String,
    val openingNpcPhonetic: String,
    val openingNpcEnglish: String,
    val initialChoices: List<StoryChoiceSeed>
)

data class StoryChoiceSeed(
    val targetText: String,
    val phonetic: String,
    val englishMeaning: String
)

data class PhonemeGuideItem(
    val id: String,
    val symbol: String,
    val phoneticCue: String,
    val articulationTip: String,
    val exampleWord: String,
    val examplePhonetic: String,
    val exampleMeaning: String
)

object CuratedContent {

    fun cardPairs(language: TargetLanguage, difficulty: DifficultyLevel): List<CardPairItem> {
        val all = when (language) {
            TargetLanguage.JAPANESE -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    CardPairItem("jp_b1", "ありがとう", "a-ri-ga-tou", "Thank you", "Greeting"),
                    CardPairItem("jp_b2", "みず", "mi-zu", "Water", "Daily"),
                    CardPairItem("jp_b3", "ねこ", "ne-ko", "Cat", "Nature"),
                    CardPairItem("jp_b4", "おはよう", "o-ha-you", "Good morning", "Greeting"),
                    CardPairItem("jp_b5", "たべる", "ta-be-ru", "To eat", "Action"),
                    CardPairItem("jp_b6", "ともだち", "to-mo-da-chi", "Friend", "People")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    CardPairItem("jp_i1", "駅 (えき)", "e-ki", "Train station", "Travel"),
                    CardPairItem("jp_i2", "美味しい", "o-i-shi-i", "Delicious", "Food"),
                    CardPairItem("jp_i3", "約束", "ya-ku-so-ku", "Promise", "Social"),
                    CardPairItem("jp_i4", "天気", "ten-ki", "Weather", "Nature"),
                    CardPairItem("jp_i5", "図書館", "to-sho-kan", "Library", "Place"),
                    CardPairItem("jp_i6", "手伝う", "te-tsu-da-u", "To help", "Action")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    CardPairItem("jp_a1", "一期一会", "i-chi-go i-chi-e", "Once-in-a-lifetime encounter", "Idiom"),
                    CardPairItem("jp_a2", "木漏れ日", "ko-mo-re-bi", "Sunlight through leaves", "Nuance"),
                    CardPairItem("jp_a3", "改善", "kai-zen", "Continuous improvement", "Concept"),
                    CardPairItem("jp_a4", "雰囲気", "fun-i-ki", "Atmosphere / vibe", "Abstract"),
                    CardPairItem("jp_a5", "丁寧", "tei-nei", "Polite & careful", "Manner"),
                    CardPairItem("jp_a6", "懐かしい", "na-tsu-ka-shi-i", "Nostalgic", "Emotion")
                )
            }
            TargetLanguage.SPANISH -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    CardPairItem("es_b1", "Buenos días", "BWEH-nos DEE-as", "Good morning", "Greeting"),
                    CardPairItem("es_b2", "Gracias", "GRAH-syas", "Thank you", "Polite"),
                    CardPairItem("es_b3", "Agua", "AH-gwah", "Water", "Daily"),
                    CardPairItem("es_b4", "Amigo", "ah-MEE-go", "Friend", "People"),
                    CardPairItem("es_b5", "Libro", "LEE-broh", "Book", "Study"),
                    CardPairItem("es_b6", "Caminar", "kah-mee-NAR", "To walk", "Action")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    CardPairItem("es_i1", "Estación", "es-tah-SYON", "Station", "Travel"),
                    CardPairItem("es_i2", "Mañana", "mah-NYAH-nah", "Tomorrow / Morning", "Time"),
                    CardPairItem("es_i3", "Comprender", "kom-pren-DER", "To understand", "Action"),
                    CardPairItem("es_i4", "Sabroso", "sah-BROH-so", "Tasty", "Food"),
                    CardPairItem("es_i5", "Recuerdo", "reh-KWER-do", "Memory / Souvenir", "Social"),
                    CardPairItem("es_i6", "Despacio", "des-PAH-syo", "Slowly", "Adverb")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    CardPairItem("es_a1", "Sobremesa", "so-breh-MEH-sah", "After-meal table chat", "Culture"),
                    CardPairItem("es_a2", "Madrugada", "mah-droo-GAH-dah", "Pre-dawn hours", "Time"),
                    CardPairItem("es_a3", "A propósito", "ah pro-POH-see-to", "By the way / On purpose", "Phrase"),
                    CardPairItem("es_a4", "Añoranza", "ah-nyo-RAHN-sah", "Deep longing", "Emotion"),
                    CardPairItem("es_a5", "Desafío", "de-sah-FEE-o", "Challenge", "Abstract"),
                    CardPairItem("es_a6", "Matiz", "mah-TEES", "Nuance / Shade", "Nuance")
                )
            }
            TargetLanguage.FRENCH -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    CardPairItem("fr_b1", "Bonjour", "bohn-ZHOOR", "Hello", "Greeting"),
                    CardPairItem("fr_b2", "Merci", "mehr-SEE", "Thank you", "Polite"),
                    CardPairItem("fr_b3", "Eau", "oh", "Water", "Daily"),
                    CardPairItem("fr_b4", "Ami", "ah-MEE", "Friend", "People"),
                    CardPairItem("fr_b5", "Pain", "pan", "Bread", "Food"),
                    CardPairItem("fr_b6", "Parler", "par-LAY", "To speak", "Action")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    CardPairItem("fr_i1", "Gare", "gahr", "Train station", "Travel"),
                    CardPairItem("fr_i2", "Délicieux", "day-lee-SYUH", "Delicious", "Food"),
                    CardPairItem("fr_i3", "Comprendre", "kohm-PRAHN-druh", "To understand", "Action"),
                    CardPairItem("fr_i4", "Rendez-vous", "rahn-day-VOO", "Appointment / Meetup", "Social"),
                    CardPairItem("fr_i5", "Aujourd'hui", "oh-zhoor-DWEE", "Today", "Time"),
                    CardPairItem("fr_i6", "Bibliothèque", "bee-blee-oh-TEK", "Library", "Place")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    CardPairItem("fr_a1", "Dépaysement", "day-pay-eez-MAHN", "Thrill of a foreign place", "Culture"),
                    CardPairItem("fr_a2", "Flâner", "flah-NAY", "To stroll aimlessly", "Action"),
                    CardPairItem("fr_a3", "Nuance", "nyoo-AHNS", "Subtle distinction", "Nuance"),
                    CardPairItem("fr_a4", "Épanouir", "ay-pah-NWEER", "To blossom / thrive", "Abstract"),
                    CardPairItem("fr_a5", "Quotidien", "ko-tee-DYAN", "Everyday life", "Time"),
                    CardPairItem("fr_a6", "Chaleureux", "shah-luh-RUH", "Warmhearted", "Emotion")
                )
            }
        }
        return all
    }

    fun calligraphyItems(language: TargetLanguage, difficulty: DifficultyLevel): List<CalligraphyPromptItem> =
        when (language) {
            TargetLanguage.JAPANESE -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    CalligraphyPromptItem("jp_c1", "あ", "a", "Hiragana 'a'", "3 strokes: horizontal, curve down, sweeping loop"),
                    CalligraphyPromptItem("jp_c2", "水", "mizu", "Water", "4 strokes: center hook first, left wing, right two strokes"),
                    CalligraphyPromptItem("jp_c3", "山", "yama", "Mountain", "3 strokes: center vertical, left-to-bottom, right vertical"),
                    CalligraphyPromptItem("jp_c4", "日", "hi / nichi", "Sun / Day", "4 strokes: left vertical, top-right corner, middle bar, bottom")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    CalligraphyPromptItem("jp_c5", "花", "hana", "Flower", "7 strokes: grass radical on top, person+change below"),
                    CalligraphyPromptItem("jp_c6", "空", "sora", "Sky / Empty", "8 strokes: cave roof top, work radical centered below"),
                    CalligraphyPromptItem("jp_c7", "心", "kokoro", "Heart / Mind", "4 strokes: left dot, sweeping curve with hook, two top dots")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    CalligraphyPromptItem("jp_c8", "夢", "yume", "Dream", "13 strokes: balanced top-to-bottom symmetry"),
                    CalligraphyPromptItem("jp_c9", "響", "hibiki", "Echo / Resonance", "20 strokes: village+sound over heart/tone")
                )
            }
            TargetLanguage.SPANISH -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    CalligraphyPromptItem("es_c1", "ñ", "eñe", "Palatal nasal 'ny'", "Draw lowercase 'n' topped with a smooth wavy tilde (~)"),
                    CalligraphyPromptItem("es_c2", "¿?", "interrogación", "Spanish question marks", "Open upside-down question mark first, closing mark at end"),
                    CalligraphyPromptItem("es_c3", "á", "a con tilde", "Stressed 'a'", "Lowercase 'a' with a crisp upward-right acute accent")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    CalligraphyPromptItem("es_c4", "Mañana", "mah-NYAH-nah", "Tomorrow", "Keep letters connected cleanly and place the tilde over the first n"),
                    CalligraphyPromptItem("es_c5", "¡Hola!", "OH-lah", "Hello!", "Start with inverted exclamation mark ¡ and finish with !")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    CalligraphyPromptItem("es_c6", "Corazón", "ko-rah-SON", "Heart", "Clean cursive/print flow with acute accent on ó")
                )
            }
            TargetLanguage.FRENCH -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    CalligraphyPromptItem("fr_c1", "ç", "c cédille", "Soft 's' C-cedilla", "Draw 'c' with a tiny hook tail hanging beneath the base"),
                    CalligraphyPromptItem("fr_c2", "é", "e accent aigu", "Closed 'ay' vowel", "Draw 'e' with an upward-right acute stroke above"),
                    CalligraphyPromptItem("fr_c3", "à", "a accent grave", "Preposition 'to/at'", "Draw 'a' with a downward-right grave stroke above")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    CalligraphyPromptItem("fr_c4", "Café", "kah-FAY", "Coffee / Café", "Balanced proportions ending with a crisp acute accent on é"),
                    CalligraphyPromptItem("fr_c5", "Crème", "krem", "Cream", "Graceful grave accent sloping down-right over è")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    CalligraphyPromptItem("fr_c6", "Français", "frahn-SEH", "French", "Clear cedilla hook beneath ç in the middle of the word")
                )
            }
        }

    fun quizQuestions(language: TargetLanguage, difficulty: DifficultyLevel): List<QuizQuestionItem> =
        when (language) {
            TargetLanguage.JAPANESE -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    QuizQuestionItem(
                        "jq_1",
                        "Which particle marks the topic of a sentence?",
                        "わたし ___ がくせいです。(I am a student.)",
                        listOf("は (wa)", "を (o)", "に (ni)", "で (de)"),
                        0,
                        "は (pronounced 'wa' as a particle) marks the grammatical topic."
                    ),
                    QuizQuestionItem(
                        "jq_2",
                        "Choose the particle for the direct object:",
                        "パン ___ たべます。(I eat bread.)",
                        listOf("が (ga)", "を (o)", "へ (e)", "と (to)"),
                        1,
                        "を (o) marks the direct object receiving the verb's action."
                    ),
                    QuizQuestionItem(
                        "jq_3",
                        "Which phrase politely asks 'Excuse me' before ordering?",
                        "___ 、すみません。",
                        listOf("おはよう", "すみません", "さようなら", "おやすみ"),
                        1,
                        "すみません (sumimasen) works for 'excuse me', 'sorry', and getting attention politely."
                    ),
                    QuizQuestionItem(
                        "jq_4",
                        "How do you make a sentence a polite yes/no question?",
                        "これは ペン です ___。",
                        listOf("ね (ne)", "よ (yo)", "か (ka)", "の (no)"),
                        2,
                        "Adding か (ka) at the end of a polite verb/copula turns it into a question."
                    )
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    QuizQuestionItem(
                        "jq_5",
                        "Select the particle indicating where an action takes place:",
                        "カフェ ___ コーヒーを のみます。",
                        listOf("に (ni)", "で (de)", "へ (e)", "から (kara)"),
                        1,
                        "で (de) marks the location where an active verb occurs."
                    ),
                    QuizQuestionItem(
                        "jq_6",
                        "Pick the polite past tense of 'たべます' (to eat):",
                        "きのう すしを ___。",
                        listOf("たべません", "たべたい", "たべました", "たべて"),
                        2,
                        "Polite verbs conjugate -ます -> -ました for past affirmative."
                    ),
                    QuizQuestionItem(
                        "jq_7",
                        "Which word means 'because / so' connecting a reason?",
                        "あめだ ___ 、いえに います。",
                        listOf("から", "けれど", "より", "ほど"),
                        0,
                        "から (kara) follows a reason clause to mean 'because'."
                    )
                )
                DifficultyLevel.ADVANCED -> listOf(
                    QuizQuestionItem(
                        "jq_8",
                        "Choose the humble (Kenjougo) verb for 'to go / come':",
                        "あした うかがいます / まいります。",
                        listOf("いらっしゃる", "まいる", "なさる", "おっしゃる"),
                        1,
                        "まいる (mairu) lowers the speaker's own action respectfully."
                    ),
                    QuizQuestionItem(
                        "jq_9",
                        "Which conditional expresses 'if it turns out that / assuming'?",
                        "ひま ___ 、ぜひ あそびに きてください。",
                        listOf("なら", "のに", "ながら", "ばかり"),
                        0,
                        "なら (nara) responds to a contextual condition ('if that's the case')."
                    )
                )
            }
            TargetLanguage.SPANISH -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    QuizQuestionItem(
                        "sq_1",
                        "Which 'to be' verb describes temporary location?",
                        "Yo ___ en Madrid hoy.",
                        listOf("soy", "estoy", "tengo", "hay"),
                        1,
                        "Use 'estar' (estoy) for locations and temporary states."
                    ),
                    QuizQuestionItem(
                        "sq_2",
                        "Pick the matching definite article for 'agua' (singular):",
                        "___ agua está fría.",
                        listOf("La", "El", "Las", "Unas"),
                        1,
                        "Feminine nouns starting with a stressed 'a' take 'el' in singular (el agua) to avoid double-a clash."
                    ),
                    QuizQuestionItem(
                        "sq_3",
                        "Complete 'I like music' in Spanish:",
                        "Me ___ la música.",
                        listOf("gusto", "gusta", "gustan", "gustas"),
                        1,
                        "'Gustar' agrees with the thing liked (singular 'la música' -> gusta)."
                    ),
                    QuizQuestionItem(
                        "sq_4",
                        "Translate 'I am 20 years old' naturally:",
                        "___ veinte años.",
                        listOf("Soy", "Estoy", "Tengo", "Hago"),
                        2,
                        "Spanish expresses age with 'tener' (to have): Tengo veinte años."
                    )
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    QuizQuestionItem(
                        "sq_5",
                        "Choose 'por' vs 'para' for a deadline:",
                        "El proyecto es ___ el viernes.",
                        listOf("por", "para", "de", "con"),
                        1,
                        "'Para' expresses deadlines, destinations, and recipients."
                    ),
                    QuizQuestionItem(
                        "sq_6",
                        "Pick the preterite form for a completed past event:",
                        "Ayer ___ al museo.",
                        listOf("iba", "fui", "voy", "iré"),
                        1,
                        "Use preterite 'fui' for a completed single trip yesterday."
                    )
                )
                DifficultyLevel.ADVANCED -> listOf(
                    QuizQuestionItem(
                        "sq_7",
                        "Select the subjunctive mood after 'Espero que':",
                        "Espero que ___ un buen viaje.",
                        listOf("tienes", "tengas", "tendrás", "tenías"),
                        1,
                        "Wishes with 'espero que' trigger the present subjunctive ('tengas')."
                    ),
                    QuizQuestionItem(
                        "sq_8",
                        "Watch out for the false friend! 'Embarazada' means:",
                        "Ella está embarazada.",
                        listOf("Embarrassed", "Pregnant", "Busy", "Exhausted"),
                        1,
                        "'Embarazada' means pregnant; 'avergonzada' means embarrassed."
                    )
                )
            }
            TargetLanguage.FRENCH -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    QuizQuestionItem(
                        "fq_1",
                        "Choose the contraction for 'at the' before masculine 'café':",
                        "Je suis ___ café.",
                        listOf("à le", "au", "à la", "aux"),
                        1,
                        "In French, 'à + le' always contracts to 'au'."
                    ),
                    QuizQuestionItem(
                        "fq_2",
                        "Complete 'I would like a croissant' politely:",
                        "Je ___ un croissant, s'il vous plaît.",
                        listOf("veux", "voudrais", "suis", "prends"),
                        1,
                        "'Je voudrais' (conditional) is the polite standard when ordering."
                    ),
                    QuizQuestionItem(
                        "fq_3",
                        "Choose the partitive article for 'some bread' (masculine):",
                        "Je mange ___ pain.",
                        listOf("de la", "du", "des", "de l'"),
                        1,
                        "'De + le' contracts to 'du' for uncountable masculine nouns."
                    )
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    QuizQuestionItem(
                        "fq_4",
                        "Pick the right auxiliary verb for 'aller' in passé composé:",
                        "Elle ___ allée à Paris.",
                        listOf("a", "est", "fait", "va"),
                        1,
                        "Verbs of motion like 'aller' form passé composé with 'être' ('est allée')."
                    ),
                    QuizQuestionItem(
                        "fq_5",
                        "Select the pronoun replacing 'à Paris' (a place):",
                        "Tu ___ vas demain ?",
                        listOf("en", "y", "le", "lui"),
                        1,
                        "The pronoun 'y' replaces places introduced by 'à', 'en', or 'dans'."
                    )
                )
                DifficultyLevel.ADVANCED -> listOf(
                    QuizQuestionItem(
                        "fq_6",
                        "Choose the subjunctive after 'Il faut que':",
                        "Il faut que nous ___ à l'heure.",
                        listOf("sommes", "soyons", "serons", "étions"),
                        1,
                        "Necessity ('il faut que') requires the subjunctive ('soyons')."
                    ),
                    QuizQuestionItem(
                        "fq_7",
                        "Watch the false friend! 'Actuellement' means:",
                        "Actuellement, j'habite à Lyon.",
                        listOf("Actually", "Currently", "Suddenly", "Occasionally"),
                        1,
                        "'Actuellement' means 'currently / right now', not 'actually' ('en fait')."
                    )
                )
            }
        }

    fun voiceChallenges(language: TargetLanguage, difficulty: DifficultyLevel): List<VoiceChallengeItem> =
        when (language) {
            TargetLanguage.JAPANESE -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    VoiceChallengeItem("jv_1", "おはようございます", "O-ha-you go-za-i-ma-su", "Good morning (polite)", "Listen & repeat cleanly"),
                    VoiceChallengeItem("jv_2", "お水をください", "O-mi-zu o ku-da-sa-i", "Water, please", "Listen & repeat cleanly"),
                    VoiceChallengeItem("jv_3", "これはいくらですか", "Ko-re wa i-ku-ra de-su ka", "How much is this?", "Listen & repeat with rising intonation")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    VoiceChallengeItem("jv_4", "駅はどこですか", "E-ki wa do-ko de-su ka", "Where is the train station?", "Listen & repeat cleanly"),
                    VoiceChallengeItem("jv_5", "日本語を勉強しています", "Ni-hon-go o ben-kyou shi-te i-ma-su", "I am studying Japanese", "Focus on smooth rhythm")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    VoiceChallengeItem("jv_6", "またお会いできて嬉しいです", "Ma-ta o-a-i de-ki-te u-re-shi-i de-su", "I'm glad we could meet again", "Natural pitch & pacing")
                )
            }
            TargetLanguage.SPANISH -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    VoiceChallengeItem("sv_1", "Buenos días, ¿cómo estás?", "BWEH-nos DEE-as, KOH-mo es-TAS", "Good morning, how are you?", "Listen & repeat cleanly"),
                    VoiceChallengeItem("sv_2", "Una mesa para dos, por favor", "OO-nah MEH-sah PAH-rah dos, por fah-VOR", "A table for two, please", "Listen & repeat cleanly"),
                    VoiceChallengeItem("sv_3", "Muchas gracias por tu ayuda", "MOO-chas GRAH-syas por too ah-YOO-dah", "Thank you very much for your help", "Listen & repeat cleanly")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    VoiceChallengeItem("sv_4", "¿Dónde está la estación de tren?", "DOHN-deh es-TAH lah es-tah-SYON deh tren", "Where is the train station?", "Clear crisp vowels"),
                    VoiceChallengeItem("sv_5", "Quisiera probar el plato del día", "kee-SYEH-rah pro-BAR el PLAH-toh del DEE-ah", "I would like to try the dish of the day", "Soft single 'r' tap")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    VoiceChallengeItem("sv_6", "El perro corre rápido por el parque", "el PEH-rro KOH-rreh RRAH-pee-do por el PAR-keh", "The dog runs fast through the park", "Master the rolled 'rr'")
                )
            }
            TargetLanguage.FRENCH -> when (difficulty) {
                DifficultyLevel.BEGINNER -> listOf(
                    VoiceChallengeItem("fv_1", "Bonjour, je voudrais un café", "bohn-ZHOOR, zhuh voo-DREH uhn kah-FAY", "Hello, I would like a coffee", "Listen & repeat cleanly"),
                    VoiceChallengeItem("fv_2", "Merci beaucoup, bonne journée", "mehr-SEE bo-KOO, bun zhoor-NAY", "Thank you very much, have a nice day", "Listen & repeat cleanly"),
                    VoiceChallengeItem("fv_3", "Excusez-moi, où est le métro ?", "ex-kyoo-zay MWAH, oo eh luh may-TRO", "Excuse me, where is the metro?", "Rounded 'u' and liaison")
                )
                DifficultyLevel.INTERMEDIATE -> listOf(
                    VoiceChallengeItem("fv_4", "Je ne comprends pas très bien", "zhuh nuh kohm-PRAHN pah treh BYAN", "I don't understand very well", "Smooth nasal vowels"),
                    VoiceChallengeItem("fv_5", "L'addition, s'il vous plaît", "lah-dee-SYOHN, seel voo PLEH", "The check, please", "Connected phrasing")
                )
                DifficultyLevel.ADVANCED -> listOf(
                    VoiceChallengeItem("fv_6", "Il pleut doucement sur les rues de Paris", "eel pluh doos-MAHN syoor lay ryoo duh pah-REE", "It rains softly on the streets of Paris", "French uvular 'r' & 'u'")
                )
            }
        }

    fun storyScenarios(language: TargetLanguage): List<StoryScenarioSeed> =
        when (language) {
            TargetLanguage.JAPANESE -> listOf(
                StoryScenarioSeed(
                    id = "tokyo_ramen",
                    language = TargetLanguage.JAPANESE,
                    title = "Midnight Ramen Counter",
                    setting = "A cozy lantern-lit ramen shop in Shimokitazawa, Tokyo.",
                    drawableRes = R.drawable.img_story_japan,
                    openingNpcLine = "いらっしゃいませ！何名様ですか？",
                    openingNpcPhonetic = "I-ra-ssha-i-ma-se! Nan-mei-sa-ma de-su ka?",
                    openingNpcEnglish = "Welcome! How many people in your party?",
                    initialChoices = listOf(
                        StoryChoiceSeed("ひとりです。カウンターでもいいですか？", "Hi-to-ri de-su. Ka-un-taa de mo i-i de-su ka?", "Just one. Is the counter okay?"),
                        StoryChoiceSeed("ふたりです。おすすめのラーメンはどれですか？", "Fu-ta-ri de-su. O-su-su-me no raa-men wa do-re de-su ka?", "Two of us. Which ramen do you recommend?")
                    )
                )
            )
            TargetLanguage.SPANISH -> listOf(
                StoryScenarioSeed(
                    id = "madrid_station",
                    language = TargetLanguage.SPANISH,
                    title = "Atocha Morning Express",
                    setting = "A bustling sunlit ticket kiosk at Madrid Atocha station.",
                    drawableRes = R.drawable.img_story_spain,
                    openingNpcLine = "¡Buenos días! ¿Adónde viaja usted hoy?",
                    openingNpcPhonetic = "BWEH-nos DEE-as! Ah-DOHN-deh VYAH-hah oos-TED oy?",
                    openingNpcEnglish = "Good morning! Where are you traveling today?",
                    initialChoices = listOf(
                        StoryChoiceSeed("Quisiera un billete a Sevilla, por favor.", "Kee-SYEH-rah oon bee-YEH-teh ah seh-VEE-yah, por fah-VOR.", "I'd like a ticket to Seville, please."),
                        StoryChoiceSeed("¿A qué hora sale el próximo tren a Toledo?", "Ah keh OH-rah SAH-leh el PROK-see-mo tren ah to-LEH-do?", "What time does the next train to Toledo leave?")
                    )
                )
            )
            TargetLanguage.FRENCH -> listOf(
                StoryScenarioSeed(
                    id = "paris_bistro",
                    language = TargetLanguage.FRENCH,
                    title = "Sidewalk Table in Le Marais",
                    setting = "A striped-awning café on a crisp morning in Paris.",
                    drawableRes = R.drawable.img_story_france,
                    openingNpcLine = "Bonjour ! Installez-vous. Qu'est-ce qui vous ferait plaisir ?",
                    openingNpcPhonetic = "Bohn-ZHOOR! An-stah-lay VOO. Kes-kee voo fuh-reh play-ZEER?",
                    openingNpcEnglish = "Hello! Have a seat. What would please you today?",
                    initialChoices = listOf(
                        StoryChoiceSeed("Bonjour ! Je voudrais un café crème et un croissant.", "Bohn-ZHOOR! Zhuh voo-DREH uhn kah-FAY krem ay uhn krwah-SAHN.", "Hello! I'd like a café crème and a croissant."),
                        StoryChoiceSeed("Avez-vous une table au soleil, s'il vous plaît ?", "Ah-vay VOO oon TAH-bluh oh so-LAY, seel voo PLEH?", "Do you have a table in the sun, please?")
                    )
                )
            )
        }

    fun phonemesFor(language: TargetLanguage): List<PhonemeGuideItem> =
        when (language) {
            TargetLanguage.JAPANESE -> listOf(
                PhonemeGuideItem("jp_p1", "ら り る れ ろ", "ra ri ru re ro", "Light single tongue tap just behind upper teeth—between English 'L' and 'D', never curled.", "ありがとう", "a-ri-ga-tou", "Thank you"),
                PhonemeGuideItem("jp_p2", "つ", "tsu", "Start with a quick 't' stop and release air like 'cats'—keep lips relaxed.", "つき", "tsu-ki", "Moon"),
                PhonemeGuideItem("jp_p3", "ふ", "fu", "Soft bilabial puff through slightly parted lips—do not bite your bottom lip.", "ふじさん", "Fu-ji-san", "Mt. Fuji"),
                PhonemeGuideItem("jp_p4", "ん", "n / m / ng", "Takes a full rhythmic beat (mora). Sounds like 'm' before b/p/m, 'ng' at ends.", "にほん", "Ni-hon", "Japan"),
                PhonemeGuideItem("jp_p5", "っ (Small tsu)", "Double consonant pause", "Hold a tiny silent beat before releasing the next consonant.", "きって", "kit-te", "Postage stamp")
            )
            TargetLanguage.SPANISH -> listOf(
                PhonemeGuideItem("es_p1", "ñ", "ny (like 'canyon')", "Press the middle of your tongue flat against the hard palate with nasal air.", "mañana", "mah-NYAH-nah", "Tomorrow"),
                PhonemeGuideItem("es_p2", "rr", "Trilled rolled 'r'", "Relax the tongue tip near the alveolar ridge and let steady airflow flutter it.", "perro", "PEH-rro", "Dog"),
                PhonemeGuideItem("es_p3", "j / ge / gi", "kh (breathy 'h')", "Gentle friction at the back of the soft palate—lighter than clearing your throat.", "jardín", "khar-DEEN", "Garden"),
                PhonemeGuideItem("es_p4", "a e i o u", "5 pure vowels", "Keep each vowel crisp and steady with zero glide or diphthong tail.", "amigo", "ah-MEE-go", "Friend"),
                PhonemeGuideItem("es_p5", "ll / y", "y / soft j", "Smooth palatal glide similar to 'yes', stronger in some regions.", "llamar", "yah-MAR", "To call")
            )
            TargetLanguage.FRENCH -> listOf(
                PhonemeGuideItem("fr_p1", "r", "Uvular 'r'", "Keep tongue tip down behind lower teeth; vibrate gently at the back of the throat.", "bonjour", "bohn-ZHOOR", "Hello"),
                PhonemeGuideItem("fr_p2", "u", "Rounded 'ee'", "Say 'ee' with your tongue while rounding your lips tightly like 'oo'.", "tu / rue", "tyoo / ryoo", "You / Street"),
                PhonemeGuideItem("fr_p3", "on / an / in", "Nasal vowels", "Lower the soft palate so air flows through nose and mouth—don't pronounce a hard 'n'!", "pain / vin", "pan / van", "Bread / Wine"),
                PhonemeGuideItem("fr_p4", "eu / œ", "Rounded 'eh'", "Shape lips in a relaxed circle while voicing 'eh'.", "fleur", "fluhr", "Flower"),
                PhonemeGuideItem("fr_p5", "Liaison", "Linking final consonants", "Silent final 's', 't', or 'n' links smoothly as 'z/t/n' before a vowel.", "les amis", "lay-zah-MEE", "The friends")
            )
        }
}
