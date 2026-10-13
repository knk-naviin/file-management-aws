$ErrorActionPreference = "Stop"

$lambda = Join-Path (Split-Path -Parent $PSScriptRoot) "lambda"
$build = Join-Path $lambda "build"
$staging = Join-Path $build "package"
$zip = Join-Path $build "secure-docs-lambdas.zip"

Push-Location $lambda
try {
    npm ci --omit=dev
    if ($LASTEXITCODE -ne 0) { throw "Lambda dependency installation failed" }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Path $staging -Force | Out-Null
Copy-Item -Path (Join-Path $lambda "*.js") -Destination $staging -Force
Copy-Item -Path (Join-Path $lambda "package.json") -Destination $staging -Force
Copy-Item -Path (Join-Path $lambda "node_modules") -Destination $staging -Recurse -Force
if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $zip -CompressionLevel Optimal
Write-Output "Built $zip"
