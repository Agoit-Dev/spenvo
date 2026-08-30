package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.InvitacionPendienteDto
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `invitaciones_pendientes_por_email` holds a doc per (email, plan) pending invite, keyed by a
 * composite id since one email can be pending-invited to multiple plans. [obtenerPorEmail] is the
 * one intentional exception to "no queries" across this feature's collections — Firestore rules
 * (Task 9) restrict that `list` to the caller's own verified email, so it stays a targeted lookup,
 * never an open query surface. See the design doc's "Invite by nombreUsuario or email" section.
 */
@Singleton
class FirebaseInvitacionPendienteRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : InvitacionPendienteRepository {

    override suspend fun crear(invitacion: InvitacionPendiente) {
        val dto = InvitacionPendienteDto.fromDomain(invitacion)
        firestore.collection(INVITACIONES_COLLECTION)
            .document(docId(invitacion.email, invitacion.planId))
            .set(dto.toMap())
            .await()
    }

    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> {
        val snapshot = firestore.collection(INVITACIONES_COLLECTION)
            .whereEqualTo("email", emailNormalizado)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.data?.let { InvitacionPendienteDto.fromData(it)?.toDomain() }
        }
    }

    override suspend fun eliminar(emailNormalizado: String, planId: String) {
        firestore.collection(INVITACIONES_COLLECTION)
            .document(docId(emailNormalizado, planId))
            .delete()
            .await()
    }

    private companion object {
        const val INVITACIONES_COLLECTION = "invitaciones_pendientes_por_email"

        fun docId(emailNormalizado: String, planId: String): String = "${emailNormalizado}_$planId"
    }
}
