# =====================================================================
#  Baby Cry Translator - build automatica dell'APK
#  Crea un repo GitHub, carica il progetto, compila l'APK su GitHub
#  Actions e lo scarica sul tuo PC. Non serve installare Flutter.
# =====================================================================

$ErrorActionPreference = "Stop"
$repoName = "baby-cry-translator"
$root = $PSScriptRoot
Set-Location $root

function Say($msg, $color = "Cyan") { Write-Host "`n>> $msg" -ForegroundColor $color }
function Fail($msg) { Write-Host "`nERRORE: $msg" -ForegroundColor Red; Read-Host "Premi INVIO per chiudere"; exit 1 }

Write-Host "==========================================================" -ForegroundColor Magenta
Write-Host "   Baby Cry Translator - compilazione APK automatica" -ForegroundColor Magenta
Write-Host "==========================================================" -ForegroundColor Magenta

# --- 1. Git installato? -----------------------------------------------
Say "Controllo Git..."
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Say "Git non trovato, provo a installarlo con winget..." "Yellow"
    winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("Path","User")
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Fail "Git non disponibile. Installalo da https://git-scm.com e rilancia lo script."
    }
}
Write-Host "   Git OK" -ForegroundColor Green

# --- 2. GitHub CLI installato? ----------------------------------------
Say "Controllo GitHub CLI (gh)..."
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Say "GitHub CLI non trovato, provo a installarlo con winget..." "Yellow"
    winget install --id GitHub.cli -e --source winget --accept-package-agreements --accept-source-agreements
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("Path","User")
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Fail "GitHub CLI non disponibile. Installala da https://cli.github.com e rilancia lo script."
    }
}
Write-Host "   GitHub CLI OK" -ForegroundColor Green

# --- 3. Autenticazione GitHub -----------------------------------------
Say "Controllo l'accesso a GitHub..."
gh auth status 2>$null
if ($LASTEXITCODE -ne 0) {
    Say "Devi accedere a GitHub. Si aprira' il browser (scegli HTTPS quando chiesto)." "Yellow"
    gh auth login --web --git-protocol https
    if ($LASTEXITCODE -ne 0) { Fail "Accesso a GitHub non riuscito." }
}
$user = (gh api user --jq ".login")
Write-Host "   Accesso come: $user" -ForegroundColor Green

# --- 4. Repository git locale -----------------------------------------
Say "Preparo il repository locale..."
if (-not (Test-Path "$root\.git")) { git init -b main | Out-Null }
# .gitignore esiste gia' e esclude lo zip del dataset e le cartelle build
git add -A
git -c user.email="$user@users.noreply.github.com" -c user.name="$user" commit -m "Baby Cry Translator" --quiet 2>$null
if ($LASTEXITCODE -ne 0) { git commit -m "Aggiornamento" --quiet 2>$null }

# --- 5. Crea/collega il repo su GitHub --------------------------------
$full = "$user/$repoName"
$exists = $false
gh repo view $full 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { $exists = $true }

if ($exists) {
    Say "Il repo $full esiste gia', aggiorno..."
    if (-not (git remote 2>$null | Select-String "origin")) {
        git remote add origin "https://github.com/$full.git"
    }
    git push -u origin main --force
} else {
    Say "Creo il repo privato $full e carico il progetto..."
    gh repo create $repoName --private --source=. --remote=origin --push
    if ($LASTEXITCODE -ne 0) { Fail "Creazione del repo non riuscita." }
}

# --- 6. Avvia la build (workflow) -------------------------------------
Say "Avvio la compilazione su GitHub Actions..."
Start-Sleep -Seconds 5
gh workflow run build-apk.yml 2>$null   # in caso il push non l'abbia gia' avviata
Start-Sleep -Seconds 8

$runId = gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq ".[0].databaseId"
if (-not $runId) { Fail "Non trovo la build avviata. Controlla la tab Actions su https://github.com/$full/actions" }

Say "Build #$runId in corso. Attendo il completamento (circa 8-12 minuti)..." "Yellow"
Write-Host "   (puoi seguirla anche qui: https://github.com/$full/actions)" -ForegroundColor DarkGray
gh run watch $runId --exit-status
$buildOk = ($LASTEXITCODE -eq 0)

# --- 7. Scarica l'APK -------------------------------------------------
$outDir = Join-Path $root "APK"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Say "Scarico l'APK..."
gh run download $runId -n baby-cry-translator-apk -D $outDir
if ($LASTEXITCODE -ne 0) {
    if ($buildOk) { Fail "Build ok ma download fallito. Scarica l'artifact da https://github.com/$full/actions/runs/$runId" }
    else { Fail "La build e' fallita. Apri https://github.com/$full/actions/runs/$runId per vedere il log." }
}

$apk = Get-ChildItem -Path $outDir -Filter "*.apk" -Recurse | Select-Object -First 1
if (-not $apk) { Fail "APK non trovato nella cartella scaricata." }

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "   FATTO! APK pronto:" -ForegroundColor Green
Write-Host "   $($apk.FullName)" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "`nPer installarlo: copia il file .apk sul telefono Android,"
Write-Host "aprilo e consenti l'installazione da 'origini sconosciute'."
Start-Process explorer.exe "/select,`"$($apk.FullName)`""
Read-Host "`nPremi INVIO per chiudere"
