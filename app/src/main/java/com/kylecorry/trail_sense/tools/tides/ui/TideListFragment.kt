package com.kylecorry.trail_sense.tools.tides.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentTideListBinding
import com.kylecorry.trail_sense.databinding.ListItemPlainMenuBinding
import com.kylecorry.trail_sense.shared.BoundFragment
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatServiceV2
import com.kylecorry.trail_sense.tools.tides.domain.TideEntity
import com.kylecorry.trail_sense.tools.tides.infrastructure.persistence.TideRepo
import com.kylecorry.trailsensecore.domain.oceanography.OceanographyService
import com.kylecorry.trailsensecore.domain.oceanography.TideType
import com.kylecorry.trailsensecore.infrastructure.view.ListView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TideListFragment: BoundFragment<FragmentTideListBinding>() {

    private lateinit var listView: ListView<TideEntity>
    private val formatService by lazy { FormatServiceV2(requireContext()) }
    private val oceanographyService = OceanographyService()
    private val tideRepo by lazy { TideRepo.getInstance(requireContext()) }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentTideListBinding {
        return FragmentTideListBinding.inflate(layoutInflater, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView = ListView(binding.tideList, R.layout.list_item_plain_menu){ listItemView, tide ->
            val itemBinding = ListItemPlainMenuBinding.bind(listItemView)
            itemBinding.title.text = tide.name ?: if (tide.coordinate != null) formatService.formatLocation(tide.coordinate!!) else getString(R.string.untitled_tide)
            itemBinding.description.text = getTideTypeName(oceanographyService.getTideType(tide.reference))
            itemBinding.root.setOnClickListener {
                editTide(tide)
            }
            
            itemBinding.menuBtn.setOnClickListener {
                CustomUiUtils.openMenu(it, R.menu.tide_menu){ action ->
                    when (action){
                        R.id.action_tide_delete -> {
                            deleteTide(tide)
                        }
                        R.id.action_tide_edit -> {
                            editTide(tide)
                        }
                    }
                    true
                }
            }
        }

        listView.addLineSeparator()

        tideRepo.getTides().observe(viewLifecycleOwner, {
            listView.setData(it)
            binding.tidesEmptyText.isVisible = it.isEmpty()
        })

        binding.addBtn.setOnClickListener {
            createTide()
        }
    }

    private fun deleteTide(tide: TideEntity){
        // Prompt for confirmation
        lifecycleScope.launch {
            withContext(Dispatchers.IO){
                tideRepo.deleteTide(tide)
            }
        }
    }

    private fun editTide(tide: TideEntity){
        // TODO open the tide create page
    }

    private fun createTide(){
        // TODO open the tide create page
    }

    private fun selectTide(){
        // TODO set the tide selection and navigate back to the tide page
    }

    private fun getTideTypeName(tideType: TideType): String {
        return when (tideType) {
            TideType.High -> getString(R.string.high_tide)
            TideType.Low -> getString(R.string.low_tide)
            TideType.Half -> getString(R.string.half_tide)
        }
    }

}