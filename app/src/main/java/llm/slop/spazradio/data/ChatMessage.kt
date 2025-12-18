package llm.slop.spazradio.data

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    val user: String,
    val message: String,
    @SerializedName("time_received")
    val timeReceived: String
)
