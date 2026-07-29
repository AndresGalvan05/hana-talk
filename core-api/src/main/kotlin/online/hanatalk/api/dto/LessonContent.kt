package online.hanatalk.api.dto

data class LessonContent(
    val grammarPoints: List<GrammarPoint>,
    val dialogue: Dialogue,
    val cultureNote: CultureNote,
)

data class GrammarPoint(
    val title: String,
    val explanation: String,
    val examples: List<ExampleSentence>,
)

data class ExampleSentence(
    val japanese: String,
    val romaji: String,
    val english: String,
)

data class Dialogue(
    val title: String,
    val lines: List<DialogueLine>,
)

data class DialogueLine(
    val speaker: String,
    val japanese: String,
    val english: String,
)

data class CultureNote(
    val title: String,
    val body: String,
)
