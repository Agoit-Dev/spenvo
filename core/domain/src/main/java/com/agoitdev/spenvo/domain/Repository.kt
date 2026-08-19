package com.agoitdev.spenvo.domain

import kotlinx.coroutines.flow.Flow

interface ObservableRepository<T> {
    fun observeAll(): Flow<List<T>>
}

interface MutateRepository<T> {
    suspend fun add(item: T)
    suspend fun update(item: T)
    suspend fun delete(id: String)
}

interface Repository<T> : ObservableRepository<T>, MutateRepository<T>
