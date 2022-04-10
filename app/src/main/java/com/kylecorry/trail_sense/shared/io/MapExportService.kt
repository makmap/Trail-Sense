package com.kylecorry.trail_sense.shared.io

import android.content.Context
import android.graphics.Bitmap
import com.kylecorry.andromeda.core.bitmap.BitmapUtils
import com.kylecorry.andromeda.core.bitmap.BitmapUtils.rotate
import com.kylecorry.andromeda.files.LocalFiles
import com.kylecorry.andromeda.pdf.*
import com.kylecorry.sol.science.geology.ReferenceEllipsoid
import com.kylecorry.trail_sense.tools.maps.domain.Map
import com.kylecorry.trail_sense.tools.maps.domain.MapProjectionType

class MapExportService(
    private val context: Context,
    private val uriPicker: UriPicker,
    private val uriService: UriService
) :
    ExportService<Map> {
    override suspend fun export(data: Map, filename: String): Boolean {
        var bitmap: Bitmap? = null
        try {
            val file = LocalFiles.getFile(context, data.filename, create = false)

            val maxImageSize = 2048

            bitmap = BitmapUtils.decodeBitmapScaled(file.path, maxImageSize, maxImageSize)

            if (data.rotation != 0){
                val rotated = bitmap.rotate(data.rotation.toFloat())
                bitmap.recycle()
                bitmap = rotated
            }

            val width = bitmap.width
            val height = bitmap.height
            val bounds = data.boundary(width.toFloat(), height.toFloat()) ?: return false

            val projections = mapOf(
                MapProjectionType.Mercator to "Mercator",
                MapProjectionType.CylindricalEquidistant to "Equidistant_Cylindrical"
            )

            val pcjcs = ProjectedCoordinateSystem(
                GeographicCoordinateSystem(
                    Datum(
                        "WGS 84",
                        Spheroid(
                            "WGS 84",
                            ReferenceEllipsoid.wgs84.semiMajorAxis.toFloat(),
                            ReferenceEllipsoid.wgs84.inverseFlattening.toFloat()
                        )
                    ),
                    0.0
                ),
                projections[data.projection] ?: ""
            )

            val pdf = listOf(
                catalog("1 0", "2 0"),
                pages("2 0", listOf("3 0")),
                page("3 0", "2 0", width, height, listOf("4 0"), listOf("5 0")),
                image("4 0", bitmap, destWidth = width, destHeight = height),
                viewport("5 0", "6 0", bbox(0, 0, width, height)),
                geo(
                    "6 0",
                    listOf(bounds.southWest, bounds.northWest, bounds.northEast, bounds.southEast),
                    gcsId = "7 0"
                ),
                gcs("7 0", pcjcs)
            )
            bitmap.recycle()
            val uri = uriPicker.create(filename, "application/pdf") ?: return false
            uriService.outputStream(uri)?.use {
                PdfConvert.toPDF(pdf, it)
            }
            return true
        } catch (e: Exception) {
            throw e
        } finally {
            bitmap?.recycle()
        }
    }

}