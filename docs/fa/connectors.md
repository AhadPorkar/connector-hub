# قابلیت کانکتورها و ماتریس تأیید

[English](../en/connectors.md) · [Deutsch](../de/connectors.md).

وجود پیاده‌سازی، قابل‌بارگذاری‌بودن درایور و آزمایش روی یک سامانهٔ واقعی، سه ادعای متفاوت هستند. این نسخه پشتیبانی از همهٔ پایگاه‌های داده و سرویس‌ها را ادعا نمی‌کند.

| نوع | پیاده‌سازی | آزمون انجام‌شده | محدودیت مهم |
|---|---|---|---|
| LOCAL | CRUD پایدار، فیلتر، صفحه و نسخه | واحد و HTTP واقعی Hub | ذخیره‌ساز نمونه، نه رابطه‌ای |
| REST | هفت متد، JSON/Text/Form/Multipart، باینری | شبیه‌ساز شبکهٔ محلی | بدون دریافت OAuth، Streaming یا WebSocket |
| SOAP | ۱٫۱/۱٫۲، Action/Fault، بازرسی WSDL مستقل | Parser و شبکهٔ محلی | بدون Import، WS-Security و Binding کامل |
| GRAPHQL | سند/متغیر HTTP و تشخیص خطا | شبکهٔ محلی | بدون Subscription/Federation |
| ODATA | HTTP و top/skip محدود | شبکهٔ محلی | بدون Batch/Delta کامل |
| JDBC | متادیتا، CRUD/SQL متصل، IN/OUT/INOUT، تابع | دوازده آزمون HSQLDB محلی ۱٫۸ | بدون تأیید عمومی فروشندگان |
| REDIS | RESP2 مستقل، GET/SET NX/SET XX/DEL، Auth، TTL و TLS اختیاری | پروتکل و واحد | بدون آزمون زنده، Cluster/Sentinel/Streams |
| MONGODB | اتصال اختیاری درایور رسمی و CRUD محدود | فقط مسیر نبود درایور | بدون پذیرش زنده، SRV/Multi-host/تراکنش |

آزمون شبیه‌ساز، آزمایش واقعی شبکهٔ کنترل‌شده است، نه تأیید همهٔ فروشندگان. JAR قدیمی HSQLDB موجود در محیط فقط برای آزمون استفاده شده، همراه محصول نیست و توصیهٔ استقرار نیست.

## نصب JDBC

JAR درایور بررسی‌شده و وابستگی‌هایش را در `drivers/` بگذارید و با اسکریپت Classpath اجرا کنید. `java -jar` به‌تنهایی آن‌ها را بارگذاری نمی‌کند. دریافت اختیاری و صریح با `mvn -f drivers/pom.xml -Ppostgres dependency:copy-dependencies` انجام می‌شود. Profileهای mysql، mariadb و mongodb نیز وجود دارند. مسیر Maven به اینترنت نیاز دارد و اینجا اجرا نشده است.

نمونهٔ کلاس‌ها: PostgreSQL برابر `org.postgresql.Driver`، MySQL برابر `com.mysql.cj.jdbc.Driver`، MariaDB برابر `org.mariadb.jdbc.Driver`، SQL Server برابر `com.microsoft.sqlserver.jdbc.SQLServerDriver` و Oracle برابر `oracle.jdbc.OracleDriver`. SQLite، H2، DB2، SAP HANA و دیگر محصولات JDBC مسیر احتمالی اتصال هستند، نه پشتیبانی تأییدشده. نسخه، مجوز، سازگاری Java، TLS، متادیتا، پروسیجر و Timeout باید جدا بررسی شوند.

```json
{
  "name":"PostgreSQL lab","type":"JDBC",
  "config":{"url":"jdbc:postgresql://db.internal:5432/integration?sslmode=verify-full","driverClass":"org.postgresql.Driver"},
  "secrets":{"username":"least_privileged_account","password":"provide-outside-source-control"}
}
```

بهره‌بردار باید Prefix باریک JDBC را مجاز کند، نه `jdbc:` عمومی. بررسی Prefix جای Parser کامل یا Egress Firewall نیست. CRUD شناسه‌ها را با متادیتا کنترل می‌کند. UPDATE/DELETE به کلید اصلی واقعی تک‌ستونی نیاز دارند. ستون نسخهٔ اختیاری، Compare-and-swap می‌دهد. QUERY/EXECUTE مقادیر را Bind می‌کنند، ولی خود SQL کد مورداعتماد است. Connection Pool وجود ندارد و اتصال برای هر اجرا باز می‌شود.

## HTTP، Redis و MongoDB

احراز هویت NONE، BASIC، BEARER و API_KEY پیاده شده است. Bearer ثابت، OAuth2 Client Credentials یا تمدید خودکار نیست. مسیر و Query کدگذاری، Header کنترل، Redirect مسدود و پاسخ محدود می‌شود. جایگزینی SOAP از XML escaping استفاده می‌کند.

کلید Redis با `ch:<tenant>:<namespace>:` شروع می‌شود. دو اتصال با مقصد و Namespace یکسان در یک مستأجر عمداً یک فضای کلید دارند. ACL سرور همچنان لازم است. MongoDB از پایگاه و مجموعهٔ صریح، شناسهٔ رشته‌ای و فیلتر برابری محدود استفاده می‌کند. جداسازی ردیف در پایگاه خارجی خودکار نیست؛ حساب/دیتابیس جدا یا فیلتر بررسی‌شده لازم است. جداسازی کاتالوگ را با جداسازی دادهٔ مقصد اشتباه نگیرید.

gRPC، Kafka، JMS، AMQP، SFTP، FTP، MQTT، LDAP، Elasticsearch، Cassandra، DynamoDB و Bindingهای خاص WCF پیاده نشده‌اند. SPI زمان شروع و نمونهٔ افزونه برای توسعه وجود دارد. Camel یا SDK رسمی گزینهٔ توسعه است، نه قابلیت آمادهٔ پنهان.
