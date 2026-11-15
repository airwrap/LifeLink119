package com.lifelink119.app.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*
import com.lifelink119.app.R
import com.lifelink119.app.data.VoiceAiPreferences

/** First-run choice and the same reversible preference page, without an emergency-flow checkbox. */
class VoiceAiSetupUi(
    private val context: Context,
    private val preferences: VoiceAiPreferences,
    private val choose: (Boolean) -> Unit,
) {
    private val style = LifeLinkUiStyle

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(style.ink)
            typeface = style.typeface(bold)
            setLineSpacing(dp(5).toFloat(), 1f)
            isSaveEnabled = false
        }

    fun view(): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            fun add(v: View, gap: Int = 20) =
                addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) })
            add(
                LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        ImageView(context).apply {
                            setImageResource(R.drawable.ic_lifelink_microphone)
                            setColorFilter(style.accent)
                        },
                        LinearLayout.LayoutParams(dp(72), dp(72)),
                    )
                    addView(
                        text("말로 답하면, AI가 함께합니다", 34f, true),
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(24) },
                    )
                }
            )
            add(text("질문은 하나씩 · 답변은 짧게\n시작한 뒤에는 화면을 계속 누르지 않아도 됩니다.", 25f))
            add(text("외부 AI에는 질문 단계와 필요한 증상 의미 코드만 보냅니다. 이름·위치·녹음·발화 원문은 보내지 않습니다.", 21f))
            add(
                text(
                    "이 선택은 이 차량에 저장합니다. 더보기의 ‘음성 AI 설정’에서 언제든 바꿀 수 있습니다. 외부 AI 없이도 기본 음성 안내를 이용할 수 있습니다.",
                    20f,
                )
            )
            if (!preferences.consent.needsChoice)
                add(
                    text(
                            if (preferences.externalAllowed) "현재: 외부 AI 사용" else "현재: 기본 음성 안내",
                            22f,
                            true,
                        )
                        .apply { id = R.id.x_ai_preference_status }
                )
            fun action(value: String, key: Int, primary: Boolean, external: Boolean) =
                Button(context).apply {
                    id = key
                    text = value
                    textSize = 25f
                    isAllCaps = false
                    minHeight = dp(96)
                    background =
                        style.actionBackground(
                            context,
                            if (primary) style.accent else style.control,
                            20,
                        )
                    setTextColor(style.actionInk)
                    filterTouchesWhenObscured = true
                    isSaveEnabled = false
                    setOnClickListener { choose(external) }
                }
            add(action("동의하고 시작", R.id.x_ai_setup_allow, true, true))
            add(action("기본 음성 안내로 시작", R.id.x_ai_setup_local, false, false), 12)
        }
}
