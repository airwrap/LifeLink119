package com.lifelink119.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/** Shared native presentation tokens. Dispatch, consent and driving policies live elsewhere. */
internal object LifeLinkUiStyle {
    val background = Color.rgb(13, 25, 31)
    val chrome = Color.rgb(22, 42, 49)
    val surface = Color.rgb(32, 54, 62)
    // Light controls on dark chrome make the whole touch target discoverable.
    val control = Color.rgb(210, 221, 232)
    val raised = Color.rgb(48, 58, 72)
    val ink = Color.rgb(243, 245, 248)
    val secondary = Color.rgb(196, 205, 216)
    val border = Color.rgb(137, 150, 168)
    val panelBorder = Color.rgb(66, 94, 102)
    // Text accents and filled actions need different luminance in a dark interface.
    val accent = Color.rgb(171, 220, 212)
    val primaryFill = Color.rgb(242, 240, 232)
    val actionInk = Color.rgb(24, 35, 48)
    val actionSecondary = Color.rgb(68, 81, 100)
    val disabledInk = Color.rgb(169, 180, 193)
    val disabledFill = Color.rgb(49, 60, 73)
    val focusInk = Color.rgb(32, 74, 117)
    val emergency = Color.rgb(242, 169, 162)
    val emergencyFill = Color.rgb(245, 181, 164)
    val emergencyActionInk = Color.rgb(89, 33, 27)
    val medicalFill = Color.rgb(203, 230, 219)
    val medicalInk = Color.rgb(27, 66, 57)
    val counselFill = Color.rgb(219, 217, 236)
    val counselInk = Color.rgb(62, 50, 85)
    val policeFill = Color.rgb(212, 231, 255)
    val policeActionInk = Color.rgb(24, 69, 132)
    val warning = Color.rgb(235, 198, 142)
    val notice = Color.rgb(52, 43, 31)

    // Preserve large automotive touch targets; web-sized skill defaults do not apply here.
    const val touchHeight = 96
    const val contentWidth = 1600
    const val actionTextSize = 28f
    const val controlRadius = 12
    const val panelRadius = 16

    fun spacing(value: Int) = if (value <= 0) 0 else ((value + 2) / 4) * 4

    private val regular = Typeface.create(Typeface.SANS_SERIF, 400, false)
    private val medium = Typeface.create(Typeface.SANS_SERIF, 500, false)

    fun typeface(emphasis: Boolean = false): Typeface = if (emphasis) medium else regular

    fun actionText(color: Int) =
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(disabledInk, color),
        )

    fun actionForeground(fill: Int) =
        when (fill) {
            emergencyFill -> emergencyActionInk
            policeFill -> policeActionInk
            else -> actionInk
        }

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density).toInt()

    fun shape(context: Context, color: Int, radius: Int = panelRadius) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radius).toFloat()
            setStroke(
                dp(context, 1),
                when (color) {
                    control -> border
                    surface -> panelBorder
                    else -> color
                },
            )
        }

    private fun actionShape(
        context: Context,
        color: Int,
        radius: Int,
        focused: Boolean,
        pressed: Boolean,
    ) =
        shape(
                context,
                if (!pressed) color
                else
                    Color.rgb(
                        (Color.red(color) * .90f).toInt(),
                        (Color.green(color) * .90f).toInt(),
                        (Color.blue(color) * .90f).toInt(),
                    ),
                radius,
            )
            .apply {
                if (focused) setStroke(dp(context, 4), if (color == surface) accent else focusInk)
            }

    fun actionBackground(context: Context, color: Int, radius: Int = controlRadius) =
        StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                shape(context, disabledFill, radius),
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                actionShape(context, color, radius, true, true),
            )
            addState(
                intArrayOf(android.R.attr.state_focused),
                actionShape(context, color, radius, true, false),
            )
            addState(intArrayOf(), actionShape(context, color, radius, false, false))
            setEnterFadeDuration(100)
            setExitFadeDuration(120)
        }
}
