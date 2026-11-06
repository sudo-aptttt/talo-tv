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
    Read-Host " Appuyez sur Entrée pour quitter..."
    exit
}

Write-Host " [OK] ADB prêt : $adbPath" -ForegroundColor Green
Write-Host ""

# Nettoyer les anciennes connexions fantômes
& $adbPath disconnect | Out-Null

# 2. Détection ou Connexion
$targetDevice = ""

# Vérifier d'abord si un appareil est déjà branché en USB
$usbDev = & $adbPath devices | Select-String -Pattern "\bdevice$" | Select-Object -First 1
if ($usbDev) {
    $targetDevice = ($usbDev.Line -split "\s+")[0]
    Write-Host " [OK] Appareil USB détecté : $targetDevice" -ForegroundColor Green
} else {
    Write-Host " --- Connexion à votre Google TV ---" -ForegroundColor Cyan
    Write-Host " Sur votre TV dans : Paramètres > Système > Options pour les développeurs > Débogage sans fil" -ForegroundColor Gray
    Write-Host ""
    $tvInput = Read-Host " Entrez l'adresse IP et le port affichés sur la TV (ex: 192.168.1.153:34201)"
    $tvInput = $tvInput.Trim()

    if ([string]::IsNullOrWhiteSpace($tvInput)) {
        Write-Host " [ERREUR] Aucune adresse entrée." -ForegroundColor Red
        Read-Host " Appuyez sur Entrée pour quitter..."
        exit
    }

    # Si l'utilisateur n'a pas mis de port, mettre le port standard 5555
    if (!($tvInput -like "*:*")) {
        $tvInput = "$tvInput`:5555"
    }

    $ipPart = $tvInput.Split(":")[0]

    Write-Host " [*] Tentative de connexion directe à $tvInput..." -ForegroundColor Yellow
    $connectResult = & $adbPath connect $tvInput

    # Si la connexion a échoué (besoin de jumelage / code)
    $devCheck = & $adbPath devices | Select-String -Pattern [regex]::Escape($tvInput)
    if (!($devCheck -like "*device*")) {
        Write-Host ""
        Write-Host " [!] La TV nécessite une première association avec un code." -ForegroundColor Yellow
        Write-Host "     1. Cliquez sur 'Associer un appareil avec un code' sur votre TV" -ForegroundColor White
        Write-Host "     2. Regardez la petite fenêtre qui vient de s'ouvrir" -ForegroundColor White
        Write-Host ""

        $pairPort = Read-Host " Entrez le numéro de port affiché dans cette fenêtre (les 5 chiffres à la fin)"
        $pairPort = $pairPort.Trim().Replace(":", "")
        $code = Read-Host " Entrez le code à 6 chiffres affiché"
        $code = $code.Trim()

        Write-Host ""
        Write-Host " [*] Association en cours..." -ForegroundColor Yellow
        & $adbPath pair "$ipPart`:$pairPort" $code

        Write-Host " [*] Reconnexion à la TV ($tvInput)..." -ForegroundColor Yellow
        & $adbPath connect $tvInput
    }

    $targetDevice = $tvInput
}

# Vérification finale de la connexion
$finalCheck = & $adbPath devices | Select-String -Pattern [regex]::Escape($targetDevice)
if (!($finalCheck -like "*device*")) {
    Write-Host ""
    Write-Host " [ERREUR] Impossible de se connecter à l'appareil ($targetDevice)." -ForegroundColor Red
    Write-Host " Vérifiez que le 'Débogage sans fil' est bien activé sur la TV et réessayez." -ForegroundColor Yellow
    Read-Host " Appuyez sur Entrée pour quitter..."
    exit
}

Write-Host " [OK] Connecté avec succès à $targetDevice !" -ForegroundColor Green
Write-Host ""

# 3. Vérification / Installation de Talo TV
Write-Host " [*] Vérification de l'application Talo TV sur la télévision..." -ForegroundColor Yellow
$packages = & $adbPath -s $targetDevice shell pm list packages com.talotv.launcher
if ($packages -like "*com.talotv.launcher*") {
    Write-Host " [OK] Talo TV est déjà présent sur votre TV." -ForegroundColor Green
} else {
    Write-Host " [*] Talo TV n'est pas encore installé. Téléchargement depuis GitHub..." -ForegroundColor Yellow
    $apkPath = "$env:TEMP\talo-tv.apk"
    Invoke-WebRequest -Uri "https://github.com/sudo-aptttt/talo-tv/releases/latest/download/talo-tv.apk" -OutFile $apkPath
    Write-Host " [*] Installation de Talo TV en cours..." -ForegroundColor Yellow
    & $adbPath -s $targetDevice install -r -d -g $apkPath
    Remove-Item $apkPath -Force -ErrorAction SilentlyContinue
    Write-Host " [OK] Talo TV a été installé avec succès." -ForegroundColor Green
}

# 4. Déblocage et activation
Write-Host ""
Write-Host " [*] 1/3 Déblocage de la sécurité Android 14 (Paramètres restreints)..." -ForegroundColor Yellow
& $adbPath -s $targetDevice shell cmd appops set com.talotv.launcher ACCESS_RESTRICTED_SETTINGS allow

Write-Host " [*] 2/3 Activation du service d'accessibilité (Bouton Accueil)..." -ForegroundColor Yellow
& $adbPath -s $targetDevice shell settings put secure enabled_accessibility_services com.talotv.launcher/com.talotv.launcher.TaloAccessibilityService
& $adbPath -s $targetDevice shell settings put secure accessibility_enabled 1

# Contrôle de confirmation
$activeServices = & $adbPath -s $targetDevice shell settings get secure enabled_accessibility_services
if ($activeServices -like "*TaloAccessibilityService*") {
    Write-Host " [OK] Service d'accessibilité activé et verrouillé !" -ForegroundColor Green
} else {
    Write-Host " [ATTENTION] Vérification du service d'accessibilité en cours..." -ForegroundColor Yellow
}

Write-Host " [*] 3/3 Lancement immédiat de Talo TV sur l'écran..." -ForegroundColor Yellow
& $adbPath -s $targetDevice shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -f 0x10000000 com.talotv.launcher/.MainActivity | Out-Null

Write-Host ""
Write-Host " ================================================================" -ForegroundColor Green
Write-Host "                 TALO TV EST ACTIVÉ AVEC SUCCÈS !                " -ForegroundColor White
Write-Host " ================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  - Sécurité Android 14 débloquée" -ForegroundColor White
Write-Host "  - Service d'accessibilité 100% actif" -ForegroundColor White
Write-Host "  - Touche Accueil (Home) redirigée vers Talo TV" -ForegroundColor White
Write-Host ""
Write-Host " Regardez votre téléviseur : Talo TV est prêt !" -ForegroundColor Cyan
Write-Host " Appuyez sur la touche Accueil (Home) de votre télécommande pour tester." -ForegroundColor Cyan
Write-Host ""
Read-Host " Appuyez sur Entrée pour fermer..."
