"""Verify the real local web UI without any parked precondition."""
import json
from datetime import datetime, timezone
from pathlib import Path
import re
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'verification'
BASE = 'http://127.0.0.1:8781'


def main():
    checks = []
    errors = []
    def record(name):
        checks.append({'check': name, 'status': 'PASS'})
        print('PASS ' + name, flush=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': 1280, 'height': 900})
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        def post(path, body):
            response = context.request.post(BASE + '/api/demo' + path, data=body)
            assert response.ok, response.text()
            return response.json()
        try:
            page.goto(BASE + '/passenger')
            expect(page.locator('#case option')).not_to_have_count(0)
            expect(page.locator('#controls')).to_be_enabled()
            expect(page.locator('#parked')).to_have_count(0)
            record('unknownMotionAllowsEmergencyFlowImmediately')
            page.locator('#motion-state').select_option('DRIVING')
            page.locator('#next').click()
            for field in ('vehicle', 'location', 'video'):
                assert not page.locator('#' + field).is_checked()
            page.locator('#connect').click()
            expect(page.locator('#ticket')).to_contain_text('DEMO-')
            ticket = re.search(r'DEMO-\d+', page.locator('#ticket').inner_text())[0]
            record('movingPassengerConnectsWithConsentOff')
            page.get_by_role('button', name='표시 승인 요청 (모의)', exact=True).click()
            for action in ('ACKNOWLEDGE', 'REVIEW'):
                post('/sessions/' + ticket + '/actions', {'syntheticOnly': True, 'action': action})
            for action in ('CONFIRM', 'APPROVE'):
                post('/sessions/' + ticket + '/signal/operator', {'syntheticOnly': True, 'action': action, 'authority': '119'})
            page.get_by_role('button', name='승인된 화면 점멸 시작', exact=True).click()
            expect(page.locator('.signal-preview-dot')).to_have_class(re.compile(r'\bactive\b'), timeout=10000)
            for motion in ('PARKED', 'UNKNOWN', 'DRIVING'):
                page.locator('#motion-state').select_option(motion)
                expect(page.locator('.signal-preview-dot')).to_have_class(re.compile(r'\bactive\b'))
            page.screenshot(path=str(OUT / 'v041-web-moving-passenger.png'), full_page=True)
            record('approvedSignalSurvivesEveryMotionContext')
            post('/sessions/' + ticket + '/signal/operator', {'syntheticOnly': True, 'action': 'REVOKE', 'authority': '119'})
            expect(page.locator('.signal-preview-dot')).not_to_have_class(re.compile(r'\bactive\b'), timeout=10000)
            post('/sessions/' + ticket + '/close', {'syntheticOnly': True})
            record('operatorRevocationStillStopsSignal')
            page.goto(BASE + '/manual')
            expect(page.locator('#manual-content')).to_be_visible()
            expect(page.locator('#manual-parked')).to_have_count(0)
            topics = page.locator('#manual-topics button')
            assert topics.count() == 6
            for i in range(6):
                title = topics.nth(i).inner_text()
                topics.nth(i).click()
                expect(page.locator('#manual-title')).to_have_text(title)
            record('sixManualTopicsOpenWithoutParkedConfirmation')
            page.goto(BASE + '/cooperation')
            expect(page.locator('#poc-case option')).not_to_have_count(0)
            expect(page.locator('#poc-parked')).to_have_count(0)
            page.locator('#poc-consent').check()
            page.locator('#poc-start').click()
            expect(page.locator('#poc-state')).to_have_text('주변 도움 요청')
            for key, state in (('confirm', '119 역할 상황 확인'), ('invite', '협력 차량 초대'),
                               ('accept', '자발적 참여 수락'), ('authorize', '응급협력 역할 부여')):
                page.locator('#poc-' + key).click()
                expect(page.locator('#poc-state')).to_have_text(state)
            expect(page.locator('#poc-activate')).to_be_disabled()
            page.locator('#poc-authority').select_option('112')
            page.locator('#poc-approve-beacon').click()
            expect(page.locator('#poc-activate')).to_be_enabled()
            page.locator('#poc-activate').click()
            expect(page.locator('#poc-beacon')).to_have_class('active', timeout=10000)
            page.screenshot(path=str(OUT / 'v041-web-cooperation-active.png'), full_page=True)
            page.locator('#poc-arrive').click()
            expect(page.locator('#poc-state')).to_have_text('안전한 현장 지원 시연')
            page.locator('#poc-authority').select_option('119')
            page.locator('#poc-handover').click()
            expect(page.locator('#poc-state')).to_have_text('구급대 인계 완료')
            expect(page.locator('#poc-beacon')).not_to_have_class('active')
            record('cooperationApprovalActivationAndHandoverNeedNoParkedClaim')
            page.set_viewport_size({'width': 390, 'height': 844})
            for route in ('/passenger', '/manual', '/cooperation'):
                page.goto(BASE + route)
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), route
            record('mobileWidthHasNoHorizontalOverflow')
            assert not errors, errors
            record('noBrowserJavaScriptExceptions')
            report = {'status': 'PASS', 'at': datetime.now(timezone.utc).isoformat(), 'checks': checks, 'browserErrors': errors}
            (OUT / 'v041-web-e2e.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        finally:
            browser.close()


if __name__ == '__main__':
    main()
