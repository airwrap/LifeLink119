package com.lifelink119.app.core.emergency

/** Explicit presentation mode. Plays authored synthetic answers; never opens a microphone. */
class DemoEmergencySpeech(
    private val output: EmergencySpeechPort,
    private val changed: () -> Unit,
) : EmergencySpeechPort {
    var enabled = false
        private set

    var example: String? = null
        private set

    private var index = 0
    private var completed = 0
    val finished
        get() = enabled && completed == ANSWERS.size

    private var generation = 0L

    fun begin() {
        stop()
        index = 0
        completed = 0
        enabled = true
    }

    fun end() {
        stop()
        enabled = false
        example = null
    }

    override fun speak(parts: List<String>, done: (Boolean) -> Unit) {
        example = null
        if (enabled) changed()
        output.speak(parts, done)
    }

    override fun listen(binary: Boolean, done: (String?) -> Unit) {
        if (!enabled) {
            output.listen(binary, done)
            return
        }
        val token = ++generation
        val answer = ANSWERS.getOrNull(index++) ?: "그만"
        example = answer
        changed()
        output.speak(listOf(answer)) { success ->
            if (token == generation && enabled) {
                if (success) completed++
                done(if (success) answer else null)
            }
        }
    }

    override fun stop() {
        generation++
        output.stop()
    }

    companion object {
        // Conscious, breathing, no fire, no bleeding, chest pain, no further worsening.
        val ANSWERS = listOf("네", "네", "아니요", "아니요", "네", "아니요")
    }
}
