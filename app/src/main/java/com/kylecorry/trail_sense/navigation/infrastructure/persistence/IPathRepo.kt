package com.kylecorry.trail_sense.navigation.infrastructure.persistence

import androidx.lifecycle.LiveData
import com.kylecorry.trail_sense.shared.paths.Path

interface IPathRepo {
    suspend fun add(value: Path): Long

    suspend fun delete(value: Path)

    suspend fun get(id: Long): Path?

    fun getLive(id: Long): LiveData<Path?>

    suspend fun getAll(): List<Path>

    fun getAllLive(): LiveData<List<Path>>
}