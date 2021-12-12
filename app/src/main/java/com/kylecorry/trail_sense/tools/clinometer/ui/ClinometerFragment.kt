package com.kylecorry.trail_sense.tools.clinometer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.camera.Camera
import com.kylecorry.andromeda.core.sensors.asLiveData
import com.kylecorry.andromeda.core.time.Throttle
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.sense.orientation.DeviceOrientation
import com.kylecorry.sol.math.InclinationService
import com.kylecorry.sol.math.SolMath
import com.kylecorry.sol.math.geometry.Size
import com.kylecorry.sol.science.geology.AvalancheRisk
import com.kylecorry.sol.science.geology.GeologyService
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentClinometerBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.tools.clinometer.infrastructure.CameraClinometer
import com.kylecorry.trail_sense.tools.clinometer.infrastructure.Clinometer
import com.kylecorry.trail_sense.tools.clinometer.infrastructure.SideClinometer
import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

class ClinometerFragment : BoundFragment<FragmentClinometerBinding>() {

    private val sensorService by lazy { SensorService(requireContext()) }
    private val cameraClinometer by lazy { CameraClinometer(requireContext()) }
    private val camera by lazy {
        Camera(
            requireContext(),
            viewLifecycleOwner,
            previewView = binding.cameraView,
            analyze = false
        )
    }
    private val angleLocator by lazy {
        val f = getFocalLength()
        val s = getSensorSize()
        if (f != null && s != null){
            ImageAngleCalculator(f, s, if (s.width > s.height) 90 else 0)
        } else {
            null
        }
    }
    private val sideClinometer by lazy { SideClinometer(requireContext()) }
    private val deviceOrientation by lazy { sensorService.getDeviceOrientationSensor() }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val geology = GeologyService()
    private val inclinationService = InclinationService()
    private val formatter by lazy { FormatService(requireContext()) }
    private val throttle = Throttle(20)

    private lateinit var clinometer: Clinometer

    private var slopeIncline: Float? = null
    private var slopeAngle: Float? = null
    private var startIncline: Float = 0f
    private var touchTime = Instant.now()

    private var distanceAway: Distance? = null

    private var useCamera = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clinometer = getClinometer()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        CustomUiUtils.setButtonState(binding.clinometerLeftQuickAction, false)
        CustomUiUtils.setButtonState(binding.clinometerRightQuickAction, false)

        val units = if (prefs.distanceUnits == UserPreferences.DistanceUnits.Meters) {
            listOf(DistanceUnits.Meters, DistanceUnits.Feet)
        } else {
            listOf(DistanceUnits.Feet, DistanceUnits.Meters)
        }

        binding.clinometerLeftQuickAction.setOnClickListener {
            if (useCamera) {
                camera.stop(null)
                binding.clinometerLeftQuickAction.setImageResource(R.drawable.ic_camera)
                CustomUiUtils.setButtonState(binding.clinometerLeftQuickAction, false)
                useCamera = false
                clinometer = getClinometer()
            } else {
                requestPermissions(listOf(Manifest.permission.CAMERA)) {
                    if (Camera.isAvailable(requireContext())) {
                        useCamera = true
                        camera.start {
                            true
                        }
                        binding.clinometerLeftQuickAction.setImageResource(R.drawable.ic_screen_flashlight)
                        CustomUiUtils.setButtonState(binding.clinometerLeftQuickAction, false)
                        clinometer = getClinometer()
                    } else {
                        Alerts.toast(
                            requireContext(),
                            getString(R.string.camera_permission_denied),
                            short = false
                        )
                    }
                }
            }
        }

        binding.clinometerRightQuickAction.setOnClickListener {
            CustomUiUtils.pickDistance(
                requireContext(),
                units,
                distanceAway,
                getString(R.string.distance_away)
            ) {
                if (it != null) {
                    distanceAway = it
                    CustomUiUtils.setButtonState(binding.clinometerRightQuickAction, true)
                }
            }
        }

        // TODO: Make this discoverable by the user
        binding.root.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (isOrientationValid() && slopeIncline == null) {
                    touchTime = Instant.now()
                    startIncline = clinometer.incline
                    binding.cameraClinometer.startAngle = clinometer.angle
                    binding.clinometer.startAngle = clinometer.angle
                } else {
                    startIncline = 0f
                    binding.cameraClinometer.startAngle = null
                    binding.clinometer.startAngle = null
                }
            } else if (event.action == MotionEvent.ACTION_UP) {
                if (Duration.between(touchTime, Instant.now()) < Duration.ofMillis(500)) {
                    startIncline = 0f
                    binding.clinometer.startAngle = null
                    binding.cameraClinometer.startAngle = null
                }

                if (slopeIncline == null && isOrientationValid()) {
                    slopeAngle = clinometer.angle
                    slopeIncline = clinometer.incline
                } else {
                    startIncline = 0f
                    binding.cameraClinometer.startAngle = null
                    binding.clinometer.startAngle = null
                    slopeAngle = null
                    slopeIncline = null
                }
            }
            true
        }

        sideClinometer.asLiveData().observe(viewLifecycleOwner, { updateUI() })
        cameraClinometer.asLiveData().observe(viewLifecycleOwner, { updateUI() })
        deviceOrientation.asLiveData().observe(viewLifecycleOwner, { updateUI() })

    }

    override fun onPause() {
        super.onPause()
        if (useCamera) {
            camera.stop(null)
            useCamera = false
        }
    }

    private fun getClinometer(): Clinometer {
        return if (useCamera) {
            cameraClinometer
        } else {
            sideClinometer
        }
    }

    private fun updateUI() {

        if (throttle.isThrottled()) {
            return
        }

        binding.lock.isVisible = slopeAngle != null

        if (!isOrientationValid() && slopeAngle == null) {
            binding.clinometerInstructions.text = getString(R.string.clinometer_rotate_device)
            return
        }

        binding.cameraView.isVisible = useCamera
        binding.cameraClinometer.isVisible = useCamera
        binding.clinometer.isInvisible = useCamera

        binding.clinometerInstructions.text = getString(R.string.set_inclination_instructions)

        val angle = slopeAngle ?: clinometer.angle
        val incline = slopeIncline ?: clinometer.incline

        val avalancheRisk = geology.getAvalancheRisk(incline)

        binding.clinometer.angle = angle
        binding.cameraClinometer.angle = clinometer.angle
        binding.cameraClinometer.endAngle = slopeAngle
        if (useCamera) {
            binding.cameraClinometer.imageAngleCalculator = angleLocator
        }

        binding.inclination.text = formatter.formatDegrees(incline)
        binding.avalancheRisk.title = getAvalancheRiskString(avalancheRisk)

        binding.inclinationDescription.text =
            getString(R.string.slope_amount, formatter.formatPercentage(getSlopePercent(incline)))

        val distance = distanceAway
        binding.estimatedHeight.title = if (distance != null) {
            formatter.formatDistance(
                getHeight(
                    distance,
                    min(startIncline, incline),
                    max(startIncline, incline)
                )
            )
        } else {
            getString(R.string.distance_unset)
        }

    }

    private fun getSlopePercent(incline: Float): Float {
        return SolMath.tanDegrees(incline) * 100
    }

    private fun getAvalancheRiskString(risk: AvalancheRisk): String {
        return when (risk) {
            AvalancheRisk.Low -> getString(R.string.low)
            AvalancheRisk.Moderate -> getString(R.string.moderate)
            AvalancheRisk.High -> getString(R.string.high)
        }
    }

    private fun isOrientationValid(): Boolean {
        val invalidOrientations = if (useCamera) {
            listOf(
                DeviceOrientation.Orientation.Landscape,
                DeviceOrientation.Orientation.LandscapeInverse
            )
        } else {
            listOf(DeviceOrientation.Orientation.Flat, DeviceOrientation.Orientation.FlatInverse)
        }

        return !invalidOrientations.contains(deviceOrientation.orientation)
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentClinometerBinding {
        return FragmentClinometerBinding.inflate(layoutInflater, container, false)
    }

    private fun getHeight(distanceAway: Distance, bottom: Float, top: Float): Distance {
        return Distance.meters(
            inclinationService.estimateHeightAngles(
                distanceAway.meters().distance,
                if ((top - bottom).absoluteValue < 3f) 0f else bottom,
                top
            )
        ).convertTo(distanceAway.units)
    }


    // TODO: Extract these to the camera class

    private fun getFocalLength(): Float? {
        val isBackCamera = true
        val manager = requireContext().getSystemService<CameraManager>() ?: return null
        try {
            val desiredOrientation =
                if (isBackCamera) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val orientation = characteristics.get(CameraCharacteristics.LENS_FACING)!!
                if (orientation == desiredOrientation) {
                    val maxFocus =
                        characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    return maxFocus!![0]
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun getSensorSize(): Size? {
        val isBackCamera = true
        val manager = requireContext().getSystemService<CameraManager>() ?: return null
        try {
            val desiredOrientation =
                if (isBackCamera) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val orientation = characteristics.get(CameraCharacteristics.LENS_FACING)!!
                if (orientation == desiredOrientation) {
                    val size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        ?: return null
                    return Size(size.width, size.height)
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }


}