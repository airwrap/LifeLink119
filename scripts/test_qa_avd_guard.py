import subprocess
import unittest

from qa_avd_guard import QaAvdGuard


class GuardTests(unittest.TestCase):
    def setUp(self):
        self.name = 'LifeLink119_QA_API_34'
        self.boot = '00000000-0000-0000-0000-000000000001'
        self.preferences = {}
        self.calls = []

    def adb(self, argv, **kwargs):
        self.calls.append(argv)
        args = argv[3:]
        value = b''
        if args == ['emu', 'avd', 'name']:
            value = (self.name + '\nOK').encode()
        elif args == ['shell', 'am', 'get-current-user']:
            value = b'10'
        elif args == ['shell', 'cat', '/proc/sys/kernel/random/boot_id']:
            value = self.boot.encode()
        elif args[-1].endswith('.xml'):
            value = self.preferences.get(args[-1], b'<map/>')
        return subprocess.CompletedProcess(argv, 0, value, b'')

    def guard(self):
        return QaAvdGuard(['adb', '-s', 'emulator-5580'], run=self.adb)

    def test_daily_demo_avds_are_refused_even_with_clean_storage(self):
        for name in ('Pleos_Connect_API_34', 'LifeLink119_Std_API_34'):
            self.name = name
            with self.assertRaises(RuntimeError):
                self.guard().acquire_clean()

    def test_both_legacy_and_collection_health_cipher_refuse_reset(self):
        for key in ('record_cipher', 'records_cipher'):
            self.preferences['shared_prefs/lifelink_personal_health_v1.xml'] = (
                '<map><string name="' + key + '">encrypted</string></map>').encode()
            with self.assertRaises(RuntimeError):
                self.guard().acquire_clean()

    def test_pin_and_profiles_refuse_reset(self):
        for pref, key in [('lifelink_medical_lock_v1', 'pin_verifier'),
                          ('lifelink_emergency_checkpoint', 'cipher'),
                          ('lifelink_synthetic_profiles_v05', 'profiles_cipher')]:
            self.preferences = {'shared_prefs/' + pref + '.xml':
                                ('<map><string name="' + key + '">x</string></map>').encode()}
            with self.assertRaises(RuntimeError):
                self.guard().acquire_clean()

    def test_target_switch_and_reboot_refuse_cleanup(self):
        guard = self.guard().acquire_clean()
        guard.before_cleanup()
        self.boot = '00000000-0000-0000-0000-000000000002'
        with self.assertRaises(RuntimeError):
            guard.before_cleanup()

    def test_cleanup_requires_acquisition_and_read_errors_fail_closed(self):
        with self.assertRaises(RuntimeError):
            self.guard().before_cleanup()
        def denied(argv, **kwargs):
            if argv[-1].endswith('.xml'):
                return subprocess.CompletedProcess(argv, 1, b'', b'Permission denied')
            return self.adb(argv, **kwargs)
        with self.assertRaises(RuntimeError):
            QaAvdGuard(['adb', '-s', 'emulator-5580'], run=denied).acquire_clean()

    def test_real_device_serial_and_malformed_storage_refused(self):
        with self.assertRaises(RuntimeError):
            QaAvdGuard(['adb', '-s', 'real-device'], run=self.adb)
        self.preferences['shared_prefs/lifelink_personal_health_v1.xml'] = b'broken'
        with self.assertRaises(RuntimeError):
            self.guard().acquire_clean()


if __name__ == '__main__':
    unittest.main()
