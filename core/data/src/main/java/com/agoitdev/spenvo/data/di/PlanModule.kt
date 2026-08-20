package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.usecase.AceptarInvitacionUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.CrearPlanUseCase
import com.agoitdev.spenvo.domain.usecase.InvitarMiembroUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanesDelUsuarioUseCase
import com.agoitdev.spenvo.data.remote.repository.FirebaseAccesoPlanRepository
import com.agoitdev.spenvo.data.remote.repository.FirebasePlanFinancieroRepository
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
}

@Module
@InstallIn(SingletonComponent::class)
object PlanUseCaseModule {

    @Provides
    fun provideCrearPlan(
        planesRepository: PlanFinancieroRepository,
        accesosRepository: AccesoPlanRepository,
    ): CrearPlanUseCase = CrearPlanUseCase(planesRepository, accesosRepository)

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
    ): InvitarMiembroUseCase = InvitarMiembroUseCase(accesosRepository)

    @Provides
    fun provideAceptarInvitacion(
        accesosRepository: AccesoPlanRepository,
    ): AceptarInvitacionUseCase = AceptarInvitacionUseCase(accesosRepository)
}
