package com.agoitdev.spenvo.data.auth

import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.usecase.EnviarRecuperacionPasswordUseCase
import com.agoitdev.spenvo.domain.usecase.IniciarSesionAnonimaUseCase
import com.agoitdev.spenvo.domain.usecase.IniciarSesionConEmailUseCase
import com.agoitdev.spenvo.domain.usecase.VincularCredencialUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    fun provideIniciarSesionAnonima(
        authRepository: AuthRepository,
    ): IniciarSesionAnonimaUseCase = IniciarSesionAnonimaUseCase(authRepository)

    @Provides
    fun provideIniciarSesionConEmail(
        authRepository: AuthRepository,
    ): IniciarSesionConEmailUseCase = IniciarSesionConEmailUseCase(authRepository)

    @Provides
    fun provideEnviarRecuperacionPassword(
        authRepository: AuthRepository,
    ): EnviarRecuperacionPasswordUseCase = EnviarRecuperacionPasswordUseCase(authRepository)

    @Provides
    fun provideVincularCredencial(
        authRepository: AuthRepository,
    ): VincularCredencialUseCase = VincularCredencialUseCase(authRepository)
}

