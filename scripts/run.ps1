$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Test-Path 'build/connector-hub.jar')) { & "$PSScriptRoot/build.ps1" }
& java -cp 'build/connector-hub.jar;drivers/*;plugins/*' io.ahad.connectorhub.server.Main @args
exit $LASTEXITCODE
