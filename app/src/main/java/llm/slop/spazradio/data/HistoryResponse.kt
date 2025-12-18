package llm.slop.spazradio.data

import com.google.gson.annotations.SerializedName

data class HistoryResponse(
    val status: String,
    @SerializedName("history")
    val history: List<ChatMessage>
)
