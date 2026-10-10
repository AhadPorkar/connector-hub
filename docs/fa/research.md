# منابع پژوهش و تصمیم فناوری

[English](../en/research.md) · [Deutsch](../de/research.md).

تاریخ بررسی ۲۰۲۶-۱۰-۱۰ است. منبع رسمی مبنای تصمیم است، نه ادعای نصب کتابخانه یا اجرای کامل استاندارد. نسخهٔ اختیاری و سازگاری باید پیش از استقرار دوباره بررسی شود.

| منبع اصلی | استفاده یا تصمیم |
|---|---|
| [JEP 444](https://openjdk.org/jeps/444) | Virtual Thread در Java 21؛ هم‌زمانی همچنان محدود است. |
| [JDK HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html) | انتقال HTTP واقعی بدون Redirect. |
| [CallableStatement](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/CallableStatement.html) | پروسیجر و تابع؛ پذیرش فروشنده همچنان لازم است. |
| [Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html) | بررسی‌شده، نه وابستگی هستهٔ آفلاین. |
| [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) | مرجع OIDC آینده، پیاده نشده. |
| [Apache Camel](https://camel.apache.org/components/4.22.x/index.html) | گزینهٔ پروتکل‌های بیشتر، بسته‌بندی نشده. |
| [Apache CXF](https://cxf.apache.org/) | گزینهٔ SOAP و WS-* کامل. |
| [Resilience4j](https://resilience4j.readme.io/docs/retry) | مرجع سیاست؛ Runner محدود داخلی. |
| [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html) | قرارداد منتشرشده و Import محدود. |
| [SOAP 1.2](https://www.w3.org/TR/soap12-part1/) | Envelope/Action/Fault، نه WS-Security کامل. |
| [GraphQL](https://spec.graphql.org/September2025/) | سند و متغیر، بدون Subscription. |
| [OWASP SSRF](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html) | Allowlist و نیاز دفاع خارجی Egress/DNS. |
| [OWASP XXE](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html) | منع DTD، Entity و Import. |
| [MongoDB Java](https://www.mongodb.com/docs/drivers/java/sync/current/) | اتصال اختیاری رسمی؛ بدون پذیرش زنده. |
| [MongoDB Pool](https://www.mongodb.com/docs/drivers/java/sync/current/connection/specify-connection-options/connection-pools/) | نیاز به بهبود Lifecycle مشتری. |
| [Redis/Jedis](https://redis.io/docs/latest/develop/clients/jedis/) | مشتری بالغ بررسی شد؛ نسخهٔ حاضر RESP2 محدود است. |
| [pgJDBC](https://jdbc.postgresql.org/download/) | Profile ثابت اختیاری، نه ادعای آخرین نسخه. |
| [MySQL](https://dev.mysql.com/doc/relnotes/connector-j/en/) | بررسی درایور، مجوز و سازگاری. |
| [MariaDB](https://mariadb.com/docs/release-notes/connectors/java/) | Profile درایور اختیاری. |
| [Microsoft JDBC](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver) | مسیر پذیرش دستی SQL Server. |
| [Testcontainers](https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/) | رویکرد آزمون واقعی؛ Docker اینجا موجود نبود. |
| [Checkout Releases](https://github.com/actions/checkout/releases) | خانوادهٔ Workflow بررسی شد؛ اجرای میزبانی‌شده انجام نشد. |
| [Artifact Releases](https://github.com/actions/upload-artifact/releases) | تنظیم تحویل شواهد، نه نتیجهٔ CI ساختگی. |

JDBC رابط را استاندارد می‌کند، نه همهٔ نوع، متادیتا، تراکنش، احراز هویت و خطاهای فروشندگان را. HTTP انتقال را تعریف می‌کند، نه معنای تجاری همهٔ APIها را. بنابراین ادعای سازگاری باید محصول و زیرمجموعهٔ دقیق را نام ببرد.

Timeout می‌تواند بعد از Commit رخ دهد؛ تکرار ممکن است عملیات برگشت‌ناپذیر را دوباره انجام دهد. Hub خواندن امن را جدا، Claim را پایدار و نوشتن نامعلوم را قرنطینه می‌کند. Idempotency بومی مقصد و تطبیق مستقل همچنان مهم‌اند. اجرای کد دلخواه تولیدشده با AI مرز اعتماد را زیاد می‌کند؛ این نسخه به نگاشت اعلانی و افزونهٔ بررسی‌شدهٔ زمان شروع محدود است.
