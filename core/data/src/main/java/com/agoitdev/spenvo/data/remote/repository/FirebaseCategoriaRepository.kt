package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.local.entity.CategoriaEntity
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.CategoriaDto
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.sync.EdicionesPendientes
import com.agoitdev.spenvo.domain.sync.VersionPendiente
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Optimistic Room-first writes: Room updates immediately, then Firestore. A
 * permanent Firestore error rolls Room back to the previous snapshot (see
 * `data-consistency.md` write contract). Update/delete also register an
 * unconfirmed pending edit (Slice 4 conflict detection) at the point `previo`
 * is read, for free.
 */
@Singleton
class FirebaseCategoriaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val categoriaDao: CategoriaDao,
    private val edicionesPendientes: EdicionesPendientes,
) : CategoriaRepository {

    override fun observarCategorias(planId: String): Flow<List<Categoria>> =
        categoriaDao.observeByPlan(planId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observarCategoriasPorTipo(
        planId: String,
        tipo: TipoCategoria,
    ): Flow<List<Categoria>> =
        categoriaDao.observeByPlanAndTipo(planId, tipo).map { entities ->
            entities.map { it.toDomain() }
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun crearCategoria(categoria: Categoria) {
        categoriaDao.upsert(categoria.toEntity())
        try {
            persistRemoto(categoria)
        } catch (e: Exception) {
            categoriaDao.delete(categoria.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun crearCategorias(categorias: List<Categoria>) {
        if (categorias.isEmpty()) return
        categoriaDao.upsertAll(categorias.map { it.toEntity() })
        try {
            val batch = firestore.batch()
            categorias.forEach { categoria ->
                batch.set(
                    firestore.collection(CATEGORIAS_COLLECTION).document(categoria.id),
                    CategoriaDto.fromDomain(categoria).toMap(),
                )
            }
            batch.commit().await()
        } catch (e: Exception) {
            categorias.forEach { categoriaDao.delete(it.id) }
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarCategoria(categoria: Categoria) {
        val previo = categoriaDao.get(categoria.id)
        registrarPendiente(categoria, previo?.editedAt)
        categoriaDao.upsert(categoria.toEntity())
        try {
            persistRemoto(categoria)
        } catch (e: Exception) {
            restaurar(previo, categoria.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun eliminarCategoria(categoria: Categoria) {
        val previo = categoriaDao.get(categoria.id)
        registrarPendiente(categoria, previo?.editedAt)
        categoriaDao.upsert(categoria.toEntity())
        try {
            persistRemoto(categoria)
        } catch (e: Exception) {
            restaurar(previo, categoria.id)
            throw e
        }
    }

    private fun registrarPendiente(categoria: Categoria, base: Instant?) {
        edicionesPendientes.registrarSiCorresponde(
            clave = EdicionesPendientes.clave(CATEGORIAS_COLLECTION, categoria.id),
            editorId = categoria.editedBy,
            base = base,
            miEditedAt = categoria.editedAt,
            miVersion = VersionPendiente.DeCategoria(categoria),
        )
    }

    private suspend fun persistRemoto(categoria: Categoria) {
        firestore.collection(CATEGORIAS_COLLECTION)
            .document(categoria.id)
            .set(CategoriaDto.fromDomain(categoria).toMap())
            .await()
    }

    private suspend fun restaurar(previo: CategoriaEntity?, id: String) {
        if (previo != null) categoriaDao.upsert(previo) else categoriaDao.delete(id)
    }

    private companion object {
        const val CATEGORIAS_COLLECTION = "categorias"
    }
}
