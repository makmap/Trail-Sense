package com.kylecorry.trail_sense.tools.tides.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.core.time.Timer
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.list.ListView
import com.kylecorry.sol.science.oceanography.Tide
import com.kylecorry.sol.time.Time.toZonedDateTime
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentCreateTideBinding
import com.kylecorry.trail_sense.databinding.ListItemTideEntryBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.extensions.promptIfUnsavedChanges
import com.kylecorry.trail_sense.tools.guide.infrastructure.UserGuideUtils
import com.kylecorry.trail_sense.tools.tides.domain.TideTable
import com.kylecorry.trail_sense.tools.tides.domain.TideTableIsDirtySpecification
import com.kylecorry.trail_sense.tools.tides.infrastructure.persistence.TideTableRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZonedDateTime

class CreateTideFragment : BoundFragment<FragmentCreateTideBinding>() {

    private val formatService by lazy { FormatService(requireContext()) }
    private var editingId: Long = 0
    private var editingTide: TideTable? = null

    private lateinit var tideTimesList: ListView<TideEntry>
    private var tides = mutableListOf<TideEntry>()

    private val tideRepo by lazy { TideTableRepo.getInstance(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val units by lazy { prefs.baseDistanceUnits }

    private var backCallback: OnBackPressedCallback? = null

    private val intervalometer = Timer {
        binding.createTideBtn.isVisible = formIsValid()
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCreateTideBinding {
        return FragmentCreateTideBinding.inflate(layoutInflater, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingId = arguments?.getLong("edit_tide_id") ?: 0L
    }

    override fun onResume() {
        super.onResume()
        intervalometer.interval(20)
    }

    override fun onPause() {
        intervalometer.stop()
        super.onPause()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.createTideTitle.rightQuickAction.setOnClickListener {
            UserGuideUtils.showGuide(this, R.raw.tides)
        }

        tideTimesList = ListView(binding.tideTimes, R.layout.list_item_tide_entry) { view, tide ->
            val itemBinding = ListItemTideEntryBinding.bind(view)

            itemBinding.tideType.text =
                if (tide.isHigh) getString(R.string.high_tide_letter) else getString(
                    R.string.low_tide_letter
                )

            itemBinding.tideType.setOnClickListener {
                tide.isHigh = !tide.isHigh
                itemBinding.tideType.text =
                    if (tide.isHigh) getString(R.string.high_tide_letter) else getString(
                        R.string.low_tide_letter
                    )
            }

            itemBinding.delete.setOnClickListener {
                tides.remove(tide)
                tideTimesList.setData(tides)
                CustomUiUtils.snackbar(
                    this,
                    getString(R.string.tide_deleted),
                    action = getString(R.string.undo)
                ) {
                    tides.add(tide)
                    tideTimesList.setData(tides)
                }
            }

            itemBinding.tideTime.text = getString(R.string.time_not_set)

            tide.time?.let {
                itemBinding.tideTime.text = formatService.formatDateTime(
                    it,
                    false,
                    abbreviateMonth = true
                )
            }

            itemBinding.tideTime.setOnClickListener {
                CustomUiUtils.pickDatetime(
                    requireContext(),
                    prefs.use24HourTime,
                    tide.time?.toLocalDateTime() ?: LocalDateTime.now()
                ) {
                    if (it != null) {
                        tide.time = it.toZonedDateTime()
                        itemBinding.tideTime.text = formatService.formatDateTime(
                            it.toZonedDateTime(),
                            false,
                            abbreviateMonth = true
                        )
                    }
                }
            }

            val initialHeight = tide.height
            itemBinding.tideHeight.text = if (initialHeight == null) {
                getString(R.string.dash)
            } else {
                formatService.formatDistance(initialHeight, 2)
            }

            itemBinding.tideHeight.setOnClickListener {
                CustomUiUtils.pickDistance(
                    requireContext(),
                    formatService.sortDistanceUnits(
                        listOf(
                            DistanceUnits.Meters,
                            DistanceUnits.Feet
                        )
                    ),
                    tide.height,
                    getString(R.string.height)
                ) { distance, cancelled ->
                    if (!cancelled) {
                        tide.height = distance
                        itemBinding.tideHeight.text = if (distance == null) {
                            getString(R.string.dash)
                        } else {
                            formatService.formatDistance(distance, 2)
                        }
                    }
                }
            }
        }

        tideTimesList.addLineSeparator()

        tides.clear()
        if (editingId != 0L) {
            runInBackground {
                withContext(Dispatchers.IO) {
                    editingTide = tideRepo.getTideTable(editingId)
                }
                withContext(Dispatchers.Main) {
                    if (editingTide != null) {
                        fillExistingTideValues(editingTide!!)
                    } else {
                        editingId = 0L
                    }
                }
            }
        } else {
            tides.add(TideEntry(true, null, null))
            tideTimesList.setData(tides)
        }

        binding.addTideEntry.setOnClickListener {
            tides.add(TideEntry(true, null, null))
            tideTimesList.setData(tides)
            tideTimesList.scrollToPosition(tides.lastIndex)
        }

        binding.createTideBtn.setOnClickListener {
            val tide = getTide()
            if (tide != null) {
                runInBackground {
                    withContext(Dispatchers.IO) {
                        tideRepo.addTideTable(tide)
                    }

                    withContext(Dispatchers.Main) {
                        backCallback?.remove()
                        findNavController().navigateUp()
                    }
                }
            }
        }

        backCallback = promptIfUnsavedChanges(this::hasChanges)

    }


    private fun fillExistingTideValues(tide: TideTable) {
        binding.tideName.setText(tide.name)
        binding.tideLocation.coordinate = tide.location
        tides.addAll(tide.tides.map {
            val h = it.height
            TideEntry(
                it.isHigh,
                it.time,
                if (h != null) Distance.meters(h).convertTo(units) else null
            )
        })
        tideTimesList.setData(tides)
    }

    private fun formIsValid(): Boolean {
        return getTide() != null
    }

    private fun getTide(): TideTable? {
        val tides = tides.mapNotNull {
            val time = it.time ?: return@mapNotNull null
            Tide(
                time,
                it.isHigh,
                it.height?.meters()?.distance
            )
        }

        if (editingId != 0L && editingTide == null) {
            return null
        }

        if (tides.isEmpty()) {
            return null
        }

        val rawName = binding.tideName.text?.toString()
        val name = if (rawName.isNullOrBlank()) null else rawName
        val location = binding.tideLocation.coordinate

        return TideTable(
            editingId,
            tides,
            name,
            location
        )
    }

    private fun hasChanges(): Boolean {
        val specification = TideTableIsDirtySpecification(editingTide)
        return specification.isSatisfiedBy(getTide())
    }


    private data class TideEntry(
        var isHigh: Boolean,
        var time: ZonedDateTime?,
        var height: Distance?
    )

}