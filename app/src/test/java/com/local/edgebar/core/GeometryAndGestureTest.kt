package com.local.edgebar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryAndGestureTest {
    private val density = 2.625f // 420dpi, typical for the Nothing Phone (3) default scale
    private val threshold = EdgeBarSettings.OPEN_THRESHOLD_DP * density

    @Test fun `inward swipe from the right edge opens at 40dp`() {
        val downX = 1079f
        assertFalse(GestureMath.isOpenSwipe(Side.RIGHT, downX, 500f, downX - threshold + 1f, 500f, threshold))
        assertTrue(GestureMath.isOpenSwipe(Side.RIGHT, downX, 500f, downX - threshold, 500f, threshold))
    }

    @Test fun `inward swipe from the left edge opens at 40dp`() {
        assertTrue(GestureMath.isOpenSwipe(Side.LEFT, 0f, 500f, threshold, 510f, threshold))
        // Moving outward never opens.
        assertFalse(GestureMath.isOpenSwipe(Side.LEFT, 50f, 500f, 0f, 500f, threshold))
    }

    @Test fun `mostly vertical drags do not open`() {
        assertFalse(GestureMath.isOpenSwipe(Side.RIGHT, 1000f, 500f, 1000f - threshold, 500f + threshold * 2, threshold))
    }

    @Test fun `close swipe is toward the panel edge`() {
        assertTrue(GestureMath.isCloseSwipe(Side.RIGHT, 800f, 500f, 800f + threshold, 500f, threshold))
        assertFalse(GestureMath.isCloseSwipe(Side.RIGHT, 800f, 500f, 800f - threshold, 500f, threshold))
        assertTrue(GestureMath.shouldInterceptClose(Side.LEFT, 200f, 500f, 150f, 505f, 20f))
        assertFalse(GestureMath.shouldInterceptClose(Side.LEFT, 200f, 500f, 150f, 600f, 20f))
    }

    @Test fun `tap detection`() {
        assertTrue(GestureMath.isTap(2f, 3f, 120, 20f, 200))
        assertFalse(GestureMath.isTap(40f, 0f, 120, 20f, 200))
        assertFalse(GestureMath.isTap(0f, 0f, 900, 20f, 200))
    }

    @Test fun `panel width is 25 to 30 percent with an 88dp minimum`() {
        assertEquals(302, PanelGeometry.panelWidthPx(1080, density, 0.28f))
        assertEquals(270, PanelGeometry.panelWidthPx(1080, density, 0.10f)) // clamped to 25 %
        assertEquals(324, PanelGeometry.panelWidthPx(1080, density, 0.90f)) // clamped to 30 %
        assertEquals(231, PanelGeometry.panelWidthPx(600, density, 0.25f)) // 88dp floor
        assertEquals(100, PanelGeometry.panelWidthPx(100, density, 0.25f)) // never wider than screen
    }

    @Test fun `handle sits inside the safe vertical range`() {
        val l = HandleGeometry.compute(1080, 2400, Side.RIGHT, TriggerMode.EDGE, 0f, 420, 42, 0, 100, 60)
        assertEquals(100, l.top)
        assertEquals(0, l.edgeOffset)
        val bottom = HandleGeometry.compute(1080, 2400, Side.RIGHT, TriggerMode.EDGE, 1f, 420, 42, 0, 100, 60)
        assertEquals(2400 - 60 - 420, bottom.top)
    }

    @Test fun `pill mode is moved inward`() {
        val l = HandleGeometry.compute(1080, 2400, Side.LEFT, TriggerMode.PILL, 0.5f, 420, 42, 31, 0, 0)
        assertEquals(31, l.edgeOffset)
        assertEquals(Box(31, 990, 73, 1410), l.screenBounds(Side.LEFT, 1080))
    }

    @Test fun `a landscape cutout on the handle edge pushes it inward`() {
        // Landscape, cutout on the left edge between y=400..600.
        val cutout = Box(0, 400, 90, 600)
        val hit = HandleGeometry.compute(2400, 1080, Side.LEFT, TriggerMode.EDGE, 0.5f, 420, 42, 0, 0, 0, listOf(cutout))
        assertEquals(90, hit.edgeOffset)
        val otherSide = HandleGeometry.compute(2400, 1080, Side.RIGHT, TriggerMode.EDGE, 0.5f, 420, 42, 0, 0, 0, listOf(cutout))
        assertEquals(0, otherSide.edgeOffset)
        val noOverlap = HandleGeometry.compute(2400, 1080, Side.LEFT, TriggerMode.EDGE, 1f, 300, 42, 0, 0, 0, listOf(cutout))
        assertEquals(0, noOverlap.edgeOffset)
    }

    @Test fun `handle never exceeds the usable height`() {
        val l = HandleGeometry.compute(1080, 300, Side.RIGHT, TriggerMode.EDGE, 0.5f, 525, 42, 0, 100, 100)
        assertEquals(100, l.height)
        assertEquals(100, l.top)
    }
}
