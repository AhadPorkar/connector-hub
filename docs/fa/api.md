# مرجع HTTP API

[English](../en/api.md) · [Deutsch](../de/api.md).

## قراردادهای عمومی

به‌جز `/healthz`، فایل‌های رابط و مستندات، مسیرها به `Authorization: Bearer <key>` نیاز دارند. درخواست دارای بدنه باید `Content-Type: application/json` داشته باشد. `Accept-Language` زبان en، de یا fa را برای پیام انتخاب می‌کند؛ کد خطا تغییر نمی‌کند. پاسخ `X-Request-ID` دارد. CORS عمومی، کوکی احراز هویت و اجرای ناشناس سرویس فعال نیست.

اشیای تنظیمات نسخهٔ عددی و ETag دارند. در PUT، `version` فعلی را بفرستید. حذف محافظت‌شده از `If-Match: "1"` استفاده می‌کند. منبع متعلق به مستأجر دیگر عمداً یافت‌نشده نمایش داده می‌شود. مجوزها مستقل از رابط، در سرور کنترل می‌شوند.

| متد | مسیر | کاربرد |
|---|---|---|
| GET | `/api/me` و `/api/dashboard` | هویت، قابلیت‌ها و آمار محدود |
| GET, POST | `/api/connections` | فهرست و ساخت اتصال |
| GET, PUT, DELETE | `/api/connections/{id}` | مدیریت اتصال |
| POST | `/api/connections/{id}/discover` و `/test` | بازرسی متادیتا؛ نه سلامت عمومی |
| POST | `/api/connections/{id}/generate-crud` | ساخت پیش‌نویس CRUD |
| GET, POST | `/api/providers` | فهرست و ساخت پروایدر |
| GET, PUT, DELETE | `/api/providers/{id}` | مدیریت نسخه‌دار |
| POST | `/api/providers/{id}/execute` | اجرای پیش‌نویس توسط طراح/مدیر |
| POST | `/api/providers/{id}/publish` | انتشار با `{"version":1}` |
| GET | `/api/publications` | متادیتای انتشار |
| DELETE | `/api/publications/{id}` | لغو انتشار |
| POST | `/api/services/{slug}/v{revision}` | فراخوانی منتشرشده و مجاز |
| GET | `/api/openapi` | قرارداد OpenAPI فیلترشده با Scope |
| POST | `/api/import/openapi` | واردکردن زیرمجموعهٔ JSON |
| GET | `/api/jobs` و `/api/jobs/{id}` | صف و نتیجه |
| POST | `/api/jobs/{id}/cancel` و `/retry` | لغو صف یا تکرار خواندن امن |
| GET, POST | `/api/keys` | مدیریت کلید |
| DELETE | `/api/keys/{id}` | ابطال کلید غیر اولیه |
| GET | `/api/audit` | متادیتای ممیزی مستأجر |
| GET, PUT | `/api/settings` | تنظیمات مستأجر |
| GET | `/api/export` و `/api/idempotency` | خروجی پالایش‌شده و متادیتای تطبیق |
| POST | `/api/idempotency/{id}/resolve` | ثبت نتیجهٔ مستقل تأییدشده |
| POST | `/api/maintenance/prune` | حذف تاریخچهٔ نهایی قدیمی |

OpenAPI تولیدشده، سرویس‌های مصرف‌کننده را مستند می‌کند، نه همهٔ APIهای مدیریتی.

## بدنهٔ اجرا

```json
{
  "input": {"customerId":42},
  "page": {"index":0,"size":25,"filters":[{"field":"status","op":"eq","value":"active"}],"orders":[{"field":"id","direction":"asc"}]},
  "async": false
}
```

فقط پارامترهای INPUT تعریف‌شده مجازند. رفتار Filter و Paging وابسته به کانکتور است. REST عمومی، فیلتر دلخواه را خودکار به قرارداد مقصد تبدیل نمی‌کند؛ نگاشت صریح لازم است. همهٔ URLها با POST و همین پوشش فراخوانی می‌شوند، نه GET ناشناس یا SOAP Server جدید.

نوشتن و عملیات غیرایمن برای تکرار به `Idempotency-Key` نیاز دارند. موفقیت شامل status، data نگاشت‌شده و متادیتای درخواست/زمان است. حالت Async، کد ۲۰۲، شناسهٔ Job و QUEUED می‌دهد. با همان هویت مجاز، نتیجه را از `/api/jobs/{id}` بخوانید. وضعیت‌های نهایی SUCCEEDED، FAILED، UNKNOWN و CANCELLED هستند.

## خطا و تطبیق

قالب خطا `{"error":{"code":"VERSION_CONFLICT","message":"توضیح ترجمه‌شده","requestId":"..."}}` است. مرزهای رایج: ۴۰۰ ورودی، ۴۰۱ احراز هویت، ۴۰۳ دسترسی، ۴۰۴ منبع، ۴۰۹ نسخه/تکرار/ابهام، ۴۱۲ درایور، ۴۱۳ اندازه، ۴۱۵ مدیا، ۴۲۹ نرخ و ۵۰۲/۵۰۴ مقصد. منطق برنامه را روی code بنویسید، نه متن ترجمه.

مدیر پس از بررسی مقصد می‌تواند UNKNOWN را با CONFIRMED_SUCCESS و نتیجه یا CONFIRMED_NO_EFFECT تعیین تکلیف کند. تصمیم دوم، اثر FAILED را زیر کلید قبلی نگه می‌دارد. این API عملیاتی را تکرار یا برنمی‌گرداند. نمونه‌های کامل در `examples/` و اسکریپت `publish-local.py` موجود است. خروجی تنظیمات جای Backup یا سامانهٔ عمومی جلوگیری از نشت اطلاعات نیست.
