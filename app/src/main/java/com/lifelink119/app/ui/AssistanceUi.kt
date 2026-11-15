package com.lifelink119.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lifelink119.app.R
import com.lifelink119.app.core.ConnectionTarget

/** Presentation only. Connection, privacy and vehicle restriction decisions stay in ExpansionUi. */
internal class AssistanceUi(
    private val context: Context,
    private val widthDp: Int,
    private val largeText: Boolean,
) {
    private val style = LifeLinkUiStyle
    private val compact = context.resources.configuration.screenHeightDp < 700
    private val fontFactor = if (largeText) 1.18f else 1f
    private val roomy =
        widthDp >= 1000 && !largeText && context.resources.configuration.fontScale <= 1.2f

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun label(value: String, size: Float, color: Int = style.ink, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size * fontFactor
            setTextColor(color)
            typeface = style.typeface(bold)
            includeFontPadding = false
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun LinearLayout.append(view: View, gap: Int = 0) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) })
    }

    private fun icon(resource: Int, tint: Int, size: Int) =
        ImageView(context).apply {
            setImageResource(resource)
            setColorFilter(tint)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun actionable(
        view: View,
        id: Int,
        description: String,
        fill: Int,
        action: () -> Unit,
    ) {
        view.id = id
        view.minimumHeight = dp(96)
        view.background = style.actionBackground(context, fill, 24)
        view.contentDescription = description
        view.isFocusable = true
        view.isClickable = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                }
            }
        view.setOnClickListener { action() }
    }

    fun home(
        emergency: () -> Unit,
        medical: () -> Unit,
        counsel: () -> Unit,
        police: () -> Unit,
        family: () -> Unit,
    ): View =
        column().apply {
            append(hero())
            val services =
                listOf(
                    serviceCard(
                        "119",
                        "긴급신고",
                        "화재 · 구조 · 구급",
                        R.drawable.ic_lifelink_phone,
                        R.id.x_emergency,
                        style.emergencyFill,
                        style.emergencyActionInk,
                        emergency,
                    ),
                    serviceCard(
                        "119",
                        "응급상담",
                        "응급처치가 궁금할 때",
                        R.drawable.ic_lifelink_medical,
                        R.id.x_medical,
                        style.medicalFill,
                        style.medicalInk,
                        medical,
                    ),
                    serviceCard(
                        "109",
                        "마음상담",
                        "자살예방 상담",
                        R.drawable.ic_lifelink_counsel,
                        R.id.x_109,
                        style.counselFill,
                        style.counselInk,
                        counsel,
                    ),
                )
            if (roomy) append(horizontal(services, listOf(1.2f, 1f, 1f)), 20)
            else {
                append(services[0], 16)
                append(horizontal(services.drop(1), stack = widthDp < 600 || largeText), 16)
            }
            append(
                horizontal(
                    listOf(
                        utility(
                            "112 경찰 신고",
                            "위협 · 범죄 상황",
                            R.drawable.ic_lifelink_shield,
                            R.id.x_home_112,
                            police,
                        ),
                        utility(
                            "가족 안심콜",
                            "가족 건강정보 관리",
                            R.drawable.ic_lifelink_family,
                            R.id.x_family,
                            family,
                        ),
                    ),
                    stack = widthDp < 600 || largeText,
                ),
                20,
            )
        }

    private fun hero(): View =
        FrameLayout(context).apply {
            val height = if (compact) 132 else 320
            val artWidth = minOf((widthDp * .55f).toInt(), height * 3 / 2)
            minimumHeight = dp(height)
            background = style.shape(context, style.chrome, 28)
            clipToOutline = true
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.hyundai_family_hero)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(dp(artWidth), dp(height), Gravity.END),
            )
            addView(
                View(context).apply {
                    background =
                        GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(style.chrome, Color.TRANSPARENT, Color.TRANSPARENT),
                        )
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(dp(artWidth), dp(height), Gravity.END),
            )
            val content =
                column().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(if (compact) 20 else 32), dp(20), dp(16), dp(20))
                    if (!compact) append(label("가족을 위한 차량형 응급지원", 19f, style.accent), 0)
                    append(
                        label("나와 내 가족을 위한\n구급차", if (compact) 30f else 44f, bold = true).apply {
                            isAccessibilityHeading = true
                        },
                        if (compact) 0 else 12,
                    )
                    if (!compact && widthDp >= 900)
                        append(label("도움이 필요한 순간, 가까이.", 22f, style.secondary), 16)
                }
            addView(
                content,
                FrameLayout.LayoutParams(
                    if (widthDp >= 760) dp((widthDp * .6f).toInt()) else -1,
                    -2,
                    Gravity.CENTER_VERTICAL,
                ),
            )
        }

    private fun serviceCard(
        number: String,
        title: String,
        subtitle: String,
        resource: Int,
        id: Int,
        fill: Int,
        ink: Int,
        action: () -> Unit,
    ): View =
        column().apply {
            actionable(this, id, "$number $title. $subtitle. 연결 시연 시작", fill, action)
            setPadding(dp(24), dp(20), dp(24), dp(24))
            minimumHeight = dp(if (compact) 188 else 252)
            val top =
                LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(icon(resource, ink, if (compact) 32 else 40))
                    addView(
                        label(number, if (compact) 40f else 52f, ink, true).apply {
                            gravity = Gravity.END
                        },
                        LinearLayout.LayoutParams(0, -2, 1f),
                    )
                }
            append(top)
            append(label(title, if (compact) 28f else 34f, ink, true), if (compact) 12 else 20)
            append(label(subtitle, 20f, ink), 8)
            for (i in 0 until childCount) getChildAt(i).importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

    private fun utility(
        title: String,
        subtitle: String,
        resource: Int,
        id: Int,
        action: () -> Unit,
    ): View =
        LinearLayout(context).apply {
            actionable(this, id, "$title. $subtitle", style.surface, action)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            addView(icon(resource, style.accent, 32))
            addView(
                column().apply {
                    append(label(title, 24f, bold = true))
                    if (!compact) append(label(subtitle, 18f, style.secondary), 6)
                },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(20) },
            )
            addView(icon(R.drawable.ic_lifelink_arrow, style.secondary, 24))
            for (i in 0 until childCount) getChildAt(i).importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

    private fun horizontal(
        views: List<View>,
        weights: List<Float> = List(views.size) { 1f },
        stack: Boolean = false,
    ) =
        LinearLayout(context).apply {
            orientation = if (stack) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            isBaselineAligned = false
            views.forEachIndexed { index, view ->
                addView(
                    view,
                    LinearLayout.LayoutParams(
                            if (stack) -1 else 0,
                            if (stack) -2 else -1,
                            if (stack) 0f else weights[index],
                        )
                        .apply {
                            if (index > 0) {
                                if (stack) topMargin = dp(16) else marginStart = dp(20)
                            }
                        },
                )
            }
        }

    fun connection(
        target: ConnectionTarget,
        status: String,
        detail: String,
        sharing: String,
        safety: String? = null,
    ): View =
        LinearLayout(context).apply {
            val accent =
                when (target) {
                    ConnectionTarget.COUNSEL_109 -> style.counselFill
                    ConnectionTarget.EMERGENCY_112 -> style.policeFill
                    else -> style.accent
                }
            val symbol =
                when (target) {
                    ConnectionTarget.COUNSEL_109 -> R.drawable.ic_lifelink_counsel
                    ConnectionTarget.CONSULT_119 -> R.drawable.ic_lifelink_medical
                    ConnectionTarget.EMERGENCY_112 -> R.drawable.ic_lifelink_shield
                    else -> R.drawable.ic_lifelink_phone
                }
            val title =
                when (target) {
                    ConnectionTarget.EMERGENCY_119 -> "119 긴급신고"
                    ConnectionTarget.CONSULT_119 -> "119 응급상담"
                    ConnectionTarget.COUNSEL_109 -> "109 마음상담"
                    ConnectionTarget.EMERGENCY_112 -> "112 경찰 신고"
                }
            orientation = if (widthDp < 760) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = style.shape(context, style.chrome, 28)
            minimumHeight = dp(if (compact) 200 else 320)
            setPadding(dp(32), dp(if (compact) 24 else 40), dp(32), dp(if (compact) 24 else 40))
            addView(
                FrameLayout(context).apply {
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(style.surface)
                            setStroke(dp(2), accent)
                        }
                    addView(
                        icon(symbol, accent, 64),
                        FrameLayout.LayoutParams(
                            dp(if (compact) 48 else 64),
                            dp(if (compact) 48 else 64),
                            Gravity.CENTER,
                        ),
                    )
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                },
                LinearLayout.LayoutParams(
                    dp(if (compact) 104 else 176),
                    dp(if (compact) 104 else 176),
                ),
            )
            addView(
                column().apply {
                    append(label(title, if (compact) 28f else 38f, bold = true))
                    append(
                        label(status, 25f, accent, true).apply {
                            id = R.id.x_session_status
                            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                        },
                        12,
                    )
                    append(label(detail, 21f, style.secondary), 12)
                    if (safety != null)
                        append(
                            label(safety, 20f, style.secondary).apply {
                                id = R.id.x_driving_safety
                            },
                            12,
                        )
                    append(label(sharing, 18f, style.accent), 16)
                },
                LinearLayout.LayoutParams(
                        if (widthDp < 760) -1 else 0,
                        -2,
                        if (widthDp < 760) 0f else 1f,
                    )
                    .apply { if (widthDp < 760) topMargin = dp(24) else marginStart = dp(40) },
            )
        }

    fun action(
        title: String,
        id: Int,
        resource: Int,
        primary: Boolean = false,
        end: Boolean = false,
        enabled: Boolean = true,
        action: () -> Unit,
    ): View =
        LinearLayout(context).apply {
            val fill =
                when {
                    end -> style.emergencyFill
                    primary -> style.medicalFill
                    else -> style.surface
                }
            val ink =
                when {
                    !enabled -> style.disabledInk
                    end -> style.emergencyActionInk
                    primary -> style.medicalInk
                    else -> style.ink
                }
            actionable(this, id, title, fill, action)
            isEnabled = enabled
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            addView(icon(resource, ink, 32))
            addView(
                label(title, 26f, ink, true),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(20) },
            )
            for (i in 0 until childCount) getChildAt(i).importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
}
