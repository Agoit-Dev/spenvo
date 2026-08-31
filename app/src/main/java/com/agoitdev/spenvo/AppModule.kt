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

/**
 * Write side of the persisted logout flag, mirroring how [LogoutExplicitoFlag] exposes the read
 * side: a one-method action instead of the whole [SesionPreferences]. Keeps DataStore (and its
 * `internal` test-only constructor, which `:app` can't reach) out of [SesionGateViewModel], so the
 * gate stays unit-testable with a plain lambda.
 */
fun interface LimpiarLogoutExplicito {
    suspend operator fun invoke()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @LogoutExplicitoFlag
    fun provideLogoutExplicitoFlag(sesionPreferences: SesionPreferences): Flow<Boolean> =
        sesionPreferences.sesionCerradaExplicitamente

    @Provides
    fun provideLimpiarLogoutExplicito(sesionPreferences: SesionPreferences): LimpiarLogoutExplicito =
        LimpiarLogoutExplicito { sesionPreferences.limpiarLogout() }
}
