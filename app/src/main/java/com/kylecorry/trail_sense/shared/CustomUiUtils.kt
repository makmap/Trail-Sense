package com.kylecorry.trail_sense.shared

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Size
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.sensors.Quality
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.fragments.AndromedaFragment
import com.kylecorry.andromeda.fragments.show
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.preferences.Preferences
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.navigation.beacons.domain.Beacon
import com.kylecorry.trail_sense.shared.camera.PhotoImportBottomSheetFragment
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.shared.permissions.requestCamera
import com.kylecorry.trail_sense.shared.views.*
import com.kylecorry.trail_sense.tools.qr.ui.ScanQRBottomSheet
import com.kylecorry.trail_sense.tools.qr.ui.ViewQRBottomSheet
import java.time.Duration
import java.time.LocalDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object CustomUiUtils {

    fun getMenuItems(context: Context, @MenuRes id: Int): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        val p = PopupMenu(context, null)
        p.menuInflater.inflate(id, p.menu)
        val menu = p.menu
        for (i in 0 until menu.size()) {
            items.add(menu[i])
        }
        return items
    }

    fun setButtonState(button: ImageButton, state: Boolean) {
        setButtonState(
            button,
            state,
            Resources.getAndroidColorAttr(button.context, R.attr.colorPrimary),
            Resources.color(button.context, R.color.colorSecondary)
        )
    }

    private fun setButtonState(
        button: ImageButton,
        isOn: Boolean,
        @ColorInt primaryColor: Int,
        @ColorInt secondaryColor: Int
    ) {
        if (isOn) {
            setImageColor(button.drawable, secondaryColor)
            button.backgroundTintList = ColorStateList.valueOf(primaryColor)
        } else {
            setImageColor(button.drawable, Resources.androidTextColorSecondary(button.context))
            button.backgroundTintList =
                ColorStateList.valueOf(Resources.androidBackgroundColorSecondary(button.context))
        }
    }


    fun setButtonState(
        button: Button,
        isOn: Boolean
    ) {
        if (isOn) {
            button.setTextColor(
                Resources.color(button.context, R.color.colorSecondary)
            )
            button.backgroundTintList = ColorStateList.valueOf(
                Resources.getAndroidColorAttr(button.context, R.attr.colorPrimary)
            )
        } else {
            button.setTextColor(Resources.androidTextColorSecondary(button.context))
            button.backgroundTintList =
                ColorStateList.valueOf(Resources.androidBackgroundColorSecondary(button.context))
        }
    }

    @ColorInt
    fun getQualityColor(quality: Quality): Int {
        return when (quality) {
            Quality.Poor, Quality.Unknown -> AppColor.Red.color
            Quality.Moderate -> AppColor.Yellow.color
            Quality.Good -> AppColor.Green.color
        }
    }

    fun pickDistance(
        context: Context,
        units: List<DistanceUnits>,
        default: Distance? = null,
        title: String,
        showFeetAndInches: Boolean = false,
        onDistancePick: (distance: Distance?, cancelled: Boolean) -> Unit
    ) {
        val view = View.inflate(context, R.layout.view_distance_entry_prompt, null)
        var distance: Distance? = default
        val distanceInput = view.findViewById<DistanceInputView>(R.id.prompt_distance)
        distanceInput?.setOnValueChangeListener {
            distance = it
        }
        distanceInput?.units = units
        distanceInput?.value = default
        if (default == null) {
            distanceInput?.unit = units.firstOrNull()
        }

        distanceInput?.showFeetAndInches = showFeetAndInches

        Alerts.dialog(
            context,
            title,
            contentView = view
        ) { cancelled ->
            if (cancelled) {
                onDistancePick.invoke(null, true)
            } else {
                onDistancePick.invoke(distance, false)
            }
        }
    }

    fun pickColor(
        context: Context,
        default: AppColor? = null,
        title: String,
        onColorPick: (color: AppColor?) -> Unit
    ) {
        val view = View.inflate(context, R.layout.view_color_picker_prompt, null)
        val colorPicker = view.findViewById<ColorPickerView>(R.id.prompt_color_picker)
        var color = default
        colorPicker?.setOnColorChangeListener {
            color = it
        }
        colorPicker?.color = color

        Alerts.dialog(
            context,
            title,
            contentView = view
        ) { cancelled ->
            if (cancelled) {
                onColorPick.invoke(null)
            } else {
                onColorPick.invoke(color)
            }
        }
    }

    fun pickDuration(
        context: Context,
        default: Duration? = null,
        title: String,
        message: String? = null,
        showSeconds: Boolean = false,
        onDurationPick: (duration: Duration?) -> Unit
    ) {
        val view = View.inflate(context, R.layout.view_duration_entry_prompt, null)
        var duration: Duration? = default
        val durationMessage = view.findViewById<TextView>(R.id.prompt_duration_message)
        val durationInput = view.findViewById<DurationInputView>(R.id.prompt_duration)

        durationMessage.isVisible = !message.isNullOrBlank()
        durationMessage.text = message

        durationInput.showSeconds = showSeconds

        durationInput?.setOnDurationChangeListener {
            duration = it
        }
        durationInput?.updateDuration(default)

        Alerts.dialog(
            context,
            title,
            contentView = view
        ) { cancelled ->
            if (cancelled) {
                onDurationPick.invoke(null)
            } else {
                onDurationPick.invoke(duration)
            }
        }
    }

    fun pickBeacon(
        context: Context,
        title: String?,
        location: Coordinate,
        onBeaconPick: (beacon: Beacon?) -> Unit
    ) {
        val view = View.inflate(context, R.layout.view_beacon_select_prompt, null)
        val beaconSelect = view.findViewById<BeaconSelectView>(R.id.prompt_beacons)
        beaconSelect.location = location
        val alert =
            Alerts.dialog(context, title ?: "", contentView = view, okText = null) {
                onBeaconPick.invoke(beaconSelect.beacon)
            }
        beaconSelect?.setOnBeaconChangeListener {
            onBeaconPick.invoke(it)
            alert.dismiss()
        }
    }

    fun pickBeaconGroup(
        context: Context,
        title: String? = null,
        okText: String? = null,
        groupsToExclude: List<Long?> = emptyList(),
        initialGroup: Long? = null,
        onBeaconGroupPick: (cancelled: Boolean, groupId: Long?) -> Unit
    ) {
        val view = View.inflate(context, R.layout.view_beacon_group_select_prompt, null)
        val beaconSelect = view.findViewById<BeaconGroupSelectView>(R.id.prompt_beacon_groups)
        if (groupsToExclude.isNotEmpty()) {
            beaconSelect.groupFilter = groupsToExclude
        }
        initialGroup?.let {
            beaconSelect.loadGroup(initialGroup)
        }
        Alerts.dialog(
            context,
            title ?: "",
            contentView = view,
            okText = okText ?: context.getString(android.R.string.ok)
        ) { cancelled ->
            onBeaconGroupPick.invoke(cancelled, beaconSelect.group?.id)
        }
    }

    fun disclaimer(
        context: Context,
        title: String,
        message: String,
        shownKey: String,
        okText: String = context.getString(android.R.string.ok),
        cancelText: String = context.getString(android.R.string.cancel),
        considerShownIfCancelled: Boolean = true,
        shownValue: Boolean = true,
        onClose: (cancelled: Boolean) -> Unit = {}
    ) {
        val prefs = Preferences(context)
        if (prefs.getBoolean(shownKey) != shownValue) {
            if (considerShownIfCancelled) {
                Alerts.dialog(context, title, message, okText = okText, cancelText = null) {
                    prefs.putBoolean(shownKey, shownValue)
                    onClose(false)
                }
            } else {
                Alerts.dialog(
                    context,
                    title,
                    message,
                    okText = okText,
                    cancelText = cancelText
                ) { cancelled ->
                    if (!cancelled) {
                        prefs.putBoolean(shownKey, shownValue)
                    }
                    onClose(cancelled)
                }
            }
        } else {
            onClose(false)
        }
    }

    fun showImage(
        context: Context,
        title: String,
        @DrawableRes image: Int
    ) {
        val view = LinearLayout(context)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER
        view.layoutParams = params
        val imageView = ImageView(context)
        val imageParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        imageParams.gravity = Gravity.CENTER
        imageView.layoutParams = imageParams
        imageView.setImageResource(image)
        view.addView(imageView)

        Alerts.dialog(context, title, contentView = view, cancelText = null)
    }

    fun setImageColor(view: ImageView, @ColorInt color: Int?) {
        if (color == null) {
            view.clearColorFilter()
            return
        }
        view.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun setImageColor(drawable: Drawable, @ColorInt color: Int?) {
        if (color == null) {
            drawable.clearColorFilter()
            return
        }
        drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun setImageColor(textView: TextView, @ColorInt color: Int?) {
        textView.compoundDrawables.forEach {
            it?.let { setImageColor(it, color) }
        }
    }

    fun snackbar(
        fragment: Fragment,
        text: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        action: String? = null,
        onAction: () -> Unit = {}
    ): Snackbar {
        return Snackbar.make(fragment.requireView(), text, duration).also {
            if (action != null) {
                it.setAction(action) {
                    onAction()
                }
            }
            it.setAnchorView(R.id.bottom_navigation)
            it.show()
        }
    }

    fun showQR(
        fragment: Fragment,
        title: String,
        qr: String
    ): BottomSheetDialogFragment {
        val sheet = ViewQRBottomSheet(title, qr)
        sheet.show(fragment)
        return sheet
    }

    fun scanQR(
        fragment: Fragment,
        title: String,
        onScan: (text: String?) -> Boolean
    ): BottomSheetDialogFragment {
        val sheet = ScanQRBottomSheet(title, onScan)
        sheet.show(fragment)
        return sheet
    }

    suspend fun takePhoto(fragment: AndromedaFragment, size: Size? = null): Uri? =
        suspendCoroutine { cont ->
            takePhoto(fragment, size) {
                cont.resume(it)
            }
        }

    fun takePhoto(
        fragment: AndromedaFragment,
        size: Size? = null,
        onCapture: (uri: Uri?) -> Unit
    ) {
        fragment.requestCamera {
            if (!it) {
                onCapture(null)
                return@requestCamera
            }

            val sheet = PhotoImportBottomSheetFragment(size) {
                onCapture(it)
            }

            sheet.show(fragment)
        }
    }

    fun pickDatetime(
        context: Context,
        use24Hours: Boolean,
        default: LocalDateTime = LocalDateTime.now(),
        onDatetimePick: (value: LocalDateTime?) -> Unit
    ) {
        Pickers.date(context, default.toLocalDate()) { date ->
            if (date != null) {
                Pickers.time(context, use24Hours, default.toLocalTime()) { time ->
                    if (time != null) {
                        onDatetimePick(LocalDateTime.of(date, time))
                    } else {
                        onDatetimePick(null)
                    }
                }
            } else {
                onDatetimePick(null)
            }
        }
    }

    fun TextView.setCompoundDrawables(
        size: Int? = null,
        @DrawableRes left: Int? = null,
        @DrawableRes top: Int? = null,
        @DrawableRes right: Int? = null,
        @DrawableRes bottom: Int? = null
    ) {
        val leftDrawable = if (left == null) null else Resources.drawable(context, left)
        val rightDrawable = if (right == null) null else Resources.drawable(context, right)
        val topDrawable = if (top == null) null else Resources.drawable(context, top)
        val bottomDrawable = if (bottom == null) null else Resources.drawable(context, bottom)

        leftDrawable?.setBounds(
            0,
            0,
            size ?: leftDrawable.intrinsicWidth,
            size ?: leftDrawable.intrinsicHeight
        )
        rightDrawable?.setBounds(
            0,
            0,
            size ?: rightDrawable.intrinsicWidth,
            size ?: rightDrawable.intrinsicHeight
        )
        topDrawable?.setBounds(
            0,
            0,
            size ?: topDrawable.intrinsicWidth,
            size ?: topDrawable.intrinsicHeight
        )
        bottomDrawable?.setBounds(
            0,
            0,
            size ?: bottomDrawable.intrinsicWidth,
            size ?: bottomDrawable.intrinsicHeight
        )

        setCompoundDrawables(leftDrawable, topDrawable, rightDrawable, bottomDrawable)

    }

    fun showLineChart(fragment: Fragment, title: String, populateFn: (LineChart) -> Unit) {
        val chartView = View.inflate(fragment.requireContext(), R.layout.view_chart_prompt, null)
        populateFn(chartView.findViewById(R.id.chart))
        Alerts.dialog(
            fragment.requireContext(),
            title,
            contentView = chartView,
            cancelText = null
        )
    }

}