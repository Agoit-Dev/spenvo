package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.CategoriaDto
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

interface CategoriaSincronizacion {
    fun sincronizar(planId: String): Flow<Unit>
}

/**
 * Syncs a plan's categories from Firestore into Room while it is collected.
 * Snapshot listener only lives during collection (active scope), per AGENTS.md
 * rule 3. Soft-deleted categories arrive as upserts with `deletedAt` set; Room
 * queries filter them.
 */
@Singleton
class CategoriaSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val categoriaDao: CategoriaDao,
) : CategoriaSincronizacion {

    override fun sincronizar(planId: String): Flow<Unit> = callbackFlow {
        val listener = firestore.collection(CATEGORIAS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                val categorias = snapshot?.documents.orEmpty()
                    .mapNotNull { CategoriaDto.fromData(it.data ?: return@mapNotNull null) }
                    .map { it.toDomain().toEntity() }
                if (categorias.isNotEmpty()) {
                    launch { categoriaDao.upsertAll(categorias) }
                }
            }
        awaitClose { listener.remove() }
    }

    private companion object {
        const val CATEGORIAS_COLLECTION = "categorias"
    }
}
