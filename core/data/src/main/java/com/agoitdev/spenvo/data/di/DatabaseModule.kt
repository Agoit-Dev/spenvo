package com.agoitdev.spenvo.data.di

import android.content.Context
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.AccesoPlanDao
import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.local.dao.PlanFinancieroDao
import com.agoitdev.spenvo.security.AndroidKeystorePassphraseProvider
import com.agoitdev.spenvo.security.PassphraseProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePassphraseProvider(
        @ApplicationContext context: Context,
    ): PassphraseProvider = AndroidKeystorePassphraseProvider(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: PassphraseProvider,
    ): SpenvoDatabase = SpenvoDatabase.build(context, passphraseProvider)

    @Provides
    fun providePlanFinancieroDao(database: SpenvoDatabase): PlanFinancieroDao =
        database.planFinancieroDao()

    @Provides
    fun provideAccesoPlanDao(database: SpenvoDatabase): AccesoPlanDao =
        database.accesoPlanDao()

    @Provides
    fun provideCategoriaDao(database: SpenvoDatabase): CategoriaDao =
        database.categoriaDao()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
