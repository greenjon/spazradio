package llm.slop.spazradio.data

data class ArchiveShow(
    val title: String,
    val url: String,
    val date: String, // Formatted date for display
    val originalDate: String, // Raw pubDate string
    val imageUrl: String? = null,
    val duration: String? = null // Format: "HH:mm:ss" or "mm:ss"
)
