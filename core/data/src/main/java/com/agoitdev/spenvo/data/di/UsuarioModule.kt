package com.agoitdev.spenvo.data.di

import android.content.Context
import com.agoitdev.spenvo.data.analytics.FirebaseAnalyticsRepository
import com.agoitdev.spenvo.data.remote.repository.FirebaseUsuarioRepository
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AnalyticsRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.AsegurarUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.GenerarNombreUsuarioUnicoUseCase
import com.agoitdev.spenvo.domain.usecase.RenombrarUsuarioUseCase
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsuarioModule {

    @Binds
    @Singleton
    abstract fun bindUsuarioRepository(impl: FirebaseUsuarioRepository): UsuarioRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: FirebaseAnalyticsRepository): AnalyticsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UsuarioUseCaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)

    @Provides
    fun provideGenerarNombreUsuarioUnico(
        usuarioRepository: UsuarioRepository,
    ): GenerarNombreUsuarioUnicoUseCase = GenerarNombreUsuarioUnicoUseCase(usuarioRepository)

    @Provides
    fun provideAsegurarUsuario(
        usuarioRepository: UsuarioRepository,
        generarNombreUsuarioUnico: GenerarNombreUsuarioUnicoUseCase,
        accesosRepository: AccesoPlanRepository,
        pendientesRepository: InvitacionPendienteRepository,
    ): AsegurarUsuarioUseCase = AsegurarUsuarioUseCase(
        usuarioRepository,
        generarNombreUsuarioUnico,
        accesosRepository,
        pendientesRepository,
    )

    @Provides
    fun provideRenombrarUsuario(
        usuarioRepository: UsuarioRepository,
    ): RenombrarUsuarioUseCase = RenombrarUsuarioUseCase(usuarioRepository)
}
