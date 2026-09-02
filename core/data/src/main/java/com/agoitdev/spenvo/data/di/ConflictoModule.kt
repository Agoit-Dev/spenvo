package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.data.remote.sync.RegistroConflictosPendientesRoom
import com.agoitdev.spenvo.data.remote.sync.RegistroEdicionesPendientesRoom
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Cross-cutting ARCH-M501 conflict-detection registries, shared by the Movimiento, Categoria and
 * Plan repositories/sincronizadores. Room-backed since ARCH-M501 — see
 * `doc/designs/2026-09-01-conflictos-pendientes-room-design.md`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConflictoModule {

    @Binds
    @Singleton
    abstract fun bindRegistroEdicionesPendientes(impl: RegistroEdicionesPendientesRoom): RegistroEdicionesPendientes

    @Binds
    @Singleton
    abstract fun bindRegistroConflictosPendientes(impl: RegistroConflictosPendientesRoom): RegistroConflictosPendientes
}
