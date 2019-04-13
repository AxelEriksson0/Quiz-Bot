package se.quizhelp

data class ScreenshotText(
    val text: String,
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int
)

data class SearchEngine(
    val title: String,
    val link: String,
    val snippet: String,
    val fileFormat: String = ""
)

data class Option(
    val option: String,
    var hits: Int = 0,
    var percentage: Double = 0.00
)