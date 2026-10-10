# ساخت و استقرار

[English](../en/deployment.md) · [Deutsch](../de/deployment.md).

## مسیر آفلاین

برای کامپایل به JDK نسخهٔ ۲۱ یا بالاتر نیاز است، نه صرفاً JRE. Python 3 برای آزمون HTTP و بررسی مخزن لازم است؛ Node برای کنترل Syntax جاوااسکریپت اختیاری است. ساخت و اجرای دمو به Maven، npm، CDN، پایگاه داده یا اینترنت نیاز ندارد.

```bash
bash scripts/build.sh
bash scripts/run-demo.sh --port 8080 --data var
bash scripts/test.sh
```

دستورهای PowerShell عبارت‌اند از `./scripts/build.ps1`، `./scripts/run-demo.ps1 --port 8080` و `./scripts/test.ps1`. اسکریپت و CI ویندوز ارائه شده، اما اینجا اجرا نشده است. JAR مستقل با `java -jar build/connector-hub.jar --demo` اجرا می‌شود. برای درایور/افزونهٔ خارجی از اسکریپت Classpath استفاده کنید. اگر ۸۰۸۰ اشغال است، `--port 18080` بدهید. پورت شبیه‌ساز ۹۰۹۱ با `HUB_DEMO_PORT` تغییر می‌کند. هر نمونه پوشهٔ دادهٔ مستقل می‌خواهد.

## متغیرهای محیطی

| متغیر | پیش‌فرض | کاربرد |
|---|---|---|
| HUB_BIND | 127.0.0.1 | آدرس شنونده |
| HUB_PORT | 8080 | پورت |
| HUB_DATA_DIR | var | پوشهٔ کاتالوگ |
| HUB_DEMO_PORT | 9091 | پورت شبیه‌ساز |
| HUB_ALLOWED_ORIGINS | خالی | Originهای دقیق با کاما |
| HUB_ALLOW_PRIVATE | false | اجازهٔ اضافی مقصد خصوصی |
| HUB_JDBC_PREFIXES | خالی | Prefixهای محدود JDBC |
| HUB_MASTER_KEY | خالی | Base64 از ۳۲ بایت تصادفی |
| HUB_ADMIN_TOKEN | خالی | کلید اولیهٔ تصادفی |
| HUB_MAX_CONCURRENT | 32 | هم‌زمانی بین ۱ تا ۲۵۶ |
| JAVA_OPTS | خالی | گزینهٔ JVM در اجراکنندهٔ POSIX |

CLI شامل `--bind`، `--port`، `--data` و `--demo` است. گزینهٔ ناشناخته رد می‌شود. مقادیر CLI برای Bind/Port/Data مقدم‌اند. Demo مقصد محلی خودش و دسترسی خصوصی را فعال می‌کند، نه حذف Allowlist را. نبود Master Key خارجی باعث ایجاد کلید توسعهٔ محلی می‌شود. توکن اولیه فقط در اولین ساخت کاتالوگ به کار می‌رود.

کلید را بیرون از کنترل نسخه با تصادف رمزنگاری بسازید؛ برای نمونه Python `secrets.token_bytes(32)` همراه Base64 و `secrets.token_urlsafe(32)`. از Secret Manager استقرار استفاده کنید. `.env`، `var/`، Log حاوی توکن شروع و کلید مجوز درایور را منتشر نکنید.

## Maven و Container

`mvn verify` ماژول‌ها را می‌سازد و آزمون Java را با exec-maven-plugin اجرا می‌کند. Surefire عمداً کنار گذاشته شده چون Runner آفلاین از Annotationهای JUnit استفاده نمی‌کند. حل وابستگی Maven اینجا اجرا نشده است. اسکریپت اصلی آزمون، HTTP واقعی و بررسی مخزن را هم اجرا می‌کند. JAR سرور درایور فروشنده را بسته‌بندی نمی‌کند.

`docker compose -f compose.demo.yml up --build` دمو با نگاشت Loopback می‌دهد. `compose.yml` کلید خارجی می‌خواهد، Demo را غیرفعال و پوشهٔ Driver/Plugin را Read-only می‌کند. فرایند با UID 10001، Volume داده، Root فایل‌سیستم Read-only و Healthcheck اجرا می‌شود. ساخت و اجرای Docker در این محیط تأیید نشده است.

JVM عمداً HTTP ارائه می‌کند. قبل از دسترسی خارجی، Reverse Proxy امن HTTPS لازم است؛ نمونهٔ Caddy موجود است. نام میزبان، گواهی، شبکه و Firewall را متناسب تنظیم کنید. Tag تصویر، Attestation تغییرناپذیر نیست؛ در انتشار تجاری Digest بررسی‌شده را Pin کنید.

## ظرفیت

یک JVM، Snapshot رمز‌شدهٔ ۶۴ MiB در حافظه، یک Worker صف، بدون Pool پایگاه داده و بدون HA. JAR معتبر در `drivers/` و افزونهٔ معتبر در `plugins/` گذاشته می‌شود و راه‌اندازی مجدد لازم است. برنامه خودکار از اینترنت JAR نمی‌گیرد. بار، مصرف حافظه و بازیابی را با workload واقعی بسنجید؛ SLA، Benchmark یا ارتقای بدون توقف ادعا نشده است.
