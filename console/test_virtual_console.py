"""The desktop emulator adapter must never select a physical/other device."""
import importlib.util
from pathlib import Path
import unittest
import io
import urllib.error
from unittest.mock import patch,MagicMock

spec=importlib.util.spec_from_file_location('virtual_console',Path(__file__).resolve().parents[1]/'scripts/virtual_console.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
VirtualConsole=module.VirtualConsole

class VirtualConsoleBoundaryTests(unittest.TestCase):
    def test_physical_serial_is_rejected_before_any_io(self):
        with patch.object(module.subprocess,'check_output') as shell,patch.object(VirtualConsole,'_open') as web:
            with self.assertRaises(ValueError):VirtualConsole('physical-phone')
            shell.assert_not_called();web.assert_not_called()
    def test_other_avd_is_preserved(self):
        with patch.object(module.subprocess,'check_output',return_value=b'Unrelated_AVD\nOK\n') as shell,patch.object(VirtualConsole,'_open') as web:
            with self.assertRaises(RuntimeError):VirtualConsole()
            self.assertEqual(shell.call_count,1);web.assert_not_called()
    def test_existing_forward_for_another_emulator_is_preserved(self):
        with patch.object(module.subprocess,'check_output',side_effect=[b'Pleos_Connect_API_34\nOK\n',b'emulator-5554 tcp:8088 tcp:8088\n']) as shell,patch.object(VirtualConsole,'_open') as web:
            with self.assertRaises(RuntimeError):VirtualConsole()
            self.assertEqual(shell.call_count,2);web.assert_not_called()
    def test_control_limits_reject_without_network(self):
        v=VirtualConsole.__new__(VirtualConsole)
        with patch.object(v,'request') as request:
            for args in [dict(speed=-1),dict(speed=51),dict(accelerator=101),dict(brake=-1),dict(steering=541)]:
                with self.assertRaises(ValueError):v.controls(**args)
            request.assert_not_called()
    def test_lost_forward_blocks_requests(self):
        v=VirtualConsole.__new__(VirtualConsole);v.serial='emulator-5580'
        with patch.object(v,'_adb',return_value='emulator-5554 tcp:8088 tcp:8088\n'),patch.object(VirtualConsole,'_open') as web:
            with self.assertRaises(RuntimeError):v.state()
            web.assert_not_called()
    def test_transient_503_retries_but_does_not_change_target(self):
        v=VirtualConsole.__new__(VirtualConsole);v.thread=None
        response=MagicMock();response.__enter__.return_value.read.return_value=b'{"ready":true}'
        error=urllib.error.HTTPError('http://127.0.0.1:8088/vehicle/state',503,'not active',None,io.BytesIO(b'{}'))
        with patch.object(v,'_verify_mapping') as guard,patch.object(v,'_open',side_effect=[error,response]) as web,patch.object(module.time,'sleep'):
            self.assertEqual(v.state(),{'ready':True});self.assertEqual(web.call_count,2);self.assertGreaterEqual(guard.call_count,2)
            self.assertTrue(all(call.args[0].full_url=='http://127.0.0.1:8088/vehicle/state' for call in web.call_args_list))
    def test_forward_is_checked_again_before_retry(self):
        v=VirtualConsole.__new__(VirtualConsole);v.thread=None
        error=urllib.error.HTTPError('http://127.0.0.1:8088/vehicle/state',503,'not active',None,io.BytesIO(b'{}'))
        with patch.object(v,'_verify_mapping',side_effect=[None,None,RuntimeError('forward changed')]),patch.object(v,'_open',side_effect=error) as web,patch.object(module.time,'sleep'):
            with self.assertRaises(RuntimeError):v.state()
            self.assertEqual(web.call_count,1)
    def test_loopback_transport_does_not_use_environment_or_windows_proxy(self):
        with patch.object(module.urllib.request,'build_opener') as factory:
            VirtualConsole._open('http://127.0.0.1:8088/vehicle/state',timeout=5)
            handler=factory.call_args.args[0]
            self.assertIsInstance(handler,module.urllib.request.ProxyHandler);self.assertEqual(handler.proxies,{})
            factory.return_value.open.assert_called_once_with('http://127.0.0.1:8088/vehicle/state',timeout=5)

if __name__=='__main__':unittest.main()
