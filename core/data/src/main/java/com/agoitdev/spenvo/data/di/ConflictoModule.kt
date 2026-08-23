package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.sync.EdicionesPendientes
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Cross-cutting Slice 4 conflict-detection registries, shared by the
 * Movimiento, Categoria and Plan repositories/sincronizadores. Kept in a
 * dedicated module (rather than PlanModule/MovimientoModule) so neither of
 * those stays coupled to a concern that spans all three entity families.
 */
@Module
@InstallIn(SingletonComponent::class)
object ConflictoModule {

    @Provides
    @Singleton
    fun provideEdicionesPendientes(): EdicionesPendientes = EdicionesPendientes()

    @Provides
    @Singleton
    fun provideConflictosPendientes(): ConflictosPendientes = ConflictosPendientes()
}
