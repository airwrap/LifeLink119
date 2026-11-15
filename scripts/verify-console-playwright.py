"""Actual PC pages against a fresh loopback server; all inputs are built-in fixtures."""
import json
import os
from pathlib import Path
import sys
import threading
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'console'))
from server import make_server


def main():
    previous = os.environ.get('LIFELINK_LLM_MODE')
    os.environ['LIFELINK_LLM_MODE'] = 'mock'
    server = make_server(0)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = 'http://127.0.0.1:' + str(server.server_port)
    out = ROOT / 'verification/current-console'
    out.mkdir(parents=True, exist_ok=True)
    result = dict(status='FAIL', checks=[], errors=[], syntheticOnly=True,
                  actualAgencyCalls=0, originalServerPreserved=True)
    def mark(value):
        result['checks'].append(value)
        print('PASS ' + value, flush=True)
    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=True)
            context = browser.new_context(viewport={'width': 1920, 'height': 1080}, locale='ko-KR')
            passenger = context.new_page()
            operator = context.new_page()
            for page in (passenger, operator):
                page.on('pageerror', lambda error: result['errors'].append(str(error)[:200]))
            passenger.goto(base + '/passenger')
            passenger.locator('#motion-state').select_option('PARKED')
            operator.goto(base + '/')
            for scenario in ['MEDICAL', 'VIDEO', 'TRANSPORT', 'POLICE', 'WELLBEING']:
                passenger.locator('#scenario').select_option(scenario)
                expect(passenger.locator('#case option').first).to_be_attached()
                passenger.locator('#next').click()
                expect(passenger.locator('#vehicle')).not_to_be_checked()
                expect(passenger.locator('#location')).not_to_be_checked()
                passenger.locator('#vehicle').check()
                passenger.locator('#location').check()
                if scenario == 'VIDEO': passenger.locator('#video').check()
                passenger.locator('#connect').click()
                expect(passenger.locator('#connected')).to_be_visible()
                ticket = passenger.locator('#ticket').inner_text().split()[0]
                operator.locator('#refresh').click()
                operator.locator('#sessions').get_by_role('button').filter(has_text=ticket).click()
                expect(operator.locator('#detail')).to_be_visible()
                for label in ['모의 접수', '검토 시작 (모의)', '가상 안내 표시']:
                    operator.locator('#actions').get_by_role('button', name=label, exact=True).click()
                passenger.locator('#refresh').click()
                expect(passenger.locator('#session-state')).to_contain_text('안내')
                passenger.screenshot(path=str(out / (scenario.lower() + '.png')))
                passenger.locator('#revoke').click()
                expect(passenger.locator('#shared')).not_to_contain_text('가상 차량 A')
                passenger.locator('#close').click()
                expect(passenger.locator('#session-state')).to_contain_text('종료')
                passenger.locator('#new').click()
                expect(passenger.locator('#intro')).to_be_visible()
                mark(scenario + ': create -> operator review/guidance -> revoke -> close')
            for path, selector in [('/manual', 'main'), ('/experience-console', '#status'), ('/cooperation', '#poc-start')]:
                operator.goto(base + path)
                expect(operator.locator(selector)).to_be_visible()
                mark('PC route loads: ' + path)
            expect(operator.locator('#poc-start')).to_be_disabled()
            operator.locator('#poc-consent').check()
            operator.locator('#poc-start').click()
            expect(operator.locator('#poc-experiment')).to_be_visible()
            expect(operator.locator('#poc-run-id')).to_contain_text('POC-')
            operator.screenshot(path=str(out / 'cooperation.png'))
            mark('Cooperation creation requires explicit synthetic-location consent')
            for control in ['poc-confirm', 'poc-invite', 'poc-accept', 'poc-authorize',
                            'poc-approve-beacon', 'poc-activate', 'poc-arrive', 'poc-handover']:
                operator.locator('#' + control).click()
            expect(operator.locator('#poc-state')).to_contain_text('인계 완료')
            expect(operator.locator('#poc-beacon-state')).not_to_contain_text('ON')
            mark('Cooperation invite -> consent -> authorize -> simulated beacon -> handover')
            if result['errors']: raise AssertionError('Uncaught browser script errors')
            result['status'] = 'PASS'
            browser.close()
    finally:
        server.shutdown(); server.server_close(); thread.join(timeout=5)
        if previous is None: os.environ.pop('LIFELINK_LLM_MODE', None)
        else: os.environ['LIFELINK_LLM_MODE'] = previous
        (out / 'results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
