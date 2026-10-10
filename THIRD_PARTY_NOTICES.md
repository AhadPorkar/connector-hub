# Third-party notices / Hinweise / اطلاعیه اجزای خارجی

## English

The baseline Java source uses JDK APIs and has no third-party Java runtime JAR bundled in this archive. The new source is MIT-licensed; the JDK distribution retains its own license. JDBC vendor JARs and the optional MongoDB driver are not included. Resolve them deliberately, review their exact licenses/advisories and complete vendor acceptance. The optional `drivers/pom.xml` is a convenience, not a legal review or a guarantee that every dependency was resolved here.

Playwright is a development-only test dependency. Chromium, Python, Maven, Docker, GitHub Actions and Eclipse Temurin images are external tools/components with their own licenses and terms. No browser, font, vendor driver or container image is redistributed in the source ZIP. The legacy HSQLDB installation used for local acceptance was supplied by the execution environment and is not included or recommended.

Protocol names and vendor names identify interoperability targets; they do not imply endorsement, certification, trademark ownership or affiliation. The GraphBPMS-style provider mapping describes independently implemented concepts, not proprietary source reuse or compatibility certification.

## Deutsch

Die Java-Basis enthält keine gebündelten fremden Laufzeit-JARs. JDK, optionale Datenbanktreiber, Browser, Testwerkzeuge, Containerbilder und CI-Aktionen behalten ihre eigenen Lizenzen. Treiber werden bewusst separat beschafft und müssen rechtlich, sicherheitstechnisch und funktional geprüft werden. Das für lokale Tests vorhandene alte HSQLDB wird nicht mitgeliefert oder empfohlen. Namen von Anbietern und Protokollen bedeuten keine Zertifizierung oder Partnerschaft.

## فارسی

پایه جاوا JAR اجرایی خارجی همراه ندارد. JDK، درایور اختیاری، مرورگر، ابزار آزمون، تصویر کانتینر و گردش CI مجوز خود را دارند. دریافت و بررسی حقوقی، امنیتی و پذیرش درایورها مستقل است. HSQLDB قدیمی موجود در محیط آزمون همراه خروجی نیست و توصیه نمی‌شود. هیچ فایل فونت یا مرورگر توزیع نشده است. نام سازنده و پروتکل به معنی تأیید، همکاری یا گواهی سازگاری نیست.
