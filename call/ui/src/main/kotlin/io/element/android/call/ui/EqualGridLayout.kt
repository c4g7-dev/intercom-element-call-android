/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.ui

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.min

/**
 * The equal grid: every tile the same size, people and shares alike, as large as the stage allows for their
 * number and its shape. Nobody is spotlit and our own tile is one of the grid.
 *
 * The column count is the one that gives the largest tiles, a tile being its cell held between a portrait 3:4 and
 * a landscape 16:9 so that neither a lone tile nor a crowded row becomes a strip. Rows are centred, a partial last row
 * too. Up to [MAX_FITTED_TILES] everything is on the stage and nothing scrolls; past that the tiles keep the size
 * that count has and the rows go on below.
 */
internal object EqualGridLayout {
    const val MAX_FITTED_TILES = 12
    const val MIN_TILE_ASPECT = 3f / 4f
    const val MAX_TILE_ASPECT = 16f / 9f

    fun compute(input: CallStageLayout.Input): CallStageLayout {
        val metrics = input.metrics
        val ids = input.gridTileIds
        if (metrics.width <= 0f || metrics.height <= 0f) return CallStageLayout.Empty
        // Upright, the control bar stays over the bottom of the stage; sideways it leaves with a tap, as in a small call.
        val top = metrics.margin
        val bottomClearance = if (metrics.isLandscape) metrics.margin else metrics.controlsClearance + metrics.margin
        val areaWidth = metrics.width - 2 * metrics.margin
        val areaHeight = metrics.height - top - bottomClearance
        if (ids.isEmpty() || areaWidth <= 0f || areaHeight <= 0f) {
            return CallStageLayout.Empty.copy(contentHeight = metrics.height, isStatic = true, fitsShares = true)
        }
        val fitted = min(ids.size, MAX_FITTED_TILES)
        val grid = bestGrid(fitted, areaWidth, areaHeight, metrics.gap)
        val isStatic = ids.size <= MAX_FITTED_TILES
        val rowCount = ceil(ids.size / grid.columns.toFloat()).toInt()
        val blockHeight = rowCount * grid.tileHeight + (rowCount - 1) * metrics.gap
        // Centred while it all fits; from the top once it scrolls.
        val blockTop = if (isStatic) top + (areaHeight - blockHeight) / 2 else top
        val tiles = LinkedHashMap<String, Rect>(ids.size)
        val positions = LinkedHashMap<String, CallStageLayout.GridPosition>(ids.size)
        ids.chunked(grid.columns).forEachIndexed { row, rowIds ->
            val rowWidth = rowIds.size * grid.tileWidth + (rowIds.size - 1) * metrics.gap
            val left = metrics.margin + (areaWidth - rowWidth) / 2
            val y = blockTop + row * (grid.tileHeight + metrics.gap)
            rowIds.forEachIndexed { column, id ->
                val x = left + column * (grid.tileWidth + metrics.gap)
                tiles[id] = Rect(x, y, x + grid.tileWidth, y + grid.tileHeight)
                positions[id] = CallStageLayout.GridPosition(row, column)
            }
        }
        return CallStageLayout(
            tiles = tiles,
            gridPositions = positions,
            spotlight = null,
            spotlightTileId = null,
            contentHeight = if (isStatic) metrics.height else maxOf(blockTop + blockHeight + bottomClearance, metrics.height),
            hiddenTileIds = emptySet(),
            heroStack = null,
            isStatic = isStatic,
            fitsShares = true,
        )
    }

    data class Grid(val columns: Int, val tileWidth: Float, val tileHeight: Float)

    /** The column count whose tiles are largest for [count] tiles in [width] by [height]; fewer columns on a tie. */
    fun bestGrid(count: Int, width: Float, height: Float, gap: Float): Grid {
        var best: Grid? = null
        for (columns in 1..count) {
            val rows = ceil(count / columns.toFloat()).toInt()
            val cellWidth = (width - (columns - 1) * gap) / columns
            val cellHeight = (height - (rows - 1) * gap) / rows
            if (cellWidth <= 0f || cellHeight <= 0f) continue
            val tileWidth = min(cellWidth, cellHeight * MAX_TILE_ASPECT)
            val tileHeight = min(cellHeight, cellWidth / MIN_TILE_ASPECT)
            val current = best
            if (current == null || tileWidth * tileHeight > current.tileWidth * current.tileHeight * TIE_MARGIN) {
                best = Grid(columns, tileWidth, tileHeight)
            }
        }
        return best ?: Grid(columns = 1, tileWidth = width, tileHeight = height)
    }

    /** More columns must be clearly better, so rounding never decides between two grids of the same area. */
    private const val TIE_MARGIN = 1.001f
}
