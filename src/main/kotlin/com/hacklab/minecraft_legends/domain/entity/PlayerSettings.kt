package com.hacklab.minecraft_legends.domain.entity

data class PlayerSettings(
    val language: String = "ja",
    val currentTitle: String? = null,
    val showTitleInChat: Boolean = true,
    val showTitleInTab: Boolean = true,
    val autoJoinGame: Boolean = true,
    val preferredLegend: String? = null,
    val showDamageNumbers: Boolean = true,
    val showKillMessages: Boolean = true,
    val enableSounds: Boolean = true,
    val customSettings: Map<String, String> = emptyMap()
) {
    fun setLanguage(language: String): PlayerSettings = copy(language = language)
    
    fun setCurrentTitle(title: String?): PlayerSettings = copy(currentTitle = title)
    
    fun setPreferredLegend(legend: String?): PlayerSettings = copy(preferredLegend = legend)
    
    fun setCustomSetting(key: String, value: String): PlayerSettings =
        copy(customSettings = customSettings + (key to value))
    
    fun removeCustomSetting(key: String): PlayerSettings =
        copy(customSettings = customSettings - key)
}