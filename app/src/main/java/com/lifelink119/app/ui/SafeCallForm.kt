package com.lifelink119.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.lifelink119.app.R
import com.lifelink119.app.data.*

/** Five ordered sections from the supplied form; local draft only, no official web submission. */
internal class SafeCallForm(
    private val host: Activity,
    private val read: () -> PersonalHealthRecord,
    private val write: (PersonalHealthRecord) -> Unit,
    private val render: () -> Unit,
    private val protect: (() -> Unit) -> Unit,
) {
    private fun dp(value: Int) = (value * host.resources.displayMetrics.density).toInt()

    private fun change(block: (SafeCallDetails) -> SafeCallDetails) {
        val record = read()
        write(record.copy(safeCall = block(record.safeCall)))
    }

    private fun LinearLayout.attach(view: View, margin: Int = 12) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(margin) })
    }

    private fun label(text: String, heading: Boolean = false) =
        TextView(host).apply {
            this.text = text
            textSize = if (heading) 24f else 21f
            setTextColor(if (heading) LifeLinkUiStyle.accent else LifeLinkUiStyle.ink)
            typeface = LifeLinkUiStyle.typeface(heading)
            isAccessibilityHeading = heading
            setLineSpacing(dp(4).toFloat(), 1f)
        }

    private fun section(parent: LinearLayout, title: String) {
        parent.attach(label(title, true), 28)
    }

    private fun note(parent: LinearLayout, text: String) {
        parent.attach(label(text).apply { textSize = 18f })
    }

    private fun input(
        parent: LinearLayout,
        title: String,
        value: String,
        id: Int,
        max: Int = 120,
        phone: Boolean = false,
        multiline: Boolean = false,
        update: (String) -> Unit,
    ) {
        parent.attach(label(title), 16)
        parent.attach(
            EditText(host).apply {
                this.id = id
                contentDescription = title
                setText(value)
                textSize = 24f
                setTextColor(LifeLinkUiStyle.ink)
                setHintTextColor(LifeLinkUiStyle.secondary)
                minHeight = dp(if (multiline) 144 else LifeLinkUiStyle.touchHeight)
                inputType =
                    if (phone) InputType.TYPE_CLASS_PHONE
                    else
                        InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                            if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
                filters = arrayOf(InputFilter.LengthFilter(max))
                if (!multiline) setSingleLine() else gravity = Gravity.TOP or Gravity.START
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                isSaveEnabled = false
                isSaveFromParentEnabled = false
                setFilterTouchesWhenObscured(true)
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) {
                            update(s?.toString().orEmpty())
                        }

                        override fun afterTextChanged(s: Editable?) {}
                    }
                )
            },
            6,
        )
    }

    private fun action(
        parent: LinearLayout,
        title: String,
        id: Int = View.NO_ID,
        enabled: Boolean = true,
        block: () -> Unit,
    ) {
        parent.attach(
            Button(host).apply {
                this.id = id
                text = title
                contentDescription = title
                isAllCaps = false
                textSize = 22f
                minHeight = dp(LifeLinkUiStyle.touchHeight)
                setPadding(dp(20), dp(16), dp(20), dp(16))
                background = LifeLinkUiStyle.actionBackground(host, LifeLinkUiStyle.control)
                setTextColor(LifeLinkUiStyle.actionInk)
                typeface = LifeLinkUiStyle.typeface(true)
                isEnabled = enabled
                setFilterTouchesWhenObscured(true)
                setOnClickListener { protect(block) }
            }
        )
    }

    private fun <T> pick(
        parent: LinearLayout,
        title: String,
        id: Int,
        options: List<Pair<T, String>>,
        current: () -> T,
        update: (T) -> Unit,
    ) {
        parent.attach(label(title), 16)
        val items = options.map { it.second }
        parent.attach(
            Spinner(host).apply {
                this.id = id
                contentDescription = title
                minimumHeight = dp(LifeLinkUiStyle.touchHeight)
                isSaveEnabled = false
                adapter =
                    object :
                        ArrayAdapter<String>(
                            host,
                            android.R.layout.simple_spinner_dropdown_item,
                            items,
                        ) {
                        private fun style(v: View) =
                            v.apply {
                                minimumHeight = dp(LifeLinkUiStyle.touchHeight)
                                setPadding(dp(20), dp(12), dp(20), dp(12))
                                if (this is TextView) {
                                    textSize = 24f
                                    setTextColor(LifeLinkUiStyle.ink)
                                }
                            }

                        override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
                            style(super.getView(position, convertView, parent))

                        override fun getDropDownView(
                            position: Int,
                            convertView: View?,
                            parent: ViewGroup,
                        ) = style(super.getDropDownView(position, convertView, parent))
                    }
                setSelection(options.indexOfFirst { it.first == current() }.coerceAtLeast(0), false)
                onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) {}

                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long,
                        ) {
                            val selected = options[position].first
                            if (selected != current()) protect { update(selected) }
                        }
                    }
            },
            6,
        )
    }

    private fun list(
        parent: LinearLayout,
        title: String,
        id: Int,
        addId: Int,
        removeId: Int,
        current: () -> List<String>,
        update: (List<String>) -> Unit,
    ) {
        parent.attach(label(title, true), 20)
        current().forEachIndexed { index, value ->
            input(
                parent,
                "$title ${index+1}",
                value,
                if (index == 0) id else View.NO_ID,
                SafeCallDetailsCodec.MAX_LIST_TEXT,
            ) { edited ->
                val values = current()
                if (index in values.indices)
                    update(values.toMutableList().apply { this[index] = edited })
            }
            action(parent, "$title ${index+1} 삭제", if (index == 0) removeId else View.NO_ID) {
                update(current().filterIndexed { i, _ -> i != index })
                render()
            }
        }
        action(
            parent,
            "$title 목록 추가 +",
            addId,
            current().size < SafeCallDetailsCodec.MAX_LIST_ITEMS,
        ) {
            update(current() + "")
            render()
        }
    }

    fun applicant(parent: LinearLayout) {
        section(parent, "1. 신청자 인적사항")
        note(parent, "공식 사이트에 등록한 항목의 명칭과 내용을 직접 입력해 주세요. 입력하지 않은 항목은 ‘미입력’으로 보관합니다.")
        val d = read().safeCall
        input(parent, "신청유형", d.applicationType, R.id.x_health_application_type, 80) { v ->
            change { it.copy(applicationType = v) }
        }
        input(parent, "신청유형 상세", d.applicationDetail, R.id.x_health_application_detail, 80) { v ->
            change { it.copy(applicationDetail = v) }
        }
        input(parent, "거동여부", d.mobility, R.id.x_health_mobility) { v ->
            change { it.copy(mobility = v) }
        }
        pick(
            parent,
            "혈액형 · 선택",
            R.id.x_health_blood,
            listOf("" to "미입력", "A" to "A", "B" to "B", "AB" to "AB", "O" to "O"),
            { read().safeCall.bloodGroup },
        ) { v ->
            change { it.copy(bloodGroup = v) }
        }
        pick(
            parent,
            "Rh · 선택",
            R.id.x_health_rhesus,
            listOf("" to "미입력", "+" to "Rh+", "-" to "Rh-"),
            { read().safeCall.rhesus },
        ) { v ->
            change { it.copy(rhesus = v) }
        }
        pick(
            parent,
            "승강기 설치여부 · 선택",
            R.id.x_health_elevator,
            listOf(
                OptionalAnswer.UNSPECIFIED to "미입력",
                OptionalAnswer.YES to "있음",
                OptionalAnswer.NO to "없음",
            ),
            { read().safeCall.elevator },
        ) { v ->
            change { it.copy(elevator = v) }
        }
        pick(
            parent,
            "전화통화 가능여부 · 선택",
            R.id.x_health_can_call,
            listOf(
                OptionalAnswer.UNSPECIFIED to "미입력",
                OptionalAnswer.YES to "가능",
                OptionalAnswer.NO to "불가능",
            ),
            { read().safeCall.canCall },
        ) { v ->
            change { it.copy(canCall = v, guardianCallConfirmed = false) }
        }
        input(parent, "전화통화 불가사유 · 선택", d.callUnavailableReason, R.id.x_health_call_reason, 300) { v
            ->
            change { it.copy(callUnavailableReason = v) }
        }
        note(parent, "전화통화가 불가능하면 아래에 통화 가능한 보호자의 연락처를 등록하고 확인해 주세요.")
    }

    fun diseaseBeforeMedication(parent: LinearLayout) {
        section(parent, "2. 질병사항 · 선택")
        input(
            parent,
            "과거질환 (수술력)",
            read().safeCall.pastHistory,
            R.id.x_health_past_history,
            1500,
            multiline = true,
        ) { v ->
            change { it.copy(pastHistory = v) }
        }
        list(
            parent,
            "현재질환",
            R.id.x_health_condition_first,
            R.id.x_health_condition_add,
            R.id.x_health_condition_remove,
            { read().safeCall.currentConditions },
        ) { v ->
            change { it.copy(currentConditions = v) }
        }
    }

    fun diseaseAfterMedication(parent: LinearLayout) {
        list(
            parent,
            "법정감염병 · 직접 입력",
            R.id.x_health_infection_first,
            R.id.x_health_infection_add,
            R.id.x_health_infection_remove,
            { read().safeCall.infectiousDiseases },
        ) { v ->
            change { it.copy(infectiousDiseases = v) }
        }
        note(parent, "이 앱은 법정감염병 여부를 판정하지 않습니다. 공식 등록 정보 또는 확인된 기록만 입력하세요.")
        section(parent, "진료의료기관 · 선택")
        val d = read().safeCall
        input(parent, "진료기관명", d.facilityName, R.id.x_health_facility) { v ->
            change { it.copy(facilityName = v) }
        }
        input(parent, "진료과목", d.specialty, R.id.x_health_specialty, 80) { v ->
            change { it.copy(specialty = v) }
        }
        input(parent, "진료기관 연락처 (주간)", d.dayPhone, R.id.x_health_day_phone, 24, phone = true) { v ->
            change { it.copy(dayPhone = v) }
        }
    }

    fun additional(parent: LinearLayout) {
        section(parent, "3. 야간 연락처·장애·기타 구급관련 정보")
        val d = read().safeCall
        input(parent, "진료기관 연락처 (야간)", d.nightPhone, R.id.x_health_night_phone, 24, phone = true) {
            v ->
            change { it.copy(nightPhone = v) }
        }
        list(
            parent,
            "장애유형",
            R.id.x_health_disability_first,
            R.id.x_health_disability_add,
            R.id.x_health_disability_remove,
            { read().safeCall.disabilities },
        ) { v ->
            change { it.copy(disabilities = v) }
        }
        input(parent, "불편사항", d.discomfort, R.id.x_health_discomfort, 500, multiline = true) { v ->
            change { it.copy(discomfort = v) }
        }
        input(parent, "특이체질", d.constitution, R.id.x_health_constitution, 500, multiline = true) { v
            ->
            change { it.copy(constitution = v) }
        }
        input(
            parent,
            "위급 시 구급대원에게 요구사항",
            d.responderRequests,
            R.id.x_health_responder_requests,
            500,
            multiline = true,
        ) { v ->
            change { it.copy(responderRequests = v) }
        }
        input(parent, "자주 가는 곳 (기타) · 15자", d.frequentPlace, R.id.x_health_frequent_place, 15) { v
            ->
            change { it.copy(frequentPlace = v) }
        }
    }

    private fun guardian(index: Int, block: (SafeCallGuardian) -> SafeCallGuardian) {
        change { d ->
            d.copy(
                guardians = d.guardians.mapIndexed { i, g -> if (i == index) block(g) else g },
                guardianCallConfirmed = false,
            )
        }
    }

    private fun addGuardian(parent: LinearLayout, id: Int) {
        action(
            parent,
            "보호자 정보 추가 +",
            id,
            read().safeCall.guardians.size < SafeCallDetailsCodec.MAX_GUARDIANS,
        ) {
            change {
                it.copy(
                    guardians = it.guardians + SafeCallGuardian(),
                    guardianCallConfirmed = false,
                )
            }
            render()
        }
    }

    fun guardiansBasic(parent: LinearLayout) {
        section(parent, "4. 보호자정보 · 이름·관계·휴대폰")
        if (read().safeCall.guardians.isEmpty())
            note(parent, "등록한 보호자가 없습니다. 보호자 정보를 보관하려면 추가해 주세요.")
        read().safeCall.guardians.forEachIndexed { index, g ->
            parent.attach(label("보호자 ${index+1}", true), 20)
            input(
                parent,
                "보호자 ${index+1} 성함",
                g.name,
                if (index == 0) R.id.x_health_guardian_name else View.NO_ID,
                80,
            ) { v ->
                guardian(index) { it.copy(name = v) }
            }
            input(
                parent,
                "보호자 ${index+1} 관계 구분",
                g.relationCategory,
                if (index == 0) R.id.x_health_guardian_category else View.NO_ID,
                80,
            ) { v ->
                guardian(index) { it.copy(relationCategory = v) }
            }
            input(
                parent,
                "보호자 ${index+1} 관계 상세",
                g.relation,
                if (index == 0) R.id.x_health_guardian_relation else View.NO_ID,
                80,
            ) { v ->
                guardian(index) { it.copy(relation = v) }
            }
            input(
                parent,
                "보호자 ${index+1} 휴대폰 번호",
                g.mobile,
                if (index == 0) R.id.x_health_guardian_mobile else View.NO_ID,
                24,
                phone = true,
            ) { v ->
                guardian(index) { it.copy(mobile = v) }
            }
            action(
                parent,
                "보호자 ${index+1} 삭제",
                if (index == 0) R.id.x_health_guardian_remove else View.NO_ID,
            ) {
                change {
                    it.copy(
                        guardians = it.guardians.filterIndexed { i, _ -> i != index },
                        guardianCallConfirmed = false,
                    )
                }
                render()
            }
        }
        addGuardian(parent, R.id.x_health_guardian_add)
    }

    fun guardiansAdditional(parent: LinearLayout) {
        section(parent, "5. 보호자정보 · 전화·이메일·거주·SMS")
        read().safeCall.guardians.forEachIndexed { index, g ->
            parent.attach(label("보호자 ${index+1} 추가 연락정보", true), 20)
            input(
                parent,
                "보호자 ${index+1} 전화번호",
                g.phone,
                if (index == 0) R.id.x_health_guardian_phone else View.NO_ID,
                24,
                phone = true,
            ) { v ->
                guardian(index) { it.copy(phone = v) }
            }
            input(
                parent,
                "보호자 ${index+1} 이메일 · 선택",
                g.email,
                if (index == 0) R.id.x_health_guardian_email else View.NO_ID,
                254,
            ) { v ->
                guardian(index) { it.copy(email = v) }
            }
            pick(
                parent,
                "보호자 ${index+1} 거주구분",
                if (index == 0) R.id.x_health_guardian_living else View.NO_ID,
                LivingArrangement.entries.map { it to it.label },
                {
                    read().safeCall.guardians.getOrNull(index)?.living
                        ?: LivingArrangement.UNSPECIFIED
                },
            ) { v ->
                guardian(index) { it.copy(living = v) }
            }
            pick(
                parent,
                "보호자 ${index+1} SMS 수신 선호 · 선택",
                if (index == 0) R.id.x_health_guardian_sms else View.NO_ID,
                OptionalAnswer.entries.map { it to it.label },
                {
                    read().safeCall.guardians.getOrNull(index)?.smsPreference
                        ?: OptionalAnswer.UNSPECIFIED
                },
            ) { v ->
                guardian(index) { it.copy(smsPreference = v) }
            }
        }
        note(parent, "공식 사이트의 사고 접수 통지 항목을 차량에 보관하는 기능입니다. 이 앱에서 SMS를 신청하거나 발송하지 않습니다.")
        parent.attach(
            CheckBox(host).apply {
                id = R.id.x_health_guardian_call_confirm
                text = "전화통화 불가인 경우: 등록한 보호자와 전화통화가 가능함을 확인했습니다"
                textSize = 21f
                setTextColor(LifeLinkUiStyle.ink)
                minHeight = dp(LifeLinkUiStyle.touchHeight)
                isChecked = read().safeCall.guardianCallConfirmed
                isSaveEnabled = false
                setFilterTouchesWhenObscured(true)
                setOnCheckedChangeListener { _, checked ->
                    protect { change { it.copy(guardianCallConfirmed = checked) } }
                }
            }
        )
        action(
            parent,
            "보호자 정보 초기화",
            R.id.x_health_guardians_reset,
            read().safeCall.guardians.isNotEmpty(),
        ) {
            AlertDialog.Builder(host)
                .setTitle("입력 중인 보호자를 모두 지울까요?")
                .setMessage("저장을 선택하기 전까지 기존 저장 정보는 유지됩니다.")
                .setPositiveButton("초안에서 삭제") { _, _ ->
                    protect {
                        change { it.copy(guardians = emptyList(), guardianCallConfirmed = false) }
                        render()
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
        addGuardian(parent, R.id.x_health_guardian_add_bottom)
    }
}
