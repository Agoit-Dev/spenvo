package com.agoitdev.spenvo

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
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
}
