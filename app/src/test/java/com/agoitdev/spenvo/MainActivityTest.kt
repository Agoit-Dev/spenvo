package com.agoitdev.spenvo

import androidx.navigation3.runtime.NavKey
import com.agoitdev.spenvo.cuenta.AuthTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {

    @Test
    fun `MostrarGate on backstack with PlanesRoute replaces it with CuentaRoute`() {
        val backStack = mutableListOf<NavKey>(PlanesRoute)

        aplicarEstadoGate(EstadoGate.MostrarGate, backStack)

        assertEquals(listOf(CuentaRoute), backStack.toList())
    }

    @Test
    fun `MostrarGate on multi-entry backstack collapses it to CuentaRoute only`() {
        val backStack = mutableListOf<NavKey>(PlanesRoute, PlanRoute("x"))

        aplicarEstadoGate(EstadoGate.MostrarGate, backStack)

        assertEquals(listOf(CuentaRoute), backStack.toList())
    }

    @Test
    fun `MostrarApp when backstack is solely CuentaRoute switches to PlanesRoute`() {
        val backStack = mutableListOf<NavKey>(CuentaRoute)

        aplicarEstadoGate(EstadoGate.MostrarApp, backStack)

        assertEquals(listOf(PlanesRoute), backStack.toList())
    }

    @Test
    fun `MostrarApp when backstack is not solely CuentaRoute leaves it unchanged`() {
        val backStack = mutableListOf<NavKey>(PlanesRoute, PlanRoute("x"))

        aplicarEstadoGate(EstadoGate.MostrarApp, backStack)

        assertEquals(listOf(PlanesRoute, PlanRoute("x")), backStack.toList())
    }

    @Test
    fun `Cargando leaves any non-empty backstack unchanged`() {
        val backStack = mutableListOf<NavKey>(PlanesRoute, PlanRoute("x"))

        aplicarEstadoGate(EstadoGate.Cargando, backStack)

        assertEquals(listOf(PlanesRoute, PlanRoute("x")), backStack.toList())
    }

    @Test
    fun `MostrarApp when backstack is solely PlanesRoute leaves it unchanged`() {
        val backStack = mutableListOf<NavKey>(PlanesRoute)

        aplicarEstadoGate(EstadoGate.MostrarApp, backStack)

        assertEquals(listOf(PlanesRoute), backStack.toList())
    }

    @Test
    fun `CuentaRoute opened from the post-logout gate defaults to the sign-in tab`() {
        assertEquals(AuthTab.INICIAR_SESION, tabInicialPara(EstadoGate.MostrarGate))
    }

    @Test
    fun `CuentaRoute opened from PlanesScreen's account menu keeps defaulting to sign-up`() {
        assertEquals(AuthTab.CREAR_CUENTA, tabInicialPara(EstadoGate.MostrarApp))
    }

    @Test
    fun `only the post-logout gate offers continue-as-guest`() {
        assertTrue(mostrarContinuarComoInvitado(EstadoGate.MostrarGate))
        // Reached from PlanesScreen's account menu: the user already has a live session, so
        // "continue as guest" would be meaningless there (and nonsense for a linked account).
        assertFalse(mostrarContinuarComoInvitado(EstadoGate.MostrarApp))
        assertFalse(mostrarContinuarComoInvitado(EstadoGate.Cargando))
    }

    @Test
    fun `navigation stays hidden while the gate is still resolving the session`() {
        // The backstack starts at PlanesRoute, so rendering NavDisplay during Cargando would
        // flash PlanesScreen at a logged-out user before MostrarGate clears it.
        assertFalse(debeMostrarNavegacion(EstadoGate.Cargando))
        assertTrue(debeMostrarNavegacion(EstadoGate.MostrarApp))
        assertTrue(debeMostrarNavegacion(EstadoGate.MostrarGate))
    }
}
