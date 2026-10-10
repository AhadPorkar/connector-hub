# آزمون‌ها و مرز اعتبار نتایج

[English](../en/testing.md) · [Deutsch](../de/testing.md).

نتایج از اجرای واقعی فرمان‌ها ثبت شده‌اند، نه حدس بر اساس وجود فایل تست. [verification.json](../evidence/verification.json) و پوشهٔ `docs/evidence/reports/` شمارش نهایی و محیط را نشان می‌دهند. اجرای مجدد، گزارش تازه را در `build/reports/` می‌نویسد و شواهد تاریخی را خودکار عوض نمی‌کند. درصد پوشش خط/شاخه اندازه‌گیری نشده و ادعا نمی‌شود.

## بخش‌های آزمون

Core شامل JSON و Unicode، Fingerprint، نوع و منبع پارامتر، نگاشت، SQL Lexer، رمزنگاری، قفل، کپی مستقل، Restart، تغییر هم‌زمان، نقش، Retry، Circuit و Rate-limit است. کانکتورها XXE/SOAP/WSDL، مقصد، RESP2، CRUD و نسخه و جداسازی LOCAL، نبود درایور و تشخیص Retry را می‌سنجند. آزمون سرور کلید، Patch محرمانه، Snapshot و بازیابی را می‌سنجد؛ به‌ویژه Restart نباید DONE/FAILED را UNKNOWN کند.

آزمون HTTP فرایند واقعی Java و شبیه‌ساز محلی را اجرا می‌کند و احراز هویت، مستأجر، نقش و Scope، چرخهٔ تنظیمات، CRUD، نسخهٔ منتشرشده، OpenAPI، متد و Bodyهای HTTP، Auth مقصد، Redirect/اندازه، نوشتن مبهم، تطبیق، صف و دوام پس از Restart را می‌سنجد.

```bash
bash scripts/test.sh
python -m pip install -r tests/browser/requirements.txt
python -m playwright install chromium
python tests/browser/ui_test.py
```

## مرورگر

Playwright به‌صورت پیش‌فرض Navigation عادی دارد. در محیط ساخت، سیاست مدیریتی مرورگر همهٔ Navigationها را بسته بود و این سیاست تغییر داده نشد. حالت `HUB_UI_TRANSPORT=harness` منابع واقعی HTML/CSS/JS را در صفحهٔ جدا رندر و Fetch را به سرور واقعی Java متصل می‌کند. تعامل، ترجمه، RTL، Layout و ذخیرهٔ تغییرات واقعی را بررسی می‌کند، اما Navigation بومی و اجرای CSP را تأیید نمی‌کند. تصاویر خروجی از وضعیت واقعی رندرشده هستند، نه Mockup. گواهی جامع Accessibility ادعا نشده است.

## پذیرش پایگاه داده

Runner مربوط به JDBC جدول `CH_ACCEPTANCE` را ایجاد و حذف می‌کند. فقط با `HUB_ALLOW_DESTRUCTIVE_TESTS=true` و روی دیتابیس دورریختنی اجرا شود. متغیرهای `HUB_TEST_JDBC_URL`، `HUB_TEST_JDBC_DRIVER`، `HUB_TEST_JDBC_USER` و `HUB_TEST_JDBC_PASSWORD` را تنظیم، درایور را نصب و `bash scripts/test-jdbc.sh` را اجرا کنید.

دوازده آزمون واقعی متادیتا، CRUD و خطا با JAR موجود HSQLDB نسخهٔ ۱٫۸٫۰ اجرا شده است. این موضوع تأیید PostgreSQL، SQL Server، Oracle، MySQL، MariaDB، SQLite، H2، DB2 یا SAP HANA نیست. Workflow دستی PostgreSQL آماده شده ولی اینجا اجرا نشده است. پروسیجر، TLS و Timeout هر درایور آزمون اختصاصی می‌خواهد.

## موارد انجام‌نشده

Redis زنده، MongoDB با درایور رسمی، SOAP فروشندهٔ واقعی، Docker، حل وابستگی Maven، ویندوز، Java 25 و GitHub Actions میزبانی‌شده محلی اجرا نشده‌اند. آزمون نفوذ، بار مداوم، HA/Failover و ارزیابی انطباق انجام نشده است. کانکتور جدید علاوه بر مسیر موفق، به ورودی نادرست، دسترسی ناکافی، Timeout، Write مبهم، تکرار، جداسازی مستأجر و Restart نیاز دارد. شمار زیاد تست جای پذیرش نسخه و قرارداد واقعی مشتری را نمی‌گیرد.
