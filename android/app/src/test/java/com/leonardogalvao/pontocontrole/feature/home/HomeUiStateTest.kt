package com.leonardogalvao.pontocontrole.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun `estado inicial identifica produto e ambiente`() {
        val state = HomeUiState()

        assertEquals("Controle de Ponto", state.title)
        assertEquals("Ambiente local", state.environment)
    }
}
