package com.kylecorry.trail_sense.navigation.beacons.ui

import android.content.res.ColorStateList
import android.view.View
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.ListItemBeaconBinding
import com.kylecorry.trail_sense.navigation.beacons.domain.BeaconGroup
import com.kylecorry.trail_sense.navigation.beacons.infrastructure.commands.MoveBeaconGroupCommand
import com.kylecorry.trail_sense.navigation.beacons.infrastructure.commands.RenameBeaconGroupCommand
import com.kylecorry.trail_sense.navigation.beacons.infrastructure.persistence.BeaconService
import com.kylecorry.trail_sense.shared.colors.AppColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BeaconGroupListItem(
    private val view: View,
    private val scope: CoroutineScope,
    private val group: BeaconGroup
) {

    var onOpen: () -> Unit = {}
    var onDeleted: () -> Unit = {}
    var onRenamed: () -> Unit = {}
    var onMoved: () -> Unit = {}

    private val service by lazy { BeaconService(view.context) }

    init {
        val binding = ListItemBeaconBinding.bind(view)

        binding.beaconName.text = group.name
        binding.beaconImage.setImageResource(R.drawable.ic_beacon_group)
        binding.beaconImage.imageTintList = ColorStateList.valueOf(AppColor.Orange.color)
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                service.getBeaconCount(group.id)
            }

            withContext(Dispatchers.Main) {
                binding.beaconSummary.text = view.context.resources.getQuantityString(
                    R.plurals.beacon_group_summary,
                    count,
                    count
                )
            }
        }

        binding.visibleBtn.visibility = View.GONE

        view.setOnClickListener {
            onOpen()
        }

        binding.beaconMenuBtn.setOnClickListener {
            Pickers.menu(it, R.menu.beacon_group_item_menu) {
                when (it) {
                    R.id.action_edit_beacon_group -> {
                        val command = RenameBeaconGroupCommand(view.context, scope, service, onRenamed)
                        command.execute(group)
                    }
                    R.id.action_move_beacon_group -> {
                        val command = MoveBeaconGroupCommand(view.context, scope, service, onMoved)
                        command.execute(group)
                    }
                    R.id.action_delete_beacon_group -> {
                        Alerts.dialog(
                            view.context,
                            view.context.getString(R.string.delete),
                            view.context.getString(
                                R.string.delete_beacon_group_message,
                                group.name
                            ),
                        ) { cancelled ->
                            if (!cancelled) {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        service.delete(group)
                                    }

                                    withContext(Dispatchers.Main) {
                                        onDeleted()
                                    }
                                }

                            }
                        }
                    }
                }
                true
            }
        }

    }


}