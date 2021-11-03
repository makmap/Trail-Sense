package com.kylecorry.trail_sense.tools.backtrack.domain.pathsort

import com.kylecorry.trail_sense.shared.paths.Path

interface IPathSortStrategy {
    fun sort(paths: List<Path>): List<Path>
}