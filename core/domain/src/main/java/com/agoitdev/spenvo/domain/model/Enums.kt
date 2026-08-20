package com.agoitdev.spenvo.domain.model

enum class Rol {
    OWNER,
    ADMIN,
    EDITOR,
    VIEWER,
}

enum class InvitacionEstado {
    PENDIENTE,
    ACEPTADA,
}

enum class TipoCategoria {
    GASTO,
    INGRESO,
}
