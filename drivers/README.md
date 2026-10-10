# Optional drivers / Optionale Treiber / درایورهای اختیاری

## English

No vendor JAR is bundled. Place reviewed runtime driver JARs here, then start with `scripts/run.sh` or `scripts/run.ps1`. Direct `java -jar` does not load this directory. Match the JDBC driver class, URL, database version, TLS settings and account privileges to the exact vendor documentation.

The optional Maven descriptor has profiles `postgres`, `mysql`, `mariadb` and `mongodb`. For example, from the repository root, `mvn -f drivers/pom.xml -Ppostgres package` resolves and copies that profile's dependencies here. These profiles were not resolved in the offline authoring environment. Versions are deliberate example pins, not claims of the latest or vulnerability-free release. Inspect transitive dependencies, licenses and advisories before use.

A JDBC-compatible driver does not guarantee that this hub's metadata, CRUD, routine or paging behavior supports the vendor. Run the disposable acceptance suite and vendor-specific tests. Never use production credentials with destructive acceptance tests. Exclude all downloaded JARs and secrets from commits.

## Deutsch

Treiber-JARs werden nicht mitgeliefert. Geprüfte Dateien hier ablegen und die Startskripte verwenden. Maven-Profile sind optional und wurden lokal nicht aufgelöst. Treiberversion, Lizenz, Sicherheitslage, TLS, Metadaten und Routinen separat prüfen. JDBC-Kompatibilität allein ist keine Anbieterabnahme. Destruktive Tests nur gegen Wegwerfdatenbanken ausführen.

## فارسی

درایور سازنده همراه نیست. فایل‌های بررسی‌شده را اینجا قرار دهید و از اسکریپت اجرا استفاده کنید. پروفایل Maven اختیاری است و در محیط اولیه حل وابستگی نشده است. نسخه، مجوز، امنیت، TLS، متادیتا و روال‌ها باید جداگانه بررسی شوند. سازگاری JDBC به‌تنهایی پذیرش سازنده نیست. آزمون مخرب فقط روی پایگاه داده دورریختنی مجاز است.
