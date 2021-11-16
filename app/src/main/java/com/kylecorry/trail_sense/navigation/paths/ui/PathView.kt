package com.kylecorry.trail_sense.navigation.paths.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.kylecorry.andromeda.canvas.CanvasView
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.sol.math.SolMath
import com.kylecorry.sol.math.SolMath.cosDegrees
import com.kylecorry.sol.math.SolMath.power
import com.kylecorry.sol.math.SolMath.sinDegrees
import com.kylecorry.sol.math.SolMath.wrap
import com.kylecorry.sol.science.geology.GeologyService
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.navigation.paths.domain.LineStyle
import com.kylecorry.trail_sense.navigation.paths.domain.PathPoint
import com.kylecorry.trail_sense.navigation.paths.domain.waypointcolors.IPointColoringStrategy
import com.kylecorry.trail_sense.navigation.paths.domain.waypointcolors.NoDrawPointColoringStrategy
import com.kylecorry.trail_sense.navigation.paths.ui.drawing.PathLineDrawerFactory
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.canvas.PixelCircle
import com.kylecorry.trail_sense.shared.toCanvasPath
import kotlin.math.floor
import kotlin.math.log10


// TODO: Fix grid with scaling or replace with a distance bar
class PathView(context: Context, attrs: AttributeSet? = null) : CanvasView(context, attrs) {

    // TODO: Update this to use a mappable path
    var pointColoringStrategy: IPointColoringStrategy = NoDrawPointColoringStrategy()
        set(value) {
            field = value
            invalidate()
        }

    var path: List<PathPoint> = emptyList()
        set(value) {
            field = value
            pathInitialized = false
            invalidate()
        }

    private var pathInitialized = false
    private var drawnPath = Path()

    var location: Coordinate? = null
        set(value) {
            field = value
            invalidate()
        }

    var azimuth: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var pointClickListener: (point: PathPoint) -> Unit = {}
    private var pathCircles: List<Pair<PathPoint, PixelCircle>> = listOf()

    private val prefs by lazy { UserPreferences(context) }
    private val formatService by lazy { FormatService(context) }
    var pathColor = Color.BLACK
    var pathStyle = LineStyle.Dotted
    private val geoService = GeologyService()
    private var metersPerPixel: Float = 1f
    private var center: Coordinate = Coordinate.zero

    private var translateX = 0f
    private var translateY = 0f
    private var scale = 1f

    init {
        runEveryCycle = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pathInitialized = false
    }

    override fun setup() {
        recenter()
    }

    override fun draw() {
        clear()
        push()
        translate(translateX, translateY)
        scale(scale, scale, width / 2f, height / 2f)
        drawMap()
        pop()
    }

    fun setOnPointClickListener(listener: (point: PathPoint) -> Unit) {
        pointClickListener = listener
    }

    private fun drawMap() {
        val bounds = geoService.getBounds(path.map { it.coordinate })

        val distanceX = bounds.width().meters().distance
        val distanceY = bounds.height().meters().distance

        if (distanceX == 0f || distanceY == 0f) {
            return
        }

        val h = height.toFloat() - dp(32f)
        val w = width.toFloat() - dp(32f)
        val scale = SolMath.scaleToFit(distanceX, distanceY, w, h)
        metersPerPixel = 1 / scale
        center = bounds.center

        val gridGap = getGridSize(Distance.meters(distanceX))
        // TODO: The grid shouldn't move - only the path (or the grid should move and scale and always remain on screen)
        drawGrid(metersPerPixel, gridGap.meters().distance)
        drawLegend(gridGap)

        drawPaths()
        drawWaypoints(path)
        location?.let {
            drawLocation(getPixels(it))
        }
    }

    private fun getGridSize(distance: Distance): Distance {
        val baseUnits = prefs.baseDistanceUnits

        val d = distance.meters().distance

        if (d == 0f) {
            return Distance(1f, baseUnits)
        }

        val exponent = (floor(log10(d / 5f))).coerceAtLeast(1f).toInt()

        return if (baseUnits == DistanceUnits.Meters) {
            Distance.meters(power(10, exponent).toFloat())
        } else {
            Distance.feet(power(10, exponent) * 3f)
        }
    }

    private fun drawWaypoints(points: List<PathPoint>) {
        val pointDiameter = dp(5f)
        noPathEffect()
        noStroke()
        val circles = mutableListOf<Pair<PathPoint, PixelCircle>>()
        for (point in points) {
            val color = pointColoringStrategy.getColor(point) ?: continue
            if (color == Color.TRANSPARENT){
                continue
            }
            fill(color)
            val position = getPixels(point.coordinate)
            circle(position.x, position.y, pointDiameter)
            circles.add(
                point to PixelCircle(
                    PixelCoordinate(position.x, position.y),
                    pointDiameter
                )
            )
        }

        pathCircles = circles
    }

    private fun drawLegend(gridGap: Distance) {
        textMode(TextMode.Corner)
        textSize(sp(14f))
        strokeWeight(0f)
        fill(Color.WHITE)
        val distanceText = context.getString(
            R.string.grid_size,
            formatService.formatDistance(gridGap)
        )
        val textWidth = textWidth(distanceText)
        text(distanceText, width - textWidth - dp(16f), height.toFloat() - dp(16f))
    }

    private fun drawGrid(
        metersPerPixel: Float,
        gap: Float
    ) {
        noFill()
        stroke(Color.WHITE)
        strokeWeight(dp(0.5f))
        opacity(50)
        grid(gap / metersPerPixel)
        opacity(255)
    }

    private fun drawLocation(pixels: PixelCoordinate) {
        stroke(Color.WHITE)
        strokeWeight(dp(1f))
        fill(Resources.color(context, R.color.colorPrimary))
        push()
        rotate(azimuth, pixels.x, pixels.y)
        triangle(
            pixels.x, pixels.y - dp(6f),
            pixels.x - dp(5f), pixels.y + dp(6f),
            pixels.x + dp(5f), pixels.y + dp(6f)
        )
        pop()
    }

    private fun drawPaths() {
        if (!pathInitialized) {
            drawnPath.reset()
            this.path.map { it.coordinate }.toCanvasPath(drawnPath) { getPixels(it) }
            pathInitialized = true
        }

        val lineDrawerFactory = PathLineDrawerFactory()
        val drawer = lineDrawerFactory.create(pathStyle)
        clear()
        drawer.draw(this, pathColor, scale) {
            path(drawnPath)
        }
        noStroke()
        fill(Color.WHITE)
        noPathEffect()
    }

    private fun getPixels(
        location: Coordinate
    ): PixelCoordinate {
        val distance = center.distanceTo(location)
        val bearing = center.bearingTo(location)
        val angle = wrap(-(bearing.value - 90), 0f, 360f)
        val pixelDistance = distance / metersPerPixel
        val xDiff = cosDegrees(angle.toDouble()).toFloat() * pixelDistance
        val yDiff = sinDegrees(angle.toDouble()).toFloat() * pixelDistance
        return PixelCoordinate(width / 2f + xDiff, height / 2f - yDiff)
    }

    fun recenter(){
        translateX = 0f
        translateY = 0f
        scale = 1f
    }

    private fun zoom(factor: Float) {
        scale *= factor
        translateX *= factor
        translateY *= factor
    }

    private val mGestureListener = object : GestureDetector.SimpleOnGestureListener() {

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            translateX -= distanceX
            translateY -= distanceY
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // TODO: Zoom to the place tapped
            zoom(2F)
            return super.onDoubleTap(e)
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val screenCoord = PixelCoordinate(e.x, e.y)

            val tapRadius = dp(12f)
            val closest = pathCircles.minByOrNull { it.second.center.distanceTo(screenCoord) }

            if (closest != null && closest.second.center.distanceTo(screenCoord) < tapRadius) {
                pointClickListener.invoke(closest.first)
            }

            return super.onSingleTapConfirmed(e)
        }
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom(detector.scaleFactor)
            return true
        }
    }

    private val gestureDetector = GestureDetector(context, mGestureListener)
    private val mScaleDetector = ScaleGestureDetector(context, scaleListener)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        invalidate()
        return true
    }
}