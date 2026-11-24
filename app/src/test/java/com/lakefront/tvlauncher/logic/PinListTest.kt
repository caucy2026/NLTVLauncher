package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinListTest {
    private fun key(pkg: String, cls: String = "$pkg.Main") = ComponentKey(pkg, cls)

    @Test
    fun addAppendsAndDedups() {
        val a = key("a")
        val b = key("b")
        val once = PinList.add(PinList.add(emptyList(), a), b)
        assertEquals(listOf(a, b), once)
        assertEquals(listOf(a, b), PinList.add(once, a))
    }

    @Test
    fun removeAndMoveRespectEnds() {
        val a = key("a")
        val b = key("b")
        val c = key("c")
        val order = listOf(a, b, c)
        assertEquals(listOf(b, a, c), PinList.move(order, a, +1))
        assertEquals(listOf(a, c, b), PinList.move(order, c, -1))
        assertEquals(order, PinList.move(order, a, -1))
        assertEquals(order, PinList.move(order, c, +1))
        assertEquals(order, PinList.move(order, key("missing"), +1))
        assertEquals(listOf(a, c), PinList.remove(order, b))
    }

    @Test
    fun reconcileDropsMissingRetargetsAndDedups() {
        val old = key("player", "player.Old")
        val neu = key("player", "player.New")
        val kept = key("music")
        val gone = key("gone")
        val result = PinList.reconcile(
            order = listOf(gone, old, kept, key("player", "player.Also")),
            installed = listOf(neu, kept),
        )
        assertEquals(listOf(neu, kept), result)
    }

    @Test
    fun codecRoundTripSkipsBadLinesAndDedups() {
        val a = key("a")
        val b = key("b")
        val encoded = PinCodec.encode(listOf(a, b))
        assertEquals(listOf(a, b), PinCodec.decode(encoded))
        assertEquals(
            listOf(a),
            PinCodec.decode("\nnot a key\n${a.flatten()}\n${a.flatten()}\n/\n"),
        )
        assertEquals(emptyList<ComponentKey>(), PinCodec.decode(null))
        assertNull(ComponentKey.parse("onlypackage"))
        assertNull(ComponentKey.parse("pkg/class/extra"))
    }
}
