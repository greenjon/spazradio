package llm.slop.spazradio.visuals

/** How hard the picture may work. Auto lets the web app's GPU probe and adaptive resolution decide. */
enum class VisualQuality(val js: String) { AUTO("auto"), LOW("low"), HIGH("high") }

/** Calmer motion: no flashes or hard looks. Auto follows the system's "remove animations" setting. */
enum class ReducedMotion(val js: String) { AUTO("auto"), ON("on"), OFF("off") }

data class VisualsSettings(
    val quality: VisualQuality = VisualQuality.AUTO,
    /** How strongly the picture follows the music: 0.25 .. 4, 2 is the web TV's default. */
    val reactivity: Float = 2f,
    val reducedMotion: ReducedMotion = ReducedMotion.AUTO,
    /** Follow a live broadcast from the Liquid LSD desktop app (needs the network). */
    val live: Boolean = false,
    /** Milliseconds the picture is held back so it lines up with what is heard (the audio sink buffers ahead). */
    val syncMs: Int = DEFAULT_SYNC_MS,
) {
    companion object {
        const val DEFAULT_SYNC_MS = 250
        const val MAX_SYNC_MS = 800
    }
}
