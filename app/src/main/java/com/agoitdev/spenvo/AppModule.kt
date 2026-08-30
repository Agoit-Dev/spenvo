package com.agoitdev.spenvo

import com.agoitdev.spenvo.data.auth.SesionPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LogoutExplicitoFlag

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @LogoutExplicitoFlag
    fun provideLogoutExplicitoFlag(sesionPreferences: SesionPreferences): Flow<Boolean> =
        sesionPreferences.sesionCerradaExplicitamente
}
