package com.agoitdev.spenvo.domain.repository

/**
 * Anonymous, dev-visibility-only analytics signal. Per AGENTS.md's "never log amounts, emails,
 * or credentials" rule and the anti-enumeration design, event names passed here must never carry
 * an identificador, email, nombreUsuario, or any other identifying payload — the whole point is
 * volume visibility (e.g. "how often do invite attempts fail to resolve") without exposing what
 * was searched.
 */
interface AnalyticsRepository {
    fun registrarEvento(nombre: String)
}
