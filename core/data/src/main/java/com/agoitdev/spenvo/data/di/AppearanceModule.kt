package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.data.appearance.ThemePreferences
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import com.agoitdev.spenvo.domain.usecase.ActualizarColorUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarTemaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppearanceModule {

    @Binds
    @Singleton
    abstract fun bindAppearancePreferencesRepository(
        impl: ThemePreferences,
    ): AppearancePreferencesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppearanceUseCaseModule {

    @Provides
    fun provideObservarAppearance(
        appearanceRepository: AppearancePreferencesRepository,
    ): ObservarAppearanceUseCase = ObservarAppearanceUseCase(appearanceRepository)

    @Provides
    fun provideActualizarTema(
        appearanceRepository: AppearancePreferencesRepository,
    ): ActualizarTemaUseCase = ActualizarTemaUseCase(appearanceRepository)

    @Provides
    fun provideActualizarColor(
        appearanceRepository: AppearancePreferencesRepository,
    ): ActualizarColorUseCase = ActualizarColorUseCase(appearanceRepository)
}
