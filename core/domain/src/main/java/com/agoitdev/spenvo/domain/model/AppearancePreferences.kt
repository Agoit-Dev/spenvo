package com.agoitdev.spenvo.domain.model

enum class ThemePreference { SYSTEM, LIGHT, DARK }

enum class ColorPreference { BRAND, DYNAMIC }

data class AppearancePreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val color: ColorPreference = ColorPreference.BRAND,
)
