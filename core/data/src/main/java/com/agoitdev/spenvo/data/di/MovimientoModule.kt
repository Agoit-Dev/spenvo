package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.data.remote.repository.FirebaseMovimientoRepository
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizador
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MovimientoModule {

    @Binds
    @Singleton
    abstract fun bindMovimientoRepository(
        impl: FirebaseMovimientoRepository,
    ): MovimientoRepository

    @Binds
    @Singleton
    abstract fun bindMovimientoSincronizacion(
        impl: MovimientoSincronizador,
    ): MovimientoSincronizacion
}

@Module
@InstallIn(SingletonComponent::class)
object MovimientoUseCaseModule {

    @Provides
    fun provideValidarMonto(): ValidarMontoUseCase = ValidarMontoUseCase()

    @Provides
    fun provideCrearGasto(
        movimientoRepository: MovimientoRepository,
        validarMonto: ValidarMontoUseCase,
    ): CrearGastoUseCase = CrearGastoUseCase(movimientoRepository, validarMonto)

    @Provides
    fun provideCrearIngreso(
        movimientoRepository: MovimientoRepository,
        validarMonto: ValidarMontoUseCase,
    ): CrearIngresoUseCase = CrearIngresoUseCase(movimientoRepository, validarMonto)

    @Provides
    fun provideObservarMovimientos(
        movimientoRepository: MovimientoRepository,
    ): ObservarMovimientosUseCase = ObservarMovimientosUseCase(movimientoRepository)
}
