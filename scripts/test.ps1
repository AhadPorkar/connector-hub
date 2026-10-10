$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& "$PSScriptRoot/build.ps1"
New-Item -ItemType Directory -Force 'build/test-classes' | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
$sources = Get-ChildItem 'hub-core/src/test/java','hub-connectors/src/test/java','hub-server/src/test/java' -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
[IO.File]::WriteAllLines((Join-Path (Get-Location) 'build/test-sources.txt'), [string[]]$sources, $utf8)
& javac --release 21 -encoding UTF-8 -cp build/classes -d build/test-classes '@build/test-sources.txt'
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
foreach ($suite in @('core.CoreTest','connectors.ConnectorTest','server.ServerTest')) {
  & java -cp 'build/classes;build/test-classes' "io.ahad.connectorhub.$suite"
  if ($LASTEXITCODE -ne 0) { throw "Failed suite: $suite" }
}
& python tests/http_test.py
if ($LASTEXITCODE -ne 0) { throw 'HTTP tests failed.' }
& python scripts/check_repository.py
if ($LASTEXITCODE -ne 0) { throw 'Repository checks failed.' }
if (Get-Command node -ErrorAction SilentlyContinue) { & node --check hub-server/src/main/resources/public/app.js; if ($LASTEXITCODE -ne 0) { throw 'JavaScript syntax check failed.' } }
