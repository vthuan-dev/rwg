# =============================================================================
# Build va deploy Backend Jar (rwg-user-app & rwg-admin-app) len VPS.
# =============================================================================
$ErrorActionPreference = "Stop"

$VPS    = "root@223.130.11.200"
$VPS_PW = "0904521297t"
$PLINK  = "C:\Program Files\PuTTY\plink.exe"
$PSCP   = "C:\Program Files\PuTTY\pscp.exe"
$BE     = "d:\Project\RWG\rwg-backend"

Write-Output "########## 1. BUILD BACKEND JAR ##########"
Push-Location $BE
try {
    cmd /c "mvn -am -pl rwg-user-app,rwg-admin-app package -DskipTests 2>&1" | Select-Object -Last 10 | ForEach-Object { "   $_" }
    if ($LASTEXITCODE -ne 0) { throw "mvn package that bai" }
} finally { Pop-Location }

Write-Output ""
Write-Output "########## 2. TAI JAR LEN VPS ##########"
$userJar  = "$BE\rwg-user-app\target\rwg-user-app-0.1.0-SNAPSHOT.jar"
$adminJar = "$BE\rwg-admin-app\target\rwg-admin-app-0.1.0-SNAPSHOT.jar"

if (-not (Test-Path $userJar))  { throw "Khong tim thay $userJar" }
if (-not (Test-Path $adminJar)) { throw "Khong tim thay $adminJar" }

Write-Output "   Tai rwg-user-app jar..."
& cmd /c "`"$PSCP`" -batch -pw $VPS_PW `"$userJar`" ${VPS}:/opt/rwg/bin/rwg-user-app-0.1.0-SNAPSHOT.jar.new 2>&1" | Select-Object -Last 1

Write-Output "   Tai rwg-admin-app jar..."
& cmd /c "`"$PSCP`" -batch -pw $VPS_PW `"$adminJar`" ${VPS}:/opt/rwg/bin/rwg-admin-app-0.1.0-SNAPSHOT.jar.new 2>&1" | Select-Object -Last 1

Write-Output ""
Write-Output "########## 3. DUNG SERVICE & THAY JAR TREN VPS ##########"
$remote = @'
set -eu
systemctl stop rwg-admin
systemctl stop rwg-user

mv /opt/rwg/bin/rwg-user-app-0.1.0-SNAPSHOT.jar.new /opt/rwg/bin/rwg-user-app-0.1.0-SNAPSHOT.jar
mv /opt/rwg/bin/rwg-admin-app-0.1.0-SNAPSHOT.jar.new /opt/rwg/bin/rwg-admin-app-0.1.0-SNAPSHOT.jar
chown -R rwg:rwg /opt/rwg/bin/

echo "   Khoi dong rwg-user (so huu Flyway schema)..."
systemctl start rwg-user

for i in $(seq 1 60); do
  if curl -fsS -o /dev/null http://127.0.0.1:9080/actuator/health 2>/dev/null; then
    echo "   rwg-user DANG CHAY sau ${i}s"
    break
  fi
  sleep 1
done

echo "   Khoi dong rwg-admin..."
systemctl start rwg-admin

for i in $(seq 1 60); do
  if curl -fsS -o /dev/null http://127.0.0.1:9081/actuator/health 2>/dev/null; then
    echo "   rwg-admin DANG CHAY sau ${i}s"
    break
  fi
  sleep 1
done

echo "   Deploy backend complete!"
'@

$remoteFile = "C:\Users\LENOVO\.gemini\antigravity-ide\brain\d2d7163f-9496-4706-a01e-605ead6f4f9f\scratch\deploy_be.sh"
(Set-Content -LiteralPath $remoteFile -Value ($remote -replace "`r`n","`n") -Encoding utf8 -NoNewline)
cmd /c "type `"$remoteFile`" | `"$PLINK`" -ssh -batch -pw $VPS_PW $VPS `"bash -s`" 2>&1"
