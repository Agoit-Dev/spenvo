package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.CategoriaDto
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FirebaseCategoriaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val categoriaDao: CategoriaDao,
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

    override suspend fun crearCategoria(categoria: Categoria) {
        persist(categoria)
    }

    override suspend fun actualizarCategoria(categoria: Categoria) {
        persist(categoria)
    }

    override suspend fun eliminarCategoria(categoria: Categoria) {
        persist(categoria)
    }

    private suspend fun persist(categoria: Categoria) {
        val dto = CategoriaDto.fromDomain(categoria)
        firestore.collection(CATEGORIAS_COLLECTION)
            .document(categoria.id)
            .set(dto.toMap())
            .await()
        categoriaDao.upsert(categoria.toEntity())
    }

    private companion object {
        const val CATEGORIAS_COLLECTION = "categorias"
    }
}
