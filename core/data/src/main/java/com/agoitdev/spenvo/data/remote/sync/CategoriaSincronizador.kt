package com.agoitdev.spenvo.data.remote.sync

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.CategoriaDto
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
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
 * queries filter them. Each Firestore callback is processed inside one Room
 * transaction (ARCH-M501) — decision and write happen together, and a single
 * `Channel` consumer keeps overlapping callbacks in Firestore's own delivery
 * order rather than racing as independent coroutines.
 */
@Singleton
class CategoriaSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val categoriaDao: CategoriaDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
) : CategoriaSincronizacion {

    override fun sincronizar(planId: String): Flow<Unit> = callbackFlow {
        val lotes = Channel<List<Categoria>>(Channel.UNLIMITED)
        val consumidor = launch {
            for (lote in lotes) {
                procesarSnapshotCategorias(
                    database,
                    categoriaDao,
                    registroEdicionesPendientes,
                    registroConflictosPendientes,
                    lote,
                )
            }
        }
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
                    .map { it.toDomain() }
                lotes.trySend(categorias)
            }
        awaitClose {
            listener.remove()
            lotes.close()
            consumidor.cancel()
        }
    }

    private companion object {
        const val CATEGORIAS_COLLECTION = "categorias"
    }
}

/**
 * One transaction per Firestore batch: every document's LWW decision and every Room write happen
 * together, so `Flow`-backed queries only ever emit a fully-consistent post-commit state, and a
 * process death mid-batch rolls the whole thing back — Firestore re-delivers the full current
 * state on listener re-attach, so nothing is lost.
 */
internal suspend fun procesarSnapshotCategorias(
    database: SpenvoDatabase,
    categoriaDao: CategoriaDao,
    registroEdicionesPendientes: RegistroEdicionesPendientes,
    registroConflictosPendientes: RegistroConflictosPendientes,
    categorias: List<Categoria>,
) {
    database.withTransaction {
        val aplicables = categorias.mapNotNull { categoria ->
            val clave = claveRegistro(CATEGORIAS_COLLECTION_INTERNAL, categoria.id)
            when (registroEdicionesPendientes.evaluar(clave, categoria.editedBy, categoria.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA ->
                    categoria.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
                    val local = categoriaDao.get(categoria.id)
                    if (local == null) {
                        registroEdicionesPendientes.limpiar(clave)
                        categoria.toEntity()
                    } else {
                        registroConflictosPendientes.registrar(
                            clave,
                            ConflictoEdicion(
                                registroId = categoria.id,
                                tipo = TipoRegistro.CATEGORIA,
                                local = local.toDomain().aSnapshotConflicto(),
                                remoto = categoria.aSnapshotConflicto(),
                            ),
                        )
                        null
                    }
                }
            }
        }
        categoriaDao.upsertAll(aplicables)
    }
}

private const val CATEGORIAS_COLLECTION_INTERNAL = "categorias"
