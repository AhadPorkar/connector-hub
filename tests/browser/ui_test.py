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
from pathlib import Path
import time
import unittest
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from playwright.sync_api import sync_playwright

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
        fixture.HubHTTP.setUpClass()
        SCREENSHOTS.mkdir(parents=True, exist_ok=True)
        cls.pw = sync_playwright().start()
        options = {'headless': True}
        if os.environ.get('HUB_BROWSER_EXECUTABLE'):
            options['executable_path'] = os.environ['HUB_BROWSER_EXECUTABLE']
        cls.browser = cls.pw.chromium.launch(**options)
        cls.page = cls.browser.new_page(viewport={'width':1440,'height':1050})
        cls.page.set_default_timeout(8000)
        cls.errors = []
        cls.page.on('pageerror', lambda e: cls.errors.append(str(e)))
        cls.page.on('dialog', lambda dialog: dialog.accept())
        if TRANSPORT == 'harness':
            def bridge(data):
                url = data['url']
                if not url.startswith('/') or url.startswith('//'):
                    raise ValueError('Harness only accepts same-origin relative URLs')
                req = urllib.request.Request(fixture.HubHTTP.base+url, data=data.get('body', '').encode() if data.get('body') else None, headers=data.get('headers', {}), method=data.get('method','GET'))
                try: response = urllib.request.urlopen(req, timeout=20)
                except urllib.error.HTTPError as error: response = error
                with response: return {'status':response.status, 'body':response.read().decode(), 'headers':dict(response.headers)}
            cls.page.expose_function('httpBridge', bridge)
            cls.page.set_content('<html><head></head><body><div id="app"></div><div id="modal-root"></div><div id="toast" role="status"></div></body></html>')
            cls.page.add_style_tag(content=(ASSETS/'app.css').read_text())
            cls.page.evaluate('''() => {
              const memory = new Map();
              Object.defineProperty(window, 'localStorage', {value:{getItem:k=>memory.get(k)||null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)}});
              window.fetch=async (url,o={})=>{const r=await window.httpBridge({url,method:o.method||'GET',body:o.body,headers:o.headers||{}});return new Response(r.status===204?null:r.body,{status:r.status,headers:r.headers});};
            }''')
            logo='data:image/svg+xml;base64,'+base64.b64encode((ASSETS/'favicon.svg').read_bytes()).decode()
            cls.page.evaluate('''logo => {new MutationObserver(()=>document.querySelectorAll('img[src="/favicon.svg"]').forEach(img=>img.src=logo)).observe(document.body,{childList:true,subtree:true});}''',logo)
            cls.page.add_script_tag(content='(async()=>{'+(ASSETS/'app.js').read_text()+'})().catch(e=>{throw e;});')
        else:
            cls.page.goto(fixture.HubHTTP.base)
        cls.login()
    @classmethod
    def login(cls):
        cls.page.locator('#access-token').wait_for()
        cls.page.locator('#access-token').fill(fixture.TOKEN)
        cls.page.locator('button[type=submit]').click()
        cls.page.locator('.hero').wait_for()
    @classmethod
    def tearDownClass(cls):
        cls.browser.close(); cls.pw.stop(); fixture.HubHTTP.tearDownClass()
    def setUp(self):
        if self.page.locator('#access-token').count(): self.login()
        self.page.keyboard.press('Escape')
        self.page.set_viewport_size({'width':1440,'height':1050})
        self.page.locator('#language').select_option('en')
        self.page.wait_for_timeout(120)
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
        self.page.wait_for_function('document.querySelector("#execution-result").textContent.includes("SUCCEEDED")')
        result=json.loads(self.page.locator('#execution-result').inner_text())
        self.assertEqual('SUCCEEDED',result['status'])
        self.assertGreater(len(result['data']),0)
        self.page.screenshot(path=str(SCREENSHOTS/'execution-en.png'),full_page=True)
    def test_03_invalid_json_does_not_execute(self):
        self.navigate('providers')
        self.page.locator('[data-run]').first.click()
        self.page.locator('#execution-input').fill('{broken')
        self.page.locator('#run-provider').click()
        self.page.wait_for_function('document.querySelector("#modal-error").textContent.length>0')
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
        self.page.wait_for_timeout(180)
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
    def startTest(self,test): super().startTest(test); self.started=time.perf_counter()
    def addSuccess(self,test): super().addSuccess(test); self.record(test,None)
    def addFailure(self,test,err): super().addFailure(test,err); self.record(test,self._exc_info_to_string(err,test))
    def addError(self,test,err): super().addError(test,err); self.record(test,self._exc_info_to_string(err,test))
    def record(self,test,error):
        case=ET.SubElement(report,'testcase',{'name':test.id(),'classname':'ui.'+TRANSPORT,'time':str(time.perf_counter()-self.started)})
        if error: ET.SubElement(case,'failure').text=error
report=ET.Element('testsuite',{'name':'browser-'+TRANSPORT})
if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2,resultclass=Report).run(unittest.defaultTestLoader.loadTestsFromTestCase(UI))
    report.set('tests',str(result.testsRun));report.set('failures',str(len(result.failures)));report.set('errors',str(len(result.errors)))
    (ROOT/'build/reports').mkdir(parents=True,exist_ok=True)
    ET.ElementTree(report).write(ROOT/'build/reports/browser.xml',encoding='utf-8',xml_declaration=True)
    raise SystemExit(0 if result.wasSuccessful() else 1)
