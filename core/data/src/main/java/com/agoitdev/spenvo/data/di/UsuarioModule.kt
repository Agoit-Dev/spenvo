package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.data.remote.repository.FirebaseUsuarioRepository
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.AsegurarUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.GenerarNombreUsuarioUnicoUseCase
import com.agoitdev.spenvo.domain.usecase.RenombrarUsuarioUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsuarioModule {

    @Binds
    @Singleton
    abstract fun bindUsuarioRepository(impl: FirebaseUsuarioRepository): UsuarioRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UsuarioUseCaseModule {

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
