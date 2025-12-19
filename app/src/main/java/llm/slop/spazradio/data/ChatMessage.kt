package llm.slop.spazradio.data

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    @SerializedName("user")
    val user: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("time_received")
    val timeReceived: Long
)
