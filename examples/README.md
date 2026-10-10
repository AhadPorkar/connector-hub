# Examples / Beispiele / نمونه‌ها

## English

Connection JSON files can be submitted to `POST /api/connections`; provider files target `POST /api/providers`. Replace `REPLACE_WITH_CREATED_CONNECTION_ID` with the returned connection ID. Placeholder credentials must be replaced deliberately. Examples do not establish that an external database, schema, routine or service exists. HTTP examples use the local demo simulator.

The JDBC query uses quoted `CH_ACCEPTANCE`/`ID`/`NAME` identifiers for the disposable acceptance schema; that test table is dropped when acceptance completes. Adapt it to an independently created sample table before normal invocation. Procedure and function names are illustrative and require vendor-specific implementation and validation. Redis and MongoDB examples require separately installed servers and reviewed security settings.

To create, publish, invoke and replay a real LOCAL provider against an already running hub, set `HUB_URL` and `HUB_TOKEN`, then run `python examples/publish-local.py`. The script intentionally leaves its synthetic provider and data in the selected workspace. Use a development workspace. Do not paste real keys into source or shell history. See the full API guides for key scoping and production boundaries.

Build the trusted extension with `bash scripts/build-example-plugin.sh`; restart using the launch scripts, choose ECHO as the connection type, and create a READ provider. The example returns resolved inputs and the tenant, without network access. Plugins execute with application privileges. Never load untrusted JARs.

## Deutsch

Verbindungs- und Provider-JSON sind Beispiele für die entsprechenden API-POSTs. Die Platzhalter-ID muss durch die tatsächlich erzeugte Verbindungs-ID ersetzt werden. Externe Tabellen, Routinen und Dienste müssen separat vorhanden sein. Das JDBC-Beispiel ist an ein eigenes Testschema anzupassen. `publish-local.py` demonstriert echte Veröffentlichung und idempotente Wiederholung mit synthetischen Daten. Es hinterlässt diese Daten im Entwicklungsarbeitsbereich. ECHO ist ein vertrauenswürdiges SPI-Beispiel, kein Sandboxing-System.

## فارسی

فایل اتصال و پروایدر برای POST به API مربوط هستند. شناسه جایگزین را با شناسه واقعی اتصال عوض کنید. جدول، روال یا سرویس خارجی باید جداگانه وجود داشته باشد؛ نمونه JDBC را با ساختار آزمایشی خود تطبیق دهید. اسکریپت `publish-local.py` انتشار، فراخوانی و تکرار بدون ایجاد مجدد را با داده ساختگی اجرا می‌کند و داده را در محیط توسعه نگه می‌دارد. افزونه ECHO نمونه کد مورد اعتماد است، نه محیط ایزوله برای JAR ناشناس.
