# Trusted extensions / Vertrauenswürdige Erweiterungen / افزونه مورد اعتماد

Plugins implement `io.ahad.connectorhub.core.Connector` and declare the implementation in `META-INF/services/io.ahad.connectorhub.core.Connector`. Build the included ECHO example with `bash scripts/build-example-plugin.sh`. Start through the launch scripts to include `plugins/*`. Restart is required; no untrusted upload or hot-deployment API is provided.

Plugins share the process, filesystem and privileges of Connector Hub. They are not sandboxed. Review, test, sign and distribute them through an owner-controlled process. Do not commit binary JARs here. Protocol capability must be demonstrated with real acceptance tests.

Erweiterungen laufen mit den Rechten der Anwendung und sind nicht isoliert. Nur geprüfte, vertrauenswürdige JARs laden. Ein Neustart ist erforderlich. Die ECHO-Erweiterung ist ein ausführbares Beispiel, keine universelle Protokollunterstützung.

افزونه با دسترسی خود برنامه اجرا می‌شود و ایزوله نیست. فقط JAR بررسی‌شده و مورد اعتماد را بارگذاری کنید. راه‌اندازی مجدد لازم است. نمونه ECHO قابل ساخت است، اما به معنی پشتیبانی از همه پروتکل‌ها نیست.
