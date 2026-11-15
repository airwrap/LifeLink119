import unittest
from emergency import EmergencyStore, PhraseProxy
from test_emergency import snapshot
from test_llm_voice import meaning


class EmergencyRefinementTests(unittest.TestCase):
    def test_false_normal_and_unsupported_symptom_are_blocked_before_client_confirmation(self):
        cases = [
            ('BREATHING', ['BREATH', 'NORMAL', 'UNSURE'], 'BREATHING_YES'),
            ('BREATHING', ['BREATH', 'DIFFICULT'], 'BREATHING_YES'),
            ('CONSCIOUSNESS', ['RESPONSE', 'NO'], 'CONSCIOUS_YES'),
            ('SYMPTOMS', ['CHEST'], 'CHEST_PAIN'),
            ('SYMPTOMS', ['BLOOD', 'NO'], 'BLEEDING'),
            ('SYMPTOMS', ['ALONE'], 'STROKE_SIGNS'),
        ]
        for step, concepts, code in cases:
            proxy = PhraseProxy({'LIFELINK_LLM_MODE': 'external'}, transport=lambda _, c=code: {'observationCode': c})
            self.assertTrue(proxy.interpret(meaning(step=step, concepts=concepts))['fallback'])

    def test_supported_candidate_remains_only_a_candidate(self):
        proxy = PhraseProxy({'LIFELINK_LLM_MODE': 'external'}, transport=lambda _: {'observationCode': 'CHEST_PAIN'})
        result = proxy.interpret(meaning(step='SYMPTOMS', concepts=['CHEST', 'PAIN']))
        self.assertEqual({'observationCode': 'CHEST_PAIN', 'fallback': False}, result)

    def test_server_preserves_critical_warning_with_other_symptoms(self):
        s = EmergencyStore()
        self.assertTrue(s.publish(snapshot(observations=['BREATHING_NO', 'CHEST_PAIN', 'BLEEDING'], guidanceId='BREATHING'))['accepted'])
        self.assertTrue(s.publish(snapshot(revision=2, observations=['BREATHING_NO', 'FIRE', 'WORSENING'], guidanceId='FIRE'))['accepted'])


if __name__ == '__main__':
    unittest.main()
