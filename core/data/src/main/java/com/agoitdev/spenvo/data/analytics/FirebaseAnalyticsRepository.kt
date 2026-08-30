package com.agoitdev.spenvo.data.analytics

import com.agoitdev.spenvo.domain.repository.AnalyticsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsRepository @Inject constructor(
    private val analytics: FirebaseAnalytics,
) : AnalyticsRepository {
    override fun registrarEvento(nombre: String) {
        analytics.logEvent(nombre, null)
    }
}
