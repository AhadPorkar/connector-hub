#!/usr/bin/env python3
"""Playwright UI verification. Native navigation is default.
HUB_UI_TRANSPORT=harness is an explicitly labeled fallback for managed browsers
that block every navigation. The harness renders real assets and bridges fetch
to the actual Java server; it does NOT verify browser CSP/network integration.
"""
from __future__ import annotations
import base64
import importlib.util
import json
import os
import re
from pathlib import Path
import time
import unittest
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('http_fixtures', ROOT/'tests/http_test.py')
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
ASSETS = ROOT/'hub-server/src/main/resources/public'
SCREENSHOTS = ROOT/'build/screenshots'
TRANSPORT = os.environ.get('HUB_UI_TRANSPORT', 'native')

class UI(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if TRANSPORT not in ('native', 'harness'):
            raise ValueError('HUB_UI_TRANSPORT must be native or harness')
        fixture.HubHTTP.setUpClass()
        cls.addClassCleanup(fixture.HubHTTP.tearDownClass)
        SCREENSHOTS.mkdir(parents=True, exist_ok=True)
        cls.pw = sync_playwright().start()
        cls.addClassCleanup(cls.pw.stop)
        options = {'headless': True}
        if os.environ.get('HUB_BROWSER_EXECUTABLE'):
            options['executable_path'] = os.environ['HUB_BROWSER_EXECUTABLE']
        cls.browser = cls.pw.chromium.launch(**options)
        cls.addClassCleanup(cls.browser.close)

    def setUp(self):
        self.context = self.browser.new_context(viewport={'width':1440,'height':1050})
        self.addCleanup(self.context.close)
        self.page = self.context.new_page()
        self.page.set_default_timeout(8000)
        self.errors = []
        self.page.on('pageerror', lambda error: self.errors.append(str(error)))
        self.page.on('dialog', lambda dialog: dialog.accept())
        if TRANSPORT == 'harness':
            def bridge(data):
                url = data['url']
                if not url.startswith('/') or url.startswith('//'):
                    raise ValueError('Harness only accepts same-origin relative URLs')
                req = urllib.request.Request(fixture.HubHTTP.base+url, data=data.get('body', '').encode() if data.get('body') else None, headers=data.get('headers', {}), method=data.get('method','GET'))
                try: response = urllib.request.urlopen(req, timeout=20)
                except urllib.error.HTTPError as error: response = error
                with response: return {'status':response.status, 'body':response.read().decode(), 'headers':dict(response.headers)}
            self.page.expose_function('httpBridge', bridge)
            self.page.set_content('<html><head></head><body><div id="app"></div><div id="modal-root"></div><div id="toast" role="status"></div></body></html>')
            self.page.add_style_tag(content=(ASSETS/'app.css').read_text())
            self.page.evaluate('''() => {
              const memory = new Map();
              Object.defineProperty(window, 'localStorage', {value:{getItem:k=>memory.get(k)||null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)}});
              window.fetch=async (url,o={})=>{const r=await window.httpBridge({url,method:o.method||'GET',body:o.body,headers:o.headers||{}});return new Response(r.status===204?null:r.body,{status:r.status,headers:r.headers});};
            }''')
            logo='data:image/svg+xml;base64,'+base64.b64encode((ASSETS/'favicon.svg').read_bytes()).decode()
            self.page.evaluate('''logo => {new MutationObserver(()=>document.querySelectorAll('img[src="/favicon.svg"]').forEach(img=>img.src=logo)).observe(document.body,{childList:true,subtree:true});}''',logo)
            self.page.add_script_tag(content='(async()=>{'+(ASSETS/'app.js').read_text()+'})().catch(e=>{throw e;});')
        else:
            self.page.goto(fixture.HubHTTP.base)
        self.login()

    def login(self):
        self.page.locator('#access-token').wait_for()
        self.page.locator('#access-token').fill(fixture.TOKEN)
        self.page.locator('button[type=submit]').click()
        self.page.locator('.hero').wait_for()

    def tearDown(self):
        # Check each test, including ones without an explicit error assertion.
        # Context cleanup still runs if this assertion fails.
        if self.errors:
            self.page.screenshot(path=str(SCREENSHOTS/(self._testMethodName+'-error.png')),full_page=True)
            (SCREENSHOTS/(self._testMethodName+'-errors.json')).write_text(json.dumps(self.errors,indent=2))
        self.assertEqual([],self.errors,'Uncaught JavaScript error in this test')

    def navigate(self, name):
        self.page.locator(f'[data-nav="{name}"]').click()
        self.page.locator('#content .loading').wait_for(state='detached')
        self.page.wait_for_timeout(80)
    def test_01_provider_filter(self):
        self.navigate('providers')
        self.page.locator('#list-search').fill('SOAP addition')
        self.assertEqual(1,self.page.locator('#provider-list tbody tr').count())
        self.page.locator('#list-search').fill('not-present-unique-filter')
        self.assertEqual(0,self.page.locator('#provider-list tbody tr').count())
    def test_02_read_provider_execution(self):
        self.navigate('providers')
        provider=fixture.HubHTTP.providers['customer-directory']
        self.page.locator(f'[data-run="{provider["id"]}"]').click()
        self.page.locator('#run-provider').click()
        expect(self.page.locator('#execution-result')).to_contain_text('SUCCEEDED',timeout=8000)
        result=json.loads(self.page.locator('#execution-result').inner_text())
        self.assertEqual('SUCCEEDED',result['status'])
        self.assertGreater(len(result['data']),0)
        self.page.screenshot(path=str(SCREENSHOTS/'execution-en.png'),full_page=True)
    def test_03_invalid_json_does_not_execute(self):
        self.navigate('providers')
        self.page.locator('[data-run]').first.click()
        self.page.locator('#execution-input').fill('{broken')
        self.page.locator('#run-provider').click()
        expect(self.page.locator('#modal-error')).not_to_be_empty()
        self.assertNotEqual('',self.page.locator('#modal-error').inner_text())
    def test_04_edit_provider_and_tabs(self):
        self.navigate('providers')
        provider=fixture.HubHTTP.providers['customer-directory']
        self.page.locator(f'[data-edit-provider="{provider["id"]}"]').click()
        self.page.locator('#provider-name').fill('Customer directory - UI verified')
        for tab in ('parameters','request','output','resilience','general'):
            self.page.locator(f'[data-tab="{tab}"]').click()
        self.page.locator('[data-tab="request"]').click()
        self.page.screenshot(path=str(SCREENSHOTS/'provider-editor-en.png'),full_page=True)
        self.page.locator('#save-provider').click()
        self.page.locator('#provider-name').wait_for(state='detached')
        found=fixture.HubHTTP.api('GET','/api/providers/'+provider['id'])[1]
        self.assertEqual('Customer directory - UI verified',found['name'])
    def test_05_local_connection_creation(self):
        self.navigate('connections')
        self.page.locator('#new-connection').click()
        self.page.locator('#connection-name').fill('UI-created local connection')
        self.page.locator('#connection-type').select_option('LOCAL')
        self.page.locator('#save-connection').click()
        self.page.locator('#connection-name').wait_for(state='detached')
        self.page.get_by_text('UI-created local connection',exact=True).wait_for()
        self.assertEqual(1,self.page.get_by_text('UI-created local connection',exact=True).count())
    def test_06_settings_saved_to_real_server(self):
        self.navigate('settings')
        self.page.locator('#settings-json').fill('{"region":"DE","source":"browser-test"}')
        self.page.locator('#save-settings').click()
        expect(self.page.locator('#toast')).to_have_class(re.compile(r'\bvisible\b'))
        value=fixture.HubHTTP.api('GET','/api/settings')[1]
        self.assertEqual('browser-test',value['source'])
    def test_07_persian_mobile_layout(self):
        self.navigate('dashboard')
        self.page.locator('#language').select_option('fa')
        self.page.locator('.hero').wait_for()
        self.page.set_viewport_size({'width':390,'height':844})
        self.page.wait_for_timeout(150)
        self.assertEqual('rtl',self.page.locator('html').get_attribute('dir'))
        self.assertFalse(self.page.evaluate('document.documentElement.scrollWidth > innerWidth + 1'))
        self.page.screenshot(path=str(SCREENSHOTS/'dashboard-fa-mobile.png'),full_page=True)
    def open_execution(self):
        self.navigate('providers')
        provider=fixture.HubHTTP.providers['customer-directory']
        self.page.locator(f'[data-run="{provider["id"]}"]').click()
        return provider

    def hold_execution_response(self):
        """Hold delivery after the real server has responded; never replay a request."""
        self.page.evaluate(r"""() => {
          const originalFetch=window.fetch.bind(window);
          const gate=window.__executionGate={count:0,status:null};
          window.fetch=async (url,options={}) => {
            if(!String(url).match(/^\/api\/providers\/[^/]+\/execute$/))
              return originalFetch(url,options);
            gate.count++;
            const response=await originalFetch(url,options);
            const body=await response.text();
            gate.status=response.status;
            await new Promise(resolve=>{
              gate.release=resolve;
              document.documentElement.setAttribute('data-execution-held','true');
            });
            const released=new Response(body,{status:response.status,headers:response.headers});
            const readJson=released.json.bind(released);
            released.json=async()=>{
              const value=await readJson();
              // Observe a later task, after the application's await/catch/finally.
              setTimeout(()=>document.documentElement.setAttribute('data-execution-delivered','true'),0);
              return value;
            };
            return released;
          };
        }""")

    def start_held_execution(self, invalid=False):
        self.hold_execution_response()
        if invalid:
            self.page.locator('#execution-input').fill('[]')
        self.page.locator('#run-provider').click()
        expect(self.page.locator('html')).to_have_attribute('data-execution-held','true')
        status=self.page.evaluate('() => window.__executionGate.status')
        self.assertEqual(400 if invalid else 200,status)
        expect(self.page.locator('#run-provider')).to_be_disabled()

    def release_execution_response(self):
        self.page.evaluate('() => window.__executionGate.release()')
        expect(self.page.locator('html')).to_have_attribute('data-execution-delivered','true')
        self.assertEqual(1,self.page.evaluate('() => window.__executionGate.count'))
        self.assertEqual([],self.errors)

    def test_08_locator_assertions_with_strict_csp(self):
        # The probe works even in managed-browser harness mode. It verifies the
        # assertions under an explicit CSP, not end-to-end native navigation.
        probe=self.context.new_page()
        try:
            probe.set_content("""<html><head><meta http-equiv="Content-Security-Policy"
              content="default-src 'none'; script-src 'self'"></head><body>
              <pre id="result">READY</pre><div id="error"></div></body></html>""")
            probe.evaluate("""() => {setTimeout(()=>{
              document.querySelector('#result').textContent='SUCCEEDED';
              document.querySelector('#error').textContent='validation error';
            },50);}""")
            expect(probe.locator('#result')).to_contain_text('SUCCEEDED')
            expect(probe.locator('#error')).not_to_be_empty()
        finally:
            probe.close()

    def test_09_server_error_is_displayed(self):
        self.open_execution()
        self.page.locator('#execution-input').fill('[]')
        self.page.locator('#run-provider').click()
        expect(self.page.locator('#modal-error')).not_to_be_empty()
        expect(self.page.locator('#execution-result')).to_contain_text('INVALID_INPUT')
        expect(self.page.locator('#run-provider')).to_be_enabled()

    def test_10_close_during_successful_execution(self):
        self.open_execution()
        self.start_held_execution()
        self.page.locator('#modal-close').click()
        self.release_execution_response()
        expect(self.page.locator('[role="dialog"]')).to_have_count(0)

    def test_11_close_during_failed_execution(self):
        self.open_execution()
        self.start_held_execution(invalid=True)
        self.page.keyboard.press('Escape')
        self.release_execution_response()
        expect(self.page.locator('[role="dialog"]')).to_have_count(0)

    def test_12_success_does_not_overwrite_new_dialog(self):
        provider=self.open_execution()
        self.start_held_execution()
        self.page.locator('#modal-close').click()
        self.page.locator(f'[data-run="{provider["id"]}"]').click()
        expected=self.page.locator('#execution-result').inner_text()
        key=self.page.locator('#execution-key').input_value()
        self.release_execution_response()
        expect(self.page.locator('#execution-result')).to_have_text(expected)
        expect(self.page.locator('#execution-key')).to_have_value(key)
        expect(self.page.locator('#modal-error')).to_be_empty()
        expect(self.page.locator('#run-provider')).to_be_enabled()

    def test_13_error_does_not_overwrite_new_dialog(self):
        provider=self.open_execution()
        self.start_held_execution(invalid=True)
        self.page.locator('#modal-close').click()
        self.page.locator(f'[data-run="{provider["id"]}"]').click()
        expected=self.page.locator('#execution-result').inner_text()
        self.release_execution_response()
        expect(self.page.locator('#execution-result')).to_have_text(expected)
        expect(self.page.locator('#modal-error')).to_be_empty()
        expect(self.page.locator('#run-provider')).to_be_enabled()

    def test_14_language_change_during_execution(self):
        self.open_execution()
        self.start_held_execution()
        # Language switching closes the dialog in bindLanguage. Dispatch the
        # event programmatically because the modal blocks a physical click.
        self.page.locator('#language').select_option('fa',force=True)
        expect(self.page.locator('html')).to_have_attribute('lang','fa')
        expect(self.page.locator('[role="dialog"]')).to_have_count(0)
        self.release_execution_response()
        expect(self.page.locator('html')).to_have_attribute('dir','rtl')

    def test_15_inflight_execution_disables_run_button(self):
        self.open_execution()
        self.start_held_execution()
        # Disabled controls must not dispatch another click/request.
        self.page.locator('#run-provider').evaluate('(button)=>button.click()')
        self.release_execution_response()
        expect(self.page.locator('#execution-result')).to_contain_text('SUCCEEDED')
        expect(self.page.locator('#run-provider')).to_be_enabled()

    def test_16_successful_execution_can_run_again(self):
        self.open_execution()
        self.page.locator('#run-provider').click()
        expect(self.page.locator('#execution-result')).to_contain_text('SUCCEEDED')
        expect(self.page.locator('#run-provider')).to_be_enabled()
        self.page.locator('#new-key').click()
        self.page.locator('#run-provider').click()
        expect(self.page.locator('#execution-result')).to_contain_text('SUCCEEDED')
        expect(self.page.locator('#run-provider')).to_be_enabled()

    def test_98_logout_clears_session(self):
        self.page.locator('#logout').click()
        self.page.locator('#access-token').wait_for()
        self.assertEqual(0,self.page.locator('.shell').count())
    def test_99_no_javascript_errors(self):
        self.assertEqual([],self.errors)

def page_case(language, name):
    def run(self):
        self.page.locator('#language').select_option(language)
        self.page.wait_for_timeout(100)
        self.navigate(name)
        self.assertEqual(language,self.page.locator('html').get_attribute('lang'))
        self.assertEqual('rtl' if language=='fa' else 'ltr',self.page.locator('html').get_attribute('dir'))
        self.assertGreater(len(self.page.locator('#content').inner_text()),40)
        self.assertFalse(self.page.evaluate('document.documentElement.scrollWidth > innerWidth + 1'))
        self.assertEqual(0,self.page.locator('#content > [role=alert]').count())
        self.assertEqual([], self.errors)
        self.assertTrue(self.page.evaluate('''() => {const s=document.querySelector('.sidebar').getBoundingClientRect(), b=document.querySelector('.sidebar .brand-sub').getBoundingClientRect();return b.left>=s.left-1 && b.right<=s.right+1;}'''))
        if name=='docs':
            text=self.page.locator('#docs-text').inner_text()
            self.assertGreater(len(text),400)
        if (language,name) in [('en','dashboard'),('de','providers'),('fa','dashboard')]:
            self.page.screenshot(path=str(SCREENSHOTS/f'{name}-{language}.png'),full_page=True)
    return run
for language in ('en','de','fa'):
    for name in ('dashboard','connections','providers','services','jobs','audit','keys','settings','docs'):
        setattr(UI,f'test_view_{language}_{name}',page_case(language,name))

class Report(unittest.TextTestResult):
    def startTest(self,test):
        super().startTest(test)
        self.started=time.perf_counter()
    def addSuccess(self,test):
        super().addSuccess(test)
        self.record(test)
    def addFailure(self,test,err):
        super().addFailure(test,err)
        self.record(test,'failure',self._exc_info_to_string(err,test))
    def addError(self,test,err):
        super().addError(test,err)
        self.record(test,'error',self._exc_info_to_string(err,test))
    def addSkip(self,test,reason):
        super().addSkip(test,reason)
        self.record(test,'skipped',reason)
    def record(self,test,kind=None,detail=None):
        elapsed=max(0,time.perf_counter()-getattr(self,'started',time.perf_counter()))
        case=ET.SubElement(report,'testcase',{'name':test.id(),'classname':'ui.'+TRANSPORT,'time':str(elapsed)})
        if kind:
            ET.SubElement(case,kind).text=detail
report=ET.Element('testsuite',{'name':'browser-'+TRANSPORT})
if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2,resultclass=Report).run(unittest.defaultTestLoader.loadTestsFromTestCase(UI))
    report.set('tests',str(len(report.findall('testcase'))));report.set('failures',str(len(result.failures)));report.set('errors',str(len(result.errors)));report.set('skipped',str(len(result.skipped)))
    (ROOT/'build/reports').mkdir(parents=True,exist_ok=True)
    ET.ElementTree(report).write(ROOT/'build/reports/browser.xml',encoding='utf-8',xml_declaration=True)
    raise SystemExit(0 if result.wasSuccessful() else 1)
