package com.kylecorry.trail_sense.tools.tides.infrastructure.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kylecorry.sol.science.oceanography.TideFrequency
import com.kylecorry.sol.time.Time.toZonedDateTime
import com.kylecorry.sol.units.Coordinate
import java.time.Instant
import java.time.ZonedDateTime

@Entity(tableName = "tides")
data class TideEntity(
    @ColumnInfo(name = "reference_high") val referenceHighTide: Long,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "latitude") val latitude: Double?,
    @ColumnInfo(name = "longitude") val longitude: Double?,
    @ColumnInfo(name = "mtl") val meanTideLevel: Float? = null,
    @ColumnInfo(name = "mllw") val meanLowerLowWater: Float? = null,
    @ColumnInfo(name = "mn") val meanRange: Float? = null,
    @ColumnInfo(name = "diurnal") val diurnal: Boolean = false
) {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0

    val reference: ZonedDateTime
        get() = Instant.ofEpochMilli(referenceHighTide).toZonedDateTime()

    val coordinate: Coordinate?
        get() {
            return if (latitude != null && longitude != null) {
                Coordinate(latitude, longitude)
            } else {
                null
            }
        }

    val frequency: TideFrequency
        get() = if (diurnal) TideFrequency.Diurnal else TideFrequency.Semidiurnal

}