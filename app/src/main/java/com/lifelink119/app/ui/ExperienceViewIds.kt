package com.lifelink119.app.ui

import android.view.View
import com.lifelink119.app.R
import com.lifelink119.app.core.ShareField
import com.lifelink119.app.core.SupportAction

/** Compile-time IDs keep the data-driven catalog observable without resource-name reflection. */
object ExperienceViewIds {
    private val cases =
        listOf(
            R.id.x_case_s01,
            R.id.x_case_s02,
            R.id.x_case_s03,
            R.id.x_case_s04,
            R.id.x_case_s05,
            R.id.x_case_s06,
            R.id.x_case_s07,
            R.id.x_case_s08,
            R.id.x_case_s09,
            R.id.x_case_s10,
            R.id.x_case_s11,
            R.id.x_case_s12,
            R.id.x_case_s13,
            R.id.x_case_s14,
            R.id.x_case_e01,
            R.id.x_case_e02,
            R.id.x_case_e03,
            R.id.x_case_e04,
        )
    private val people =
        listOf(
            R.id.x_person_1,
            R.id.x_person_2,
            R.id.x_person_3,
            R.id.x_person_4,
            R.id.x_person_5,
            R.id.x_person_6,
            R.id.x_person_7,
            R.id.x_person_8,
            R.id.x_person_9,
            R.id.x_person_10,
            R.id.x_person_11,
            R.id.x_person_12,
        )

    fun scenario(id: String): Int {
        if (id == "D01") return R.id.x_case_d01
        val n = id.drop(1).toIntOrNull() ?: return View.NO_ID
        return cases.getOrNull(if (id.startsWith("S")) n - 1 else n + 13) ?: View.NO_ID
    }

    fun person(id: String) =
        people.getOrNull((id.removePrefix("DEMO-P-").toIntOrNull() ?: 0) - 1) ?: View.NO_ID

    fun action(value: SupportAction) =
        when (value) {
            SupportAction.CARD -> R.id.x_action_card
            SupportAction.VIDEO -> R.id.x_action_video
            SupportAction.LOCATION -> R.id.x_action_location
            SupportAction.TEXT -> R.id.x_action_text
            SupportAction.SIGN -> R.id.x_action_sign
            SupportAction.PHRASES -> R.id.x_action_phrases
            SupportAction.VOICE -> R.id.x_action_voice
            SupportAction.EASY -> R.id.x_action_easy
            SupportAction.MOBILITY -> R.id.x_action_mobility
            SupportAction.GUARDIAN -> R.id.x_action_guardian
            SupportAction.SUMMARY -> R.id.x_action_summary
            SupportAction.POLICE -> R.id.x_action_police
            SupportAction.NAVIGATION -> R.id.x_action_navigation
            SupportAction.SAFE_CALL -> R.id.x_action_safe_call
            SupportAction.POWER -> R.id.x_action_power
            SupportAction.VEHICLE -> R.id.x_action_vehicle
            SupportAction.PRIVACY -> R.id.x_action_privacy
            SupportAction.MANUAL -> R.id.x_action_manual
            SupportAction.COOPERATION -> R.id.x_action_cooperation
        }

    fun consent(value: ShareField) =
        when (value) {
            ShareField.ALIAS -> R.id.x_consent_alias
            ShareField.CONDITIONS -> R.id.x_consent_conditions
            ShareField.MEDICATIONS -> R.id.x_consent_medications
            ShareField.ALLERGIES -> R.id.x_consent_allergies
            ShareField.PREGNANCY -> R.id.x_consent_pregnancy
            ShareField.ACCESS -> R.id.x_consent_access
            ShareField.GUARDIAN -> R.id.x_consent_guardian
            ShareField.LOCATION -> R.id.x_consent_location
            ShareField.VEHICLE -> R.id.x_consent_vehicle
            ShareField.SUMMARY -> R.id.x_consent_summary
            ShareField.TEXT -> R.id.x_consent_text
            ShareField.VIDEO -> R.id.x_consent_video
        }
}
