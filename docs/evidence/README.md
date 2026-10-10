# Verification evidence / Prüfnachweise / شواهد آزمون

[Machine-readable record](verification.json) · [English guide](../en/testing.md) · [Deutsche Anleitung](../de/testing.md) · [راهنمای فارسی](../fa/testing.md).

## English

These files record actual local execution of the supplied implementation. Six suites currently contain 319 passing tests: core 111, connectors 53, server 23, HTTP 84, JDBC acceptance 12 and browser 36. The authoritative environment, report hashes and exclusions are in `verification.json`. Reports are copied from actual runner outputs, not generated from intended coverage. No coverage percentage or production guarantee is claimed.

JDBC acceptance used the environment's legacy HSQLDB 1.8.0 installation. It is not bundled, recommended or evidence of other vendors. Browser evidence used an explicitly labeled Playwright harness because the managed Chromium installation blocked native navigation. Real application assets were rendered and fetch calls reached the actual running Java server. Native navigation, CSP enforcement and normal browser network semantics remain unverified.

Screenshots show actual application states, not mockups. The sample data and keys were synthetic. The demo normally starts with five connections; screenshots after creation tests show an additional test connection.

[English dashboard](dashboard-en.png) · [German providers](providers-de.png) · [Persian dashboard](dashboard-fa.png) · [Mobile Persian](dashboard-fa-mobile.png) · [Provider editor](provider-editor-en.png) · [Execution](execution-en.png).

Normal tests write `build/reports/`. To deliberately replace historical evidence, execute all suites successfully, inspect the outputs, and run `python scripts/capture_evidence.py`. That script refuses reports containing failures but cannot prove the provenance of files copied from another machine; review execution logs and environment accordingly.

## Deutsch

Die Dateien dokumentieren tatsächlich ausgeführte lokale Tests. Die sechs Suiten enthalten 319 erfolgreiche Prüfungen. HSQLDB-Abnahme bedeutet keine Unterstützung aller Datenbankanbieter. Der Browser-Harness verwendet echte Oberflächenressourcen und den laufenden Java-Server, prüft jedoch keine native Navigation oder CSP-Durchsetzung. Die Screenshots zeigen reale Zustände mit synthetischen Daten. Umgebung, Hashes und offene Prüfungen stehen im JSON-Nachweis.

## فارسی

این فایل‌ها حاصل اجرای واقعی محلی هستند. شش مجموعه در مجموع ۳۱۹ آزمون موفق دارند. پذیرش HSQLDB به معنی تأیید سایر سازندگان نیست. واسط آزمون مرورگر، منابع واقعی رابط را به سرور جاوا متصل کرده اما ناوبری عادی و اعمال CSP را تأیید نکرده است. تصاویر از وضعیت واقعی با داده ساختگی گرفته شده‌اند. محیط، هش گزارش‌ها و موارد بررسی‌نشده در فایل JSON ثبت شده‌اند.
