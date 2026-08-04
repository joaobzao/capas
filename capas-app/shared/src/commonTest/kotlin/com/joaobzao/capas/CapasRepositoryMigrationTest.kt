package com.joaobzao.capas

import com.joaobzao.capas.capas.mergeNewAllowedIds
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cobre a migração SAPO: ao trocar a fonte das capas (vercapas -> SAPO), que
 * traz muitos mais jornais, os ids novos são acrescentados ao fim SEM perder a
 * ordem/favoritos que o utilizador já tinha.
 */
class CapasRepositoryMigrationTest {

    @Test
    fun appendsNewIds_preservingExistingOrder() {
        val saved = listOf("publico", "a-bola", "record")            // ordem do utilizador
        val fetched = listOf("publico", "a-bola", "record", "expresso", "marca") // SAPO (mais jornais)

        assertEquals(
            listOf("publico", "a-bola", "record", "expresso", "marca"),
            mergeNewAllowedIds(saved, fetched)
        )
    }

    @Test
    fun keepsUserCustomOrder_whenSourceListsSamePapersInDifferentOrder() {
        val saved = listOf("record", "publico", "a-bola")            // reordenado pelo utilizador
        val fetched = listOf("publico", "a-bola", "record")          // ordem "natural" da fonte

        // A ordem guardada ganha; nada é reordenado nem acrescentado.
        assertEquals(saved, mergeNewAllowedIds(saved, fetched))
    }

    @Test
    fun doesNotDuplicate_whenFetchRepeatsOrOverlaps() {
        val saved = listOf("publico", "a-bola")
        val fetched = listOf("publico", "a-bola", "expresso", "expresso")

        assertEquals(listOf("publico", "a-bola", "expresso"), mergeNewAllowedIds(saved, fetched))
    }

    @Test
    fun noNewIds_returnsCurrentUnchanged() {
        val saved = listOf("publico", "a-bola", "record")
        assertEquals(saved, mergeNewAllowedIds(saved, listOf("a-bola", "publico")))
    }
}
