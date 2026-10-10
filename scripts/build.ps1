$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'JDK 21 or newer is required.' }
if (Test-Path 'build/classes') { Remove-Item 'build/classes' -Recurse -Force }
New-Item -ItemType Directory -Force 'build/classes','build/reports' | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
$sources = Get-ChildItem 'hub-core/src/main/java','hub-connectors/src/main/java','hub-server/src/main/java' -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
[IO.File]::WriteAllLines((Join-Path (Get-Location) 'build/main-sources.txt'), [string[]]$sources, $utf8)
& javac --release 21 -encoding UTF-8 -d build/classes '@build/main-sources.txt'
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
Copy-Item 'hub-server/src/main/resources/*' 'build/classes/' -Recurse -Force
New-Item -ItemType Directory -Force 'build/classes/public/docs' | Out-Null
foreach ($lang in @('en','de','fa')) { if (Test-Path "docs/$lang") { Copy-Item "docs/$lang" 'build/classes/public/docs/' -Recurse -Force } }
& jar --create --file build/connector-hub.jar --main-class io.ahad.connectorhub.server.Main -C build/classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Write-Host 'Built build/connector-hub.jar'
