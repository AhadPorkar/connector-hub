#!/usr/bin/env python3
"""HTTP contract and transport regression tests against the actual Java process.
Uses Python's standard library, an isolated encrypted catalog, and loopback fixtures.
No live vendor claims are made by the simulated REST/SOAP/GraphQL/OData cases.
"""
from __future__ import annotations
import base64
import concurrent.futures
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import unittest
import urllib.error
import urllib.request
import urllib.parse
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TOKEN = "synthetic-contract-test-token-not-a-production-credential"

def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]

class HubHTTP(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="connector-hub-http-")
        cls.port, cls.upstream = free_port(), free_port()
        cls.base = f"http://127.0.0.1:{cls.port}"
        cls.log = open(Path(cls.temp.name) / "server.log", "w+")
        cls.start()
        cls.providers = {p["slug"]: p for p in cls.api("GET", "/api/providers")[1]}
        cls.connections = {p["type"]: p for p in cls.api("GET", "/api/connections")[1]}
        cls.publications = cls.api("GET", "/api/publications")[1]
    @classmethod
    def start(cls):
        cls.process = subprocess.Popen(["java", "-jar", str(ROOT / "build/connector-hub.jar"), "--demo", "--port", str(cls.port), "--data", cls.temp.name], stdout=cls.log, stderr=subprocess.STDOUT, env={**os.environ, "HUB_ADMIN_TOKEN": TOKEN, "HUB_DEMO_PORT": str(cls.upstream)})
        for _ in range(150):
            try:
                if cls.api("GET", "/healthz")[0] == 200:
                    return
            except (OSError, urllib.error.URLError):
                pass
            if cls.process.poll() is not None:
                cls.log.flush(); cls.log.seek(0)
                raise RuntimeError(cls.log.read())
            time.sleep(0.1)
        raise RuntimeError("Java server did not start")
    @classmethod
    def stop(cls):
        cls.process.terminate()
        try: cls.process.wait(timeout=15)
        except subprocess.TimeoutExpired: cls.process.kill(); cls.process.wait()
    @classmethod
    def tearDownClass(cls):
        cls.stop(); cls.log.close(); cls.temp.cleanup()
    @classmethod
    def api(cls, method, path, data=None, token=TOKEN, headers=None, raw=None):
        h = {"Accept-Language": "en", **({"Authorization": f"Bearer {token}"} if token else {})}
        if data is not None:
            raw = json.dumps(data, ensure_ascii=False).encode(); h["Content-Type"] = "application/json"
        h.update(headers or {})
        request = urllib.request.Request(cls.base + path, data=raw, method=method, headers=h)
        try: response = urllib.request.urlopen(request, timeout=15)
        except urllib.error.HTTPError as e: response = e
        with response:
            body = response.read()
            try: value = json.loads(body)
            except (ValueError, UnicodeError): value = body.decode("utf-8", errors="replace")
            return response.status, value, dict(response.headers)
    def ok(self, method, path, data=None, status=200, **kw):
        actual, value, _ = self.api(method, path, data, **kw)
        self.assertEqual(status, actual, value)
        return value
    def err(self, method, path, data, code, status=400, **kw):
        value = self.ok(method, path, data, status=status, **kw)
        self.assertEqual(code, value["error"]["code"])
        self.assertIn("requestId", value["error"])
        return value
    def provider(self, kind="REST", operation="READ", config=None, parameters=None, **extra):
        body = {"name": "Contract test", "slug": "test-" + uuid.uuid4().hex[:18], "connectionId": self.connections[kind]["id"], "operation": operation, "parameters": parameters or [], "config": config or {"method": "GET", "path": "/echo"}, "maxAttempts": 1, **extra}
        return self.ok("POST", "/api/providers", body, status=201)
    def execute(self, provider, inputs=None, key=None, status=200, **extra):
        return self.ok("POST", f"/api/providers/{provider['id']}/execute", {"input": inputs or {}, **extra}, headers={"Idempotency-Key": key} if key else {}, status=status)
    def key(self, role, **extra):
        return self.ok("POST", "/api/keys", {"role": role, "name": "Test only", **extra}, status=201)
    def test_health_is_public(self):
        self.assertEqual("UP", self.ok("GET", "/healthz", token=None)["status"])
    def test_unauthenticated_management_denied(self):
        self.err("GET", "/api/providers", None, "UNAUTHORIZED", 401, token=None)
    def test_invalid_token_denied(self):
        self.err("GET", "/api/me", None, "UNAUTHORIZED", 401, token="invalid-" * 8)
    def test_security_headers(self):
        status, _, headers = self.api("GET", "/")
        headers = {k.lower(): v for k, v in headers.items()}
        self.assertEqual(200, status)
        self.assertEqual("nosniff", headers["x-content-type-options"])
        self.assertIn("default-src 'none'", headers["content-security-policy"])
        self.assertNotIn("access-control-allow-origin", headers)
        self.assertNotIn("set-cookie", headers)
    def test_static_source_not_exposed(self):
        self.err("GET", "/Main.java", None, "NOT_FOUND", 404)
    def test_encoded_traversal_rejected(self):
        self.err("GET", "/%2e%2e/master.key", None, "NOT_FOUND", 404)
    def test_json_content_type_required(self):
        self.err("POST", "/api/providers", None, "UNSUPPORTED_MEDIA", 415, raw=b"{}")
    def test_duplicate_json_keys_rejected(self):
        self.err("POST", "/api/providers", None, "INVALID_INPUT", raw=b'{"name":"x","name":"y"}', headers={"Content-Type": "application/json"})
    def test_invalid_utf8_rejected(self):
        self.err("POST", "/api/providers", None, "INVALID_INPUT", raw=b'{"name":"\xff"}', headers={"Content-Type": "application/json"})
    def test_large_request_rejected(self):
        self.err("POST", "/api/providers", None, "BODY_TOO_LARGE", 413, raw=b"x" * 1048577, headers={"Content-Type": "application/json"})
    def test_compressed_request_rejected(self):
        self.err("POST", "/api/providers", {}, "UNSUPPORTED_MEDIA", 415, headers={"Content-Encoding": "gzip"})
    def test_locale_key_parity(self):
        values = [self.ok("GET", f"/locales/{lang}.json", token=None) for lang in ("en", "de", "fa")]
        self.assertEqual(set(values[0]), set(values[1])); self.assertEqual(set(values[0]), set(values[2]))
        self.assertNotEqual(values[0]["welcome"], values[1]["welcome"])
        self.assertTrue(any("\u0600" <= c <= "\u06ff" for c in values[2]["welcome"]))
    def test_connections_hide_secrets(self):
        c = self.ok("POST", "/api/connections", {"name": "Secret test", "type": "LOCAL", "config": {}, "secrets": {"password": "not-to-be-returned"}}, status=201)
        self.assertNotIn("not-to-be-returned", json.dumps(c)); self.assertIn("password", c["configuredSecrets"])
        self.assertNotIn("secrets", self.ok("GET", "/api/connections/" + c["id"]))
    def test_connection_version_conflict(self):
        c = self.ok("POST", "/api/connections", {"name": "Version", "type": "LOCAL", "config": {}}, status=201)
        self.err("PUT", "/api/connections/" + c["id"], {**c, "version": 0}, "VERSION_CONFLICT", 409)
    def test_connection_delete_requires_version(self):
        c = self.ok("POST", "/api/connections", {"name": "Delete", "type": "LOCAL", "config": {}}, status=201)
        self.err("DELETE", "/api/connections/" + c["id"], None, "VERSION_CONFLICT", 409)
        self.ok("DELETE", "/api/connections/" + c["id"], status=204, headers={"If-Match": '"1"'})
    def test_unlisted_destination_denied(self):
        self.err("POST", "/api/connections", {"name": "Denied", "type": "REST", "config": {"baseUrl": "https://unlisted.invalid"}}, "DESTINATION_BLOCKED", 403)
    def test_unknown_connector_denied(self):
        self.err("POST", "/api/connections", {"name": "Unsupported", "type": "MAGIC_ALL_DATABASES", "config": {}}, "CONNECTOR_UNSUPPORTED")
    def test_referenced_connection_cannot_delete(self):
        c = self.connections["LOCAL"]
        self.err("DELETE", "/api/connections/" + c["id"], None, "RESOURCE_REFERENCED", 409, headers={"If-Match": str(c["version"])})
    def test_generated_crud_has_four_distinct_operations(self):
        p = self.ok("POST", "/api/connections/" + self.connections["LOCAL"]["id"] + "/generate-crud", {"table": "case" + uuid.uuid4().hex[:8]}, status=201)
        self.assertEqual({"CREATE", "READ", "UPDATE", "DELETE"}, {x["operation"] for x in p})
    def test_complete_local_crud_with_compare_and_swap(self):
        table = "case" + uuid.uuid4().hex[:8]
        ps = self.ok("POST", "/api/connections/" + self.connections["LOCAL"]["id"] + "/generate-crud", {"table": table}, status=201)
        ps = {p["operation"]: p for p in ps}
        row = self.execute(ps["CREATE"], {"name": "Before"}, "create-" + table)["data"]
        self.assertEqual(1, row["version"])
        updated = self.execute(ps["UPDATE"], {"id": row["id"], "name": "After", "expectedVersion": 1}, "update-" + table)["data"]
        self.assertEqual(2, updated["version"])
        self.assertEqual("After", self.execute(ps["READ"], {"id": row["id"]})["data"][0]["name"])
        self.execute(ps["DELETE"], {"id": row["id"], "expectedVersion": 2}, "delete-" + table)
        self.assertEqual([], self.execute(ps["READ"], {"id": row["id"]})["data"])
    def test_provider_compare_and_swap(self):
        p = self.provider()
        self.err("PUT", "/api/providers/" + p["id"], {**p, "version": 0}, "VERSION_CONFLICT", 409)
        p = self.ok("PUT", "/api/providers/" + p["id"], {**p, "name": "New"})
        self.assertEqual(2, p["version"])
    def test_provider_duplicate_slug(self):
        p = self.provider()
        self.err("POST", "/api/providers", p, "SLUG_CONFLICT", 409)
    def test_provider_invalid_slug(self):
        p = self.provider()
        self.err("POST", "/api/providers", {**p, "slug": "../../escape"}, "SLUG_INVALID")
    def test_provider_delete_requires_version(self):
        p = self.provider()
        self.err("DELETE", "/api/providers/" + p["id"], None, "VERSION_CONFLICT", 409)
        self.ok("DELETE", "/api/providers/" + p["id"], status=204, headers={"If-Match": "1"})
    def test_published_snapshot_does_not_follow_draft(self):
        p = self.provider(config={"method": "GET", "path": "/customers"}, fieldsRoot="/data/items")
        pub = self.ok("POST", "/api/providers/" + p["id"] + "/publish", {"version": 1}, status=201)
        self.ok("PUT", "/api/providers/" + p["id"], {**p, "config": {"method": "GET", "path": "/echo"}, "fieldsRoot": "/data"})
        data = self.ok("POST", pub["path"], {"input": {}})["data"]
        self.assertIsInstance(data, list); self.assertEqual(3, len(data))
        self.err("DELETE", "/api/providers/" + p["id"], None, "RESOURCE_REFERENCED", 409, headers={"If-Match": "2"})
        self.ok("DELETE", "/api/publications/" + pub["id"], status=204)
        self.err("POST", pub["path"], {"input": {}}, "NOT_FOUND", 404)
    def test_unknown_parameter_rejected(self):
        p = self.provider()
        value = self.execute(p, {"rogue": 1}, status=400)
        self.assertEqual("UNKNOWN_PARAMETER", value["error"]["code"])
    def test_required_parameter_type_validation(self):
        p = self.provider(parameters=[{"name": "n", "type": "INTEGER", "source": "INPUT", "required": True}])
        self.assertEqual("REQUIRED_PARAMETER", self.execute(p, status=400)["error"]["code"])
        self.assertEqual("PARAMETER_TYPE", self.execute(p, {"n": "bad"}, status=400)["error"]["code"])
    def test_request_unknown_top_level_rejected(self):
        p = self.provider()
        self.err("POST", "/api/providers/" + p["id"] + "/execute", {"tenant": "other"}, "INVALID_INPUT")
    def test_page_bound_enforced(self):
        self.assertEqual("INVALID_INPUT", self.execute(self.provider(), page={"size": 501}, status=400)["error"]["code"])
    def test_output_root_before_mapping(self):
        p = self.provider(config={"method": "GET", "path": "/customers"}, fieldsRoot="/data/items", outputMappings=[{"name": "customer", "pointer": "/name", "type": "STRING"}])
        self.assertEqual({"customer": "Nordwerk Manufacturing"}, self.execute(p)["data"][0])
    def test_read_retries_recover(self):
        p = self.provider(config={"method": "GET", "path": "/flaky"}, maxAttempts=3)
        result = self.execute(p); self.assertTrue(result["data"]["recovered"]); self.assertGreaterEqual(result["attempts"], 1)
    def test_write_requires_idempotency(self):
        p = self.provider(operation="CREATE", config={"method": "POST", "path": "/echo"})
        self.assertEqual("IDEMPOTENCY_REQUIRED", self.execute(p, status=400)["error"]["code"])
    def test_idempotency_replay_and_payload_conflict(self):
        p = self.provider(operation="CREATE", parameters=[{"name": "v", "type": "STRING"}], config={"method": "POST", "path": "/echo", "body": {"v": "${v}"}})
        first = self.execute(p, {"v": "one"}, "same-key-001")
        again = self.execute(p, {"v": "one"}, "same-key-001")
        self.assertEqual(first["executionId"], again["executionId"]); self.assertTrue(again["replayed"])
        self.assertEqual("IDEMPOTENCY_CONFLICT", self.execute(p, {"v": "two"}, "same-key-001", status=409)["error"]["code"])
    def test_concurrent_duplicate_does_not_repeat_write(self):
        p = self.provider(operation="CREATE", config={"method": "POST", "path": "/slow"})
        path = "/api/providers/" + p["id"] + "/execute"
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda _: self.api("POST", path, {"input": {}}, headers={"Idempotency-Key": "concurrent-001"}), range(2)))
        self.assertTrue(any(r[0] == 200 for r in results))
        self.assertTrue(all(r[0] in (200, 409) for r in results))
        successes = [r[1]["executionId"] for r in results if r[0] == 200]
        self.assertEqual(1, len(set(successes)))
    def test_ambiguous_write_never_replayed_and_can_reconcile(self):
        p = self.provider(operation="CREATE", config={"method": "POST", "path": "/write-ambiguous"}, maxAttempts=4)
        self.assertEqual("UPSTREAM_UNAVAILABLE", self.execute(p, key="ambiguous-001", status=502)["error"]["code"])
        self.assertEqual("EXECUTION_UNKNOWN", self.execute(p, key="ambiguous-001", status=409)["error"]["code"])
        counter = self.provider(config={"method": "GET", "path": "/write-count"})
        self.assertEqual(1, self.execute(counter)["data"]["count"])
        entry = next(e for e in self.ok("GET", "/api/idempotency") if e["providerId"] == p["id"])
        self.assertEqual("UNKNOWN", entry["status"])
        self.ok("POST", "/api/idempotency/" + entry["id"] + "/resolve", {"decision": "CONFIRMED_SUCCESS", "result": {"verified": True}}, status=204)
        self.assertTrue(self.execute(p, key="ambiguous-001")["replayed"])
    def test_persistent_async_job_completes(self):
        p = self.provider()
        job = self.execute(p, key="job-test-001", status=202, **{"async": True})
        for _ in range(60):
            job = self.ok("GET", "/api/jobs/" + job["id"])
            if job["status"] not in ("QUEUED", "RUNNING"): break
            time.sleep(0.1)
        self.assertEqual("SUCCEEDED", job["status"])
        self.assertNotIn("idempotencyKey", job)
    def test_async_same_key_deduplicates_job(self):
        p = self.provider()
        a = self.execute(p, key="job-dedupe-001", status=202, **{"async": True})
        b = self.execute(p, key="job-dedupe-001", status=202, **{"async": True})
        self.assertEqual(a["id"], b["id"])
    def test_rate_limit_actual_http(self):
        p = self.provider(rateLimit=1)
        self.execute(p)
        self.assertEqual("RATE_LIMIT", self.execute(p, status=429)["error"]["code"])
    def test_query_parameters_are_encoded(self):
        p = self.provider(config={"method": "GET", "path": "/echo", "query": {"q": "${q}"}}, parameters=[{"name": "q", "type": "STRING"}])
        self.assertEqual("q=a%26admin%3Dtrue", self.execute(p, {"q": "a&admin=true"})["data"]["query"])
    def test_header_injection_rejected(self):
        p = self.provider(config={"method": "GET", "path": "/echo", "headers": {"X-Test": "bad\r\nInjected: yes"}})
        self.assertEqual("HEADER_BLOCKED", self.execute(p, status=400)["error"]["code"])
    def test_reserved_header_rejected(self):
        p = self.provider(config={"method": "GET", "path": "/echo", "headers": {"Host": "evil.invalid"}})
        self.assertEqual("HEADER_BLOCKED", self.execute(p, status=400)["error"]["code"])
    def test_redirect_is_not_followed(self):
        p = self.provider(config={"method": "GET", "path": "/redirect"})
        self.assertEqual("UPSTREAM_REDIRECT", self.execute(p, status=502)["error"]["code"])
    def test_timeout_is_bounded(self):
        p = self.provider(config={"method": "GET", "path": "/slow"}, timeoutMs=100)
        self.assertEqual("UPSTREAM_TIMEOUT", self.execute(p, status=504)["error"]["code"])
    def test_invalid_upstream_json_is_sanitized(self):
        p = self.provider(config={"method": "GET", "path": "/invalid-json"})
        self.assertEqual("UPSTREAM_FORMAT", self.execute(p, status=502)["error"]["code"])
    def test_oversized_upstream_is_bounded(self):
        p = self.provider(config={"method": "GET", "path": "/big", "responseType": "TEXT"})
        self.assertEqual("UPSTREAM_TOO_LARGE", self.execute(p, status=502)["error"]["code"])
    def test_binary_download_is_base64(self):
        p = self.provider(config={"method": "GET", "path": "/download", "responseType": "BINARY"})
        self.assertEqual(b"id,name\n1,Simulation\n", base64.b64decode(self.execute(p)["data"]["contentBase64"]))
    def test_graphql_success(self):
        p = self.provider("GRAPHQL", config={"path": "/graphql", "document": "{ workOrders { id } }"}, fieldsRoot="/data/data/workOrders")
        self.assertEqual(2, len(self.execute(p, key="graphql-read-001")["data"]))
    def test_graphql_error_envelope(self):
        p = self.provider("GRAPHQL", config={"path": "/graphql-errors", "document": "{ workOrders { id } }"})
        self.assertEqual("GRAPHQL_ERROR", self.execute(p, key="graphql-error-001", status=502)["error"]["code"])
    def test_odata_injects_paging(self):
        p = self.provider("ODATA", config={"method": "GET", "path": "/echo"})
        query = self.execute(p, page={"size": 5, "index": 2})["data"]["query"]
        self.assertIn("%24top=5", query); self.assertIn("%24skip=10", query)
    def test_soap_calculation(self):
        p = next(v for v in self.providers.values() if v["connectionId"] == self.connections["SOAP"]["id"])
        result = self.execute(p, {"a": 3, "b": 4}, "soap-calc-001")
        self.assertIn("7", json.dumps(result["data"]))
    def test_soap_500_fault_detected(self):
        p = self.provider("SOAP", config={"path": "/soap-fault-500", "envelope": "<r/>", "responseType": "XML"})
        self.assertEqual("SOAP_FAULT", self.execute(p, key="soap-fault-001", status=502)["error"]["code"])
    def test_soap_external_entities_rejected_before_send(self):
        p = self.provider("SOAP", config={"path": "/soap", "envelope": "<!DOCTYPE x SYSTEM 'file:///etc/passwd'><x/>"})
        self.assertEqual("XML_INVALID", self.execute(p, key="soap-xxe-001", status=400)["error"]["code"])
    def test_openapi_has_caller_inputs_not_constants(self):
        p = self.provider(parameters=[{"name": "secret", "type": "STRING", "source": "CONSTANT", "value": "private-constant"}, {"name": "input", "type": "STRING", "source": "INPUT"}])
        pub = self.ok("POST", "/api/providers/" + p["id"] + "/publish", {"version": 1}, status=201)
        doc = self.ok("GET", "/api/openapi")
        self.assertEqual("3.1.1", doc["openapi"])
        self.assertNotIn("private-constant", json.dumps(doc["paths"][pub["path"]]))
        props = doc["paths"][pub["path"]]["post"]["requestBody"]["content"]["application/json"]["schema"]["properties"]["input"]["properties"]
        self.assertEqual({"input"}, set(props))
    def test_openapi_import_creates_reviewable_draft(self):
        ps = self.ok("POST", "/api/import/openapi", {"connectionId": self.connections["REST"]["id"], "document": {"openapi": "3.1.0", "paths": {"/echo": {"get": {"summary": "Imported echo", "operationId": "echo", "parameters": [{"name": "q", "in": "query", "schema": {"type": "string"}}]}}}}}, status=201)
        self.assertEqual(1, len(ps)); self.assertEqual("q=x", self.execute(ps[0], {"q": "x"})["data"]["query"])
    def test_openapi_remote_reference_rejected(self):
        self.err("POST", "/api/import/openapi", {"connectionId": self.connections["REST"]["id"], "document": {"openapi": "3.1.0", "paths": {"/x": {"get": {"parameters": [{"$ref": "https://evil.invalid/schema"}]}}}}}, "OPENAPI_REFERENCE")
    def test_designer_cannot_change_credentials(self):
        key = self.key("DESIGNER")
        self.err("POST", "/api/connections", {"name": "No", "type": "LOCAL", "config": {}}, "FORBIDDEN", 403, token=key["token"])
    def test_auditor_cannot_execute(self):
        key = self.key("AUDITOR")
        pub = self.publications[0]
        self.err("POST", pub["path"], {"input": {}}, "FORBIDDEN", 403, token=key["token"])
    def test_invoker_scope_is_enforced(self):
        pub = next(p for p in self.publications if p["slug"] == "customer-directory")
        key = self.key("INVOKER", providers=[pub["providerId"]])
        self.ok("POST", pub["path"], {"input": {}}, token=key["token"])
        other = next(p for p in self.publications if p["providerId"] != pub["providerId"])
        self.err("POST", other["path"], {"input": {}}, "FORBIDDEN", 403, token=key["token"])
        self.assertEqual({pub["path"]}, set(self.ok("GET", "/api/openapi", token=key["token"])["paths"]))
    def test_invoker_cannot_read_design(self):
        key = self.key("INVOKER", allProviders=True)
        self.err("GET", "/api/providers", None, "FORBIDDEN", 403, token=key["token"])
    def test_cross_tenant_objects_are_not_found(self):
        key = self.key("ADMIN", tenant="other-tenant")
        self.assertEqual([], self.ok("GET", "/api/connections", token=key["token"]))
        self.err("GET", "/api/connections/" + self.connections["LOCAL"]["id"], None, "NOT_FOUND", 404, token=key["token"])
        self.err("POST", self.publications[0]["path"], {"input": {}}, "NOT_FOUND", 404, token=key["token"])
    def test_normal_tenant_admin_cannot_create_other_tenant(self):
        key = self.key("ADMIN", tenant="tenant-admin-test")
        self.err("POST", "/api/keys", {"role": "ADMIN", "tenant": "other"}, "FORBIDDEN", 403, token=key["token"])
    def test_key_requires_scope(self):
        self.err("POST", "/api/keys", {"role": "INVOKER"}, "SCOPE_REQUIRED")
    def test_revoked_token_cannot_authenticate(self):
        key = self.key("AUDITOR")
        self.ok("DELETE", "/api/keys/" + key["id"], status=204)
        self.err("GET", "/api/me", None, "UNAUTHORIZED", 401, token=key["token"])
    def test_api_key_hashes_never_returned(self):
        for key in self.ok("GET", "/api/keys"):
            self.assertNotIn("hash", key); self.assertNotIn("token", key)
    def test_self_revocation_is_protected(self):
        self.err("DELETE", "/api/keys/bootstrap", None, "KEY_SELF_REVOKE", 409)
    def test_configuration_sources_are_server_resolved(self):
        self.ok("PUT", "/api/settings", {"region": "Germany"}, status=204)
        p = self.provider(parameters=[{"name": "region", "source": "CONFIG", "type": "STRING", "value": "region"}], config={"method": "GET", "path": "/echo", "query": {"region": "${region}"}})
        self.assertEqual("region=Germany", self.execute(p)["data"]["query"])
        self.assertEqual("UNKNOWN_PARAMETER", self.execute(p, {"region": "Override"}, status=400)["error"]["code"])
    def test_export_omits_connection_secrets(self):
        data = self.ok("GET", "/api/export")
        self.assertFalse(data["secretsIncluded"])
        self.assertNotIn("not-to-be-returned", json.dumps(data))
    def test_audit_is_metadata_only(self):
        data = self.ok("GET", "/api/audit")
        self.assertTrue(data)
        for entry in data:
            self.assertNotIn("input", entry); self.assertNotIn("result", entry); self.assertNotIn("token", entry)
    def test_prune_preserves_unknown_entries(self):
        self.assertTrue(self.ok("POST", "/api/maintenance/prune", {})["unknownEntriesRetained"])
    def test_z_restart_retains_published_service_and_idempotency(self):
        p = self.provider(operation="CREATE", config={"method": "POST", "path": "/echo"})
        pub = self.ok("POST", "/api/providers/" + p["id"] + "/publish", {"version": 1}, status=201)
        before = self.ok("POST", pub["path"], {"input": {}}, headers={"Idempotency-Key": "persistent-001"})
        type(self).stop(); type(self).start()
        after = self.ok("POST", pub["path"], {"input": {}}, headers={"Idempotency-Key": "persistent-001"})
        self.assertEqual(before["executionId"], after["executionId"]); self.assertTrue(after["replayed"])

# Each method and body mode exercises a different transport branch, not repeated no-op assertions.
def verb_test(verb):
    def test(self):
        safe = verb in ("GET", "HEAD")
        p = self.provider(operation="READ" if safe else "EXECUTE", config={"method": verb, "path": "/echo"})
        result = self.execute(p, key=None if safe else "method-" + verb)
        if verb == "HEAD": self.assertIsNone(result["data"])
        else: self.assertEqual(verb, result["data"]["method"])
    return test
for verb in ("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"):
    setattr(HubHTTP, "test_http_method_" + verb.lower(), verb_test(verb))
def mode_test(mode):
    def test(self):
        body = "literal text" if mode == "TEXT" else {"message": "hello & world"}
        p = self.provider(operation="CREATE", config={"method": "POST", "path": "/echo", "bodyMode": mode, "body": body})
        text = self.execute(p, key="body-" + mode)["data"]["body"]
        if mode == "JSON": self.assertEqual(body, json.loads(text))
        elif mode == "FORM": self.assertEqual({"message": ["hello & world"]}, urllib.parse.parse_qs(text))
        else: self.assertIn("literal text" if mode == "TEXT" else "hello & world", text)
    return test
for mode in ("JSON", "FORM", "TEXT", "MULTIPART"):
    setattr(HubHTTP, "test_body_mode_" + mode.lower(), mode_test(mode))
def auth_test(auth):
    def test(self):
        secrets = {"username": "demo", "password": "test"} if auth == "BASIC" else {"token": "fixture-secret"} if auth == "BEARER" else {"apiKey": "fixture-secret"}
        c = self.ok("POST", "/api/connections", {"name": "Auth " + auth, "type": "REST", "config": {"baseUrl": f"http://127.0.0.1:{self.upstream}", "authType": auth, "apiKeyHeader": "X-Demo-Key"}, "secrets": secrets}, status=201)
        path = "/auth/" + ("key" if auth == "API_KEY" else auth.lower())
        p = self.provider(connectionId=c["id"], config={"method": "GET", "path": path})
        self.assertTrue(self.execute(p)["data"]["accepted"])
    return test
for auth in ("BASIC", "BEARER", "API_KEY"):
    setattr(HubHTTP, "test_upstream_auth_" + auth.lower(), auth_test(auth))

class XMLResult(unittest.TextTestResult):
    def startTest(self, test):
        self.started = time.monotonic(); super().startTest(test)
    def stopTest(self, test):
        elapsed = time.monotonic() - self.started
        testcase = ET.SubElement(self.xml, "testcase", classname=type(test).__name__, name=test._testMethodName, time=f"{elapsed:.4f}")
        for case, detail in self.failures + self.errors:
            if case == test: ET.SubElement(testcase, "failure", message="Test failed").text = detail
        super().stopTest(test)
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs); self.xml = ET.Element("testsuite", name="http-contracts")

if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(HubHTTP)
    result = unittest.TextTestRunner(verbosity=2, resultclass=XMLResult).run(suite)
    result.xml.set("tests", str(result.testsRun)); result.xml.set("failures", str(len(result.failures))); result.xml.set("errors", str(len(result.errors)))
    report = ROOT / "build/reports/http.xml"; report.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(result.xml).write(report, encoding="utf-8", xml_declaration=True)
    raise SystemExit(0 if result.wasSuccessful() else 1)
