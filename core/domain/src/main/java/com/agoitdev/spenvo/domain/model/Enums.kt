package com.agoitdev.spenvo.domain.model

enum class Rol {
    OWNER,
    ADMIN,
    EDITOR,
    VIEWER,
}

/**
 * Client-side mirror of firestore.rules' `roleLevel`/`tieneRolMinimo` ordering
 * (owner(3) > admin(2) > editor(1) > viewer(0)) — declaration order here is most-to-least
 * privileged, so a lower `ordinal` means a higher role.
 */
fun Rol.esAlMenos(minimo: Rol): Boolean = ordinal <= minimo.ordinal

enum class InvitacionEstado {
    PENDIENTE,
    ACEPTADA,
}

enum class TipoCategoria {
    GASTO,
    INGRESO,
}
