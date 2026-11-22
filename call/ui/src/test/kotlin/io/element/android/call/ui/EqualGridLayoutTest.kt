/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.ui

import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EqualGridLayoutTest {
    @Test
    fun `every tile is the same size and inside the stage, clear of the controls, at every fitted count`() {
        for (metrics in listOf(PORTRAIT, LANDSCAPE)) {
            for (count in 1..EqualGridLayout.MAX_FITTED_TILES) {
                val layout = equalGrid(count, metrics)

                assertThat(layout.tiles.keys).containsExactlyElementsIn(aCrowd(count)).inOrder()
                assertThat(layout.tiles.values.map { it.width.toInt() to it.height.toInt() }.toSet()).hasSize(1)
                val bottom = if (metrics.isLandscape) metrics.height - MARGIN else metrics.height - CLEARANCE - MARGIN
                val area = Rect(MARGIN - TOLERANCE, MARGIN - TOLERANCE, metrics.width - MARGIN + TOLERANCE, bottom + TOLERANCE)
                layout.tiles.values.forEach { tile ->
                    assertThat(tile.left).isAtLeast(area.left)
                    assertThat(tile.top).isAtLeast(area.top)
                    assertThat(tile.right).isAtMost(area.right)
                    assertThat(tile.bottom).isAtMost(area.bottom)
                    assertThat(tile.width / tile.height).isAtLeast(EqualGridLayout.MIN_TILE_ASPECT - TOLERANCE)
                    assertThat(tile.width / tile.height).isAtMost(EqualGridLayout.MAX_TILE_ASPECT + TOLERANCE)
                }
                // Nothing overlaps.
                val tiles = layout.tiles.values.toList()
                for (i in tiles.indices) for (j in i + 1 until tiles.size) assertThat(tiles[i].deflate(TOLERANCE).overlaps(tiles[j])).isFalse()
                // Nothing scrolls, nothing is spotlit or hidden, and a share is shown whole.
                assertThat(layout.isStatic).isTrue()
                assertThat(layout.maxScroll(metrics.height)).isEqualTo(0f)
                assertThat(layout.spotlight).isNull()
                assertThat(layout.hiddenTileIds).isEmpty()
                assertThat(layout.fitsShares).isTrue()
            }
        }
    }

    @Test
    fun `two tiles are stacked upright and side by side sideways`() {
        val upright = equalGrid(2, PORTRAIT)
        assertThat(upright.gridPositions.getValue("t1")).isEqualTo(CallStageLayout.GridPosition(row = 1, column = 0))
        assertThat(upright.tiles.getValue("t0").width).isWithin(TOLERANCE).of(WIDTH - 2 * MARGIN)

        val sideways = equalGrid(2, LANDSCAPE)
        assertThat(sideways.gridPositions.getValue("t1")).isEqualTo(CallStageLayout.GridPosition(row = 0, column = 1))
        assertThat(sideways.tiles.getValue("t0").height).isWithin(TOLERANCE).of(WIDTH - 2 * MARGIN)
    }

    @Test
    fun `the columns grow with the count and a partial last row is centred`() {
        val layout = equalGrid(5, PORTRAIT)

        assertThat(layout.gridPositions.values.maxOf { it.column }).isEqualTo(1)
        val last = layout.tiles.getValue("t4")
        assertThat(last.center.x).isWithin(TOLERANCE).of(WIDTH / 2)
        // More tiles never get larger ones.
        val areas = (1..EqualGridLayout.MAX_FITTED_TILES).map { count -> equalGrid(count, PORTRAIT).tiles.values.first().let { it.width * it.height } }
        assertThat(areas).isInOrder(Comparator<Float> { a, b -> b.compareTo(a) })
    }

    @Test
    fun `past the fitted count the tiles keep their size and the rows scroll clear of the controls`() {
        val fitted = equalGrid(EqualGridLayout.MAX_FITTED_TILES, PORTRAIT)
        val crowded = equalGrid(30, PORTRAIT)

        assertThat(crowded.tiles.getValue("t0").size).isEqualTo(fitted.tiles.getValue("t0").size)
        assertThat(crowded.isStatic).isFalse()
        assertThat(crowded.tiles.getValue("t0").top).isEqualTo(MARGIN)
        assertThat(crowded.contentHeight).isWithin(TOLERANCE).of(crowded.tiles.getValue("t29").bottom + CLEARANCE + MARGIN)
        assertThat(crowded.maxScroll(HEIGHT)).isGreaterThan(0f)
    }

    @Test
    fun `no tiles and no room are both an empty stage`() {
        assertThat(equalGrid(0, PORTRAIT).tiles).isEmpty()
        assertThat(equalGrid(3, PORTRAIT.copy(width = 0f)).tiles).isEmpty()
    }

    private fun equalGrid(count: Int, metrics: CallStageMetrics) = CallStageArrangement.EqualGrid.compute(
        CallStageLayout.Input(gridTileIds = aCrowd(count), spotlightTileId = null, heroIds = emptyList(), metrics = metrics),
    )

    private fun aCrowd(size: Int) = List(size) { "t$it" }

    private companion object {
        const val WIDTH = 393f
        const val HEIGHT = 734f
        const val GAP = 8f
        const val MARGIN = 8f
        const val CLEARANCE = 88f + 34f
        const val TOLERANCE = 0.01f
        val PORTRAIT = CallStageMetrics(width = WIDTH, height = HEIGHT, gap = GAP, margin = MARGIN, controlsClearance = CLEARANCE)
        val LANDSCAPE = PORTRAIT.copy(width = HEIGHT, height = WIDTH)
    }
}
