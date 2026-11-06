# =====================================================================
# Talo TV - Script d'installation et activation automatique Windows
# =====================================================================
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch {}
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
try { $Host.UI.RawUI.WindowTitle = "Talo TV - Activation Automatique Android 14" } catch {}

Write-Host ""
Write-Host " ================================================================" -ForegroundColor Cyan
Write-Host "             TALO TV - ACTIVATION ANDROID TV 14                  " -ForegroundColor White
Write-Host " ================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Vérification / Téléchargement automatique de ADB
$adbPath = $null
$systemAdb = Get-Command "adb" -ErrorAction SilentlyContinue
if ($systemAdb) {
    $adbPath = $systemAdb.Source
} elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") {
    $adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
} elseif (Test-Path "$env:LOCALAPPDATA\TaloTV\platform-tools\adb.exe") {
    $adbPath = "$env:LOCALAPPDATA\TaloTV\platform-tools\adb.exe"
} else {
    Write-Host " [*] Outil ADB non détecté. Téléchargement des outils officiels Google..." -ForegroundColor Yellow
    $toolsDir = "$env:LOCALAPPDATA\TaloTV"
    if (!(Test-Path $toolsDir)) { New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null }
    $zipPath = "$toolsDir\platform-tools.zip"
    
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
    $adbPath = "$toolsDir\platform-tools\adb.exe"
}

if (!(Test-Path $adbPath)) {
    Write-Host " [ERREUR] Impossible de préparer ADB. Vérifiez votre connexion internet." -ForegroundColor Red
    pause
    exit
}

Write-Host " [OK] ADB prêt : $adbPath" -ForegroundColor Green
Write-Host ""

# 2. Choix de la méthode de connexion
Write-Host " Choisissez votre méthode de connexion :" -ForegroundColor Cyan
Write-Host "  [1] Débogage sans fil (Android 11 / Android 14 avec code à 6 chiffres) [Recommandé]" -ForegroundColor White
Write-Host "  [2] Câble USB ou Débogage réseau standard (port 5555)" -ForegroundColor White
Write-Host ""
$choice = Read-Host " Votre choix (1 ou 2, défaut: 1)"
if ([string]::IsNullOrWhiteSpace($choice)) { $choice = "1" }

$targetDevice = ""

if ($choice -eq "1") {
    Write-Host ""
    Write-Host " --- Instructions sur votre Google TV ---" -ForegroundColor Yellow
    Write-Host " 1. Dans Paramètres > Système > Options pour les développeurs > Débogage sans fil"
    Write-Host " 2. Cliquez sur 'Associer un appareil avec un code d'association'"
    Write-Host " 3. Une fenêtre s'affiche avec un code et une adresse IP:PORT"
    Write-Host ""

    $pairHost = Read-Host " Entrez l'IP et PORT de jumelage de la petite fenêtre (ex: 192.168.1.153:42625)"
    $code = Read-Host " Entrez le code à 6 chiffres (ex: 126203)"

    Write-Host ""
    Write-Host " [*] Association avec la TV..." -ForegroundColor Yellow
    & $adbPath pair $pairHost $code

    Write-Host ""
    $connectPort = Read-Host " Entrez le port de connexion principal (affiché sous 'Débogage sans fil', ex: 34201)"
    $ipOnly = $pairHost.Split(":")[0]
    $connectHost = "$ipOnly`:$connectPort"

    Write-Host " [*] Connexion à la TV ($connectHost)..." -ForegroundColor Yellow
    & $adbPath connect $connectHost
    $targetDevice = $connectHost
} else {
    Write-Host ""
    $devices = & $adbPath devices | Select-String "device$"
    if ($devices) {
        Write-Host " [OK] Appareil USB détecté automatiquement !" -ForegroundColor Green
    } else {
        $ip = Read-Host " Entrez l'adresse IP de votre TV (ex: 192.168.1.50)"
        & $adbPath connect "$ip`:5555"
        $targetDevice = "$ip`:5555"
    }
}

Write-Host ""
Write-Host " [*] Vérification de l'état de l'appareil..." -ForegroundColor Yellow
Start-Sleep -Seconds 1

# 3. Vérification de la présence de Talo TV
$installed = & $adbPath shell pm list packages com.talotv.launcher
if (!($installed -like "*com.talotv.launcher*")) {
    Write-Host " [*] Talo TV n'est pas encore installé. Téléchargement de la dernière version..." -ForegroundColor Yellow
    $apkPath = "$env:TEMP\talo-tv.apk"
    Invoke-WebRequest -Uri "https://github.com/sudo-aptttt/talo-tv/releases/latest/download/talo-tv.apk" -OutFile $apkPath
    Write-Host " [*] Installation de Talo TV sur la télévision..." -ForegroundColor Yellow
    & $adbPath install -r -d -g $apkPath
    Remove-Item $apkPath -Force -ErrorAction SilentlyContinue
} else {
    Write-Host " [OK] Talo TV est déjà installé sur la télévision." -ForegroundColor Green
}

# 4. Déblocage de la sécurité Android 14 et activation de l'accessibilité
Write-Host " [*] Déblocage des paramètres restreints Android 14..." -ForegroundColor Yellow
& $adbPath shell cmd appops set com.talotv.launcher ACCESS_RESTRICTED_SETTINGS allow

Write-Host " [*] Activation du service d'accessibilité (Bouton Accueil)..." -ForegroundColor Yellow
& $adbPath shell settings put secure enabled_accessibility_services com.talotv.launcher/com.talotv.launcher.TaloAccessibilityService
& $adbPath shell settings put secure accessibility_enabled 1

Write-Host " [*] Lancement de Talo TV sur l'écran..." -ForegroundColor Yellow
& $adbPath shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -f 0x10000000 com.talotv.launcher/.MainActivity | Out-Null

Write-Host ""
Write-Host " ================================================================" -ForegroundColor Green
Write-Host "                 TALO TV EST ACTIVÉ AVEC SUCCÈS !                " -ForegroundColor White
Write-Host " ================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  - Sécurité Android 14 débloquée" -ForegroundColor White
Write-Host "  - Service d'accessibilité activé et verrouillé" -ForegroundColor White
Write-Host "  - Touche Accueil (Home) configurée pour Talo TV" -ForegroundColor White
Write-Host ""
Write-Host " Appuyez sur la touche Accueil de votre télécommande pour tester !" -ForegroundColor Cyan
Write-Host ""
