package io.tolgee.intellij.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StringsTest {

    @Test
    fun `blank string yields empty list`() {
        assertEquals(emptyList<String>(), "".splitCsv())
        assertEquals(emptyList<String>(), "   ".splitCsv())
    }

    @Test
    fun `trims each token`() {
        assertEquals(listOf("en", "de", "fr"), " en , de ,fr ".splitCsv())
    }

    @Test
    fun `drops empty tokens from consecutive commas`() {
        assertEquals(listOf("a", "b"), "a,,b,".splitCsv())
    }

    @Test
    fun `single token without comma`() {
        assertEquals(listOf("common"), "common".splitCsv())
    }

    @Test
    fun `returns mutable list`() {
        val list = "a,b".splitCsv()
        list.add("c")
        assertEquals(listOf("a", "b", "c"), list)
    }
}
