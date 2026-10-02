package com.district9.neonsteps.ui.scene

/** What the street sounds like: the scene reports, the audio engine plays. */
interface SceneAudio {
    /** Called every frame with the current rain (0..1) and grid power (0 in a blackout). */
    fun ambience(rain: Float, gridPower: Float)
    fun thunder(strength: Float)
    fun sparks(intensity: Float)
    fun firework(size: Float)
    fun poof(big: Boolean)
    fun powerDown()
    fun chime()
    fun ding()
}
