package com.agoitdev.spenvo.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolTest {

    @Test
    fun `OWNER cumple el minimo ADMIN`() {
        assertTrue(Rol.OWNER.esAlMenos(Rol.ADMIN))
    }

    @Test
    fun `ADMIN cumple el minimo ADMIN`() {
        assertTrue(Rol.ADMIN.esAlMenos(Rol.ADMIN))
    }

    @Test
    fun `EDITOR no cumple el minimo ADMIN`() {
        assertFalse(Rol.EDITOR.esAlMenos(Rol.ADMIN))
    }

    @Test
    fun `VIEWER no cumple el minimo ADMIN`() {
        assertFalse(Rol.VIEWER.esAlMenos(Rol.ADMIN))
    }
}
