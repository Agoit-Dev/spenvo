package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AnalyticsRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.AceptarInvitacionUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.CrearCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.CrearPlanUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.InvitarMiembroUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasUseCase
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanesDelUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarCategoriasPorDefectoUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarPlanEjemploUseCase
import com.agoitdev.spenvo.data.remote.repository.FirebaseAccesoPlanRepository
import com.agoitdev.spenvo.data.remote.repository.FirebaseCategoriaRepository
import com.agoitdev.spenvo.data.remote.repository.FirebaseInvitacionPendienteRepository
import com.agoitdev.spenvo.data.remote.repository.FirebasePlanFinancieroRepository
import com.agoitdev.spenvo.data.remote.sync.CategoriaSincronizacion
import com.agoitdev.spenvo.data.remote.sync.CategoriaSincronizador
import com.agoitdev.spenvo.data.remote.sync.PlanSincronizacion
import com.agoitdev.spenvo.data.remote.sync.PlanSincronizador
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlanModule {

    @Binds
    @Singleton
    abstract fun bindPlanFinancieroRepository(
        impl: FirebasePlanFinancieroRepository,
    ): PlanFinancieroRepository

    @Binds
    @Singleton
    abstract fun bindAccesoPlanRepository(
        impl: FirebaseAccesoPlanRepository,
    ): AccesoPlanRepository

    @Binds
    @Singleton
    abstract fun bindCategoriaRepository(
        impl: FirebaseCategoriaRepository,
    ): CategoriaRepository

    @Binds
    @Singleton
    abstract fun bindInvitacionPendienteRepository(
        impl: FirebaseInvitacionPendienteRepository,
    ): InvitacionPendienteRepository

    @Binds
    @Singleton
    abstract fun bindCategoriaSincronizacion(
        impl: CategoriaSincronizador,
    ): CategoriaSincronizacion

    @Binds
    @Singleton
    abstract fun bindPlanSincronizacion(
        impl: PlanSincronizador,
    ): PlanSincronizacion
}

@Module
@InstallIn(SingletonComponent::class)
object PlanUseCaseModule {

    @Provides
    fun provideCrearPlan(
        planesRepository: PlanFinancieroRepository,
        accesosRepository: AccesoPlanRepository,
        sembrarCategorias: SembrarCategoriasPorDefectoUseCase,
    ): CrearPlanUseCase = CrearPlanUseCase(planesRepository, accesosRepository, sembrarCategorias)

    @Provides
    fun provideObservarPlanesDelUsuario(
        planesRepository: PlanFinancieroRepository,
    ): ObservarPlanesDelUsuarioUseCase = ObservarPlanesDelUsuarioUseCase(planesRepository)

    @Provides
    fun provideObservarPlan(
        planesRepository: PlanFinancieroRepository,
    ): ObservarPlanUseCase = ObservarPlanUseCase(planesRepository)

    @Provides
    fun provideActualizarPlan(
        planesRepository: PlanFinancieroRepository,
    ): ActualizarPlanUseCase = ActualizarPlanUseCase(planesRepository)

    @Provides
    fun provideInvitarMiembro(
        accesosRepository: AccesoPlanRepository,
        usuarioRepository: UsuarioRepository,
        pendientesRepository: InvitacionPendienteRepository,
        analyticsRepository: AnalyticsRepository,
    ): InvitarMiembroUseCase = InvitarMiembroUseCase(
        accesosRepository,
        usuarioRepository,
        pendientesRepository,
        analyticsRepository,
    )

    @Provides
    fun provideAceptarInvitacion(
        accesosRepository: AccesoPlanRepository,
    ): AceptarInvitacionUseCase = AceptarInvitacionUseCase(accesosRepository)

    @Provides
    fun provideSembrarPlanEjemplo(
        observarPlanes: ObservarPlanesDelUsuarioUseCase,
        crearPlan: CrearPlanUseCase,
        crearGasto: CrearGastoUseCase,
        crearIngreso: CrearIngresoUseCase,
    ): SembrarPlanEjemploUseCase = SembrarPlanEjemploUseCase(observarPlanes, crearPlan, crearGasto, crearIngreso)
}

@Module
@InstallIn(SingletonComponent::class)
object CategoriaUseCaseModule {

    @Provides
    fun provideSembrarCategoriasPorDefecto(
        categoriasRepository: CategoriaRepository,
    ): SembrarCategoriasPorDefectoUseCase = SembrarCategoriasPorDefectoUseCase(categoriasRepository)

    @Provides
    fun provideCrearCategoria(
        categoriasRepository: CategoriaRepository,
    ): CrearCategoriaUseCase = CrearCategoriaUseCase(categoriasRepository)

    @Provides
    fun provideActualizarCategoria(
        categoriasRepository: CategoriaRepository,
    ): ActualizarCategoriaUseCase = ActualizarCategoriaUseCase(categoriasRepository)

    @Provides
    fun provideEliminarCategoria(
        categoriasRepository: CategoriaRepository,
    ): EliminarCategoriaUseCase = EliminarCategoriaUseCase(categoriasRepository)

    @Provides
    fun provideObservarCategorias(
        categoriasRepository: CategoriaRepository,
    ): ObservarCategoriasUseCase = ObservarCategoriasUseCase(categoriasRepository)

    @Provides
    fun provideObservarCategoriasPorTipo(
        categoriasRepository: CategoriaRepository,
    ): ObservarCategoriasPorTipoUseCase = ObservarCategoriasPorTipoUseCase(categoriasRepository)
}
