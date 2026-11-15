package com.lifelink119.app.ui

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.lifelink119.app.R
import com.lifelink119.app.data.OptionalAnswer
import com.lifelink119.app.data.SafeCallImportPreview

/** Protected, memory-only review. It owns no store and cannot authorize saving by itself. */
internal object SafeCallImportReviewUi {
    fun create(
        context: Context,
        preview: SafeCallImportPreview,
        save: (Boolean) -> Unit,
        edit: () -> Unit,
        cancel: () -> Unit,
    ): View =
        LinearLayout(context).apply {
            id = R.id.safe_import_review
            orientation = LinearLayout.VERTICAL
            isSaveEnabled = false
            fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
            fun line(value: String, heading: Boolean = false) {
                addView(
                    TextView(context).apply {
                        text = value
                        textSize = if (heading) 24f else 21f
                        setTextColor(if (heading) LifeLinkUiStyle.accent else LifeLinkUiStyle.ink)
                        setPadding(0, dp(12), 0, dp(10))
                        isSaveEnabled = false
                    }
                )
            }
            line("안심콜 웹 화면에서 가져온 내 정보", true)
            line(
                "아직 저장하지 않았습니다. 본인 정보와 현재 내용을 확인해 주세요. 웹에서 수정 중인 값도 포함될 수 있으며, 공식 등록 완료를 뜻하지 않습니다."
            )
            line(
                "비어 있거나 읽지 못한 항목은 기존 값을 유지합니다. 질환·보호자 목록은 기존 정보와 합치고, 보호자는 가져온 연락처부터 표시합니다. 오래된 연락처는 ‘내용 보완’에서 정리하고 전체 목록을 확인해 주세요."
            )
            preview.imported.forEach { (label, value) ->
                line(label, true)
                line(value)
            }
            if (preview.missing.isNotEmpty())
                line("읽지 못한 항목 · 기존 값 유지\n${preview.missing.joinToString(" · ")}")
            val record = preview.record
            if (record.conditions.isNotBlank() || record.allergies.isNotBlank()) {
                line("차량에 입력했던 메모 · 유지", true)
                if (record.conditions.isNotBlank()) line("병력 메모: ${record.conditions}")
                if (record.allergies.isNotBlank()) line("알레르기: ${record.allergies}")
            }
            line("저장 후에도 의료정보 잠금을 사용합니다. 응급요약에는 인증 후 이번 환자를 선택한 경우에만 반영하며 외부 AI에 보내지 않습니다.")
            if (preview.identityConflict)
                line(
                    "기존 ‘내 정보’의 이름 또는 연락처와 다릅니다. 다른 사람의 정보가 섞이지 않도록 저장을 막았습니다. 기존 내 정보를 확인한 뒤 다시 가져와 주세요."
                )
            val own =
                CheckBox(context).apply {
                    id = R.id.safe_import_self
                    text = "본인 정보이며, 내용을 확인하고 차량에 보관하는 데 동의합니다"
                    textSize = 22f
                    minHeight = dp(72)
                    setTextColor(LifeLinkUiStyle.ink)
                    isSaveEnabled = false
                    setFilterTouchesWhenObscured(true)
                }
            addView(own)
            val needsGuardian = record.safeCall.canCall == OptionalAnswer.NO
            val guardian =
                CheckBox(context).apply {
                    id = R.id.safe_import_guardian
                    text = "저장할 보호자 연락처로 통화할 수 있음을 확인했습니다"
                    textSize = 22f
                    minHeight = dp(72)
                    setTextColor(LifeLinkUiStyle.ink)
                    isSaveEnabled = false
                    setFilterTouchesWhenObscured(true)
                    visibility = if (needsGuardian) View.VISIBLE else View.GONE
                }
            addView(guardian)
            fun button(label: String, key: Int, action: () -> Unit): Button =
                Button(context).apply {
                    id = key
                    text = label
                    textSize = 24f
                    isAllCaps = false
                    minHeight = dp(80)
                    isSaveEnabled = false
                    setFilterTouchesWhenObscured(true)
                    setTextColor(LifeLinkUiStyle.actionInk)
                    background = LifeLinkUiStyle.actionBackground(context, LifeLinkUiStyle.control)
                    setOnClickListener { action() }
                    addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                }
            val saveButton =
                button("확인한 정보 암호화 저장", R.id.safe_import_save) {
                    if (own.isChecked) save(guardian.isChecked)
                }
            val editButton =
                button("내용 보완 후 저장", R.id.safe_import_edit) { if (own.isChecked) edit() }
            fun enabled() {
                editButton.isEnabled = own.isChecked && !preview.identityConflict
                saveButton.isEnabled =
                    own.isChecked &&
                        !preview.identityConflict &&
                        (!needsGuardian ||
                            guardian.isChecked && record.safeCall.guardians.isNotEmpty())
            }
            own.setOnCheckedChangeListener { _, _ -> enabled() }
            guardian.setOnCheckedChangeListener { _, _ -> enabled() }
            enabled()
            button("취소 · 가져온 정보 버리기", R.id.safe_import_cancel, cancel)
        }
}
