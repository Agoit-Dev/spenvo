package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.EdicionPendienteDao
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.EdicionPendiente
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.decidirSincronizacion
import java.time.Instant
import javax.inject.Inject

class RegistroEdicionesPendientesRoom @Inject constructor(
    private val dao: EdicionPendienteDao,
) : RegistroEdicionesPendientes {

    override suspend fun evaluar(clave: String, editedBy: String?, editedAt: Instant?): DecisionSincronizacion {
        val entity = dao.get(clave)
        val decision = decidirSincronizacion(entity?.toDomain(), editedBy, editedAt)
        if (decision == DecisionSincronizacion.PROPIA_CONFIRMADA) dao.delete(clave)
        return decision
    }

    override suspend fun registrarSiCorresponde(
        clave: String,
        editorId: String?,
        base: Instant?,
        miEditedAt: Instant?,
        tipo: TipoRegistro,
    ) {
        if (editorId == null) return
        dao.upsert(EdicionPendienteEntity(clave, tipo, editorId, base, miEditedAt))
    }

    override suspend fun limpiar(clave: String) = dao.delete(clave)
}

private fun EdicionPendienteEntity.toDomain() = EdicionPendiente(clave, tipo, editorId, base, miEditedAt)
