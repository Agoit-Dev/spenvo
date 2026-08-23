package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.data.remote.repository.FirebaseStorageRepository
import com.agoitdev.spenvo.domain.repository.StorageRepository
import com.agoitdev.spenvo.domain.usecase.SubirAvatarUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindStorageRepository(impl: FirebaseStorageRepository): StorageRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StorageUseCaseModule {

    @Provides
    fun provideSubirAvatarUseCase(
        storageRepository: StorageRepository,
    ): SubirAvatarUseCase = SubirAvatarUseCase(storageRepository)
}
