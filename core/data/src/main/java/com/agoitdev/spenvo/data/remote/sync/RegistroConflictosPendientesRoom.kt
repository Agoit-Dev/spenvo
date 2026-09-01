package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.ConflictoEdicionDao
import com.agoitdev.spenvo.data.local.entity.ConflictoEdicionEntity
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RegistroConflictosPendientesRoom @Inject constructor(
    private val dao: ConflictoEdicionDao,
) : RegistroConflictosPendientes {

    override val conflictos: Flow<Map<String, ConflictoEdicion>> =
        dao.observeAll().map { entities -> entities.associate { it.clave to it.toDomain() } }

    override suspend fun conflictoPara(clave: String): ConflictoEdicion? = dao.get(clave)?.toDomain()

    override suspend fun registrar(clave: String, conflicto: ConflictoEdicion) =
        dao.upsert(ConflictoEdicionEntity(clave, conflicto.registroId, conflicto.tipo, conflicto.local, conflicto.remoto))

    override suspend fun resolver(clave: String) = dao.delete(clave)
}

private fun ConflictoEdicionEntity.toDomain() = ConflictoEdicion(registroId, tipo, local, remoto)
