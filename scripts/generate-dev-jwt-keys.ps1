$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$keyDirectory = Join-Path $root ".local"
$privateKeyFile = Join-Path $keyDirectory "jwt-private.der"
$publicKeyFile = Join-Path $keyDirectory "jwt-public.der"
$envFile = Join-Path $root ".env"

if (-not (Get-Command openssl.exe -ErrorAction SilentlyContinue)) {
    throw "OpenSSL is required. Install OpenSSL, then rerun this script."
}

New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
& openssl.exe genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -outform DER -out $privateKeyFile
if ($LASTEXITCODE -ne 0) { throw "RSA private-key generation failed" }
& openssl.exe pkey -inform DER -in $privateKeyFile -pubout -outform DER -out $publicKeyFile
if ($LASTEXITCODE -ne 0) { throw "RSA public-key export failed" }

$privateKey = [Convert]::ToBase64String([IO.File]::ReadAllBytes($privateKeyFile))
$publicKey = [Convert]::ToBase64String([IO.File]::ReadAllBytes($publicKeyFile))
$existing = if (Test-Path $envFile) { Get-Content $envFile } else { @() }
$preserved = $existing | Where-Object {
    $_ -notmatch '^\s*JWT_(PRIVATE|PUBLIC)_KEY_BASE64\s*='
}
@($preserved) + @(
    "JWT_PRIVATE_KEY_BASE64=$privateKey"
    "JWT_PUBLIC_KEY_BASE64=$publicKey"
) | Set-Content -Path $envFile -Encoding ascii

Write-Output "Generated development-only RSA keys and updated .env. Do not use these keys in production."
