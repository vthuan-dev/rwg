# =============================================================================
# Build va deploy Backend Jar (rwg-user-app & rwg-admin-app) len VPS.
# =============================================================================
$ErrorActionPreference = "Stop"

$VPS    = "root@223.130.11.200"
$VPS_PW = "0904521297t"
$PLINK  = "C:\Program Files\PuTTY\plink.exe"
$PSCP   = "C:\Program Files\PuTTY\pscp.exe"
$BE     = "d:\Project\RWG\rwg-backend"

Write-Output "########## 1. DUNG TIEN TRINH JAVA DANG GIU KHOA TEP ##########"
# BAT BUOC, khong phai tuy chon. Tren Windows, `mvn spring-boot:run` giu khoa tep trong
# target/, va khi do `mvn package` bo qua buoc dong goi MA VAN bao BUILD SUCCESS. Jar cu
# se duoc tai len VPS, service khoi dong sach, health-check tra 200 - moi buoc bao thanh
# cong nhung ma nguon moi khong he co trong jar. Da mat mot vong deploy vi dieu nay.
$stopped = 0
foreach ($port in 8080, 8081) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        Write-Output "   Dung tien trinh o port $port (pid $($conn[0].OwningProcess))"
        Stop-Process -Id $conn[0].OwningProcess -Force -ErrorAction SilentlyContinue
        $stopped++
    }
}
if ($stopped -gt 0) {
    # Cho he dieu hanh nha khoa tep. Build ngay lap tuc van co the gap khoa con sot.
    Start-Sleep -Seconds 3
    Write-Output "   Da dung $stopped tien trinh. LUU Y: can chay lai dev server sau khi deploy."
} else {
    Write-Output "   Khong co tien trinh nao dang chay o 8080/8081"
}

Write-Output ""
Write-Output "########## 2. BUILD BACKEND JAR ##########"
Push-Location $BE
try {
    cmd /c "mvn -am -pl rwg-user-app,rwg-admin-app package -DskipTests 2>&1" | Select-Object -Last 10 | ForEach-Object { "   $_" }
    if ($LASTEXITCODE -ne 0) { throw "mvn package that bai" }
} finally { Pop-Location }

Write-Output ""
Write-Output "########## 3. KIEM NOI DUNG JAR ##########"
$userJar  = "$BE\rwg-user-app\target\rwg-user-app-0.1.0-SNAPSHOT.jar"
$adminJar = "$BE\rwg-admin-app\target\rwg-admin-app-0.1.0-SNAPSHOT.jar"

if (-not (Test-Path $userJar))  { throw "Khong tim thay $userJar" }
if (-not (Test-Path $adminJar)) { throw "Khong tim thay $adminJar" }

# KIEM DAU RA, khong chi tin vao ma tra ve cua build. Test-Path chi khang dinh tep TON
# TAI - jar cu tu sang cung ton tai. Script nay mo jar ra doi chieu class va migration.
$env:PYTHONIOENCODING = "utf-8"
python "$PSScriptRoot\verify_jar_contents.py" $userJar $adminJar | ForEach-Object { "   $_" }
if ($LASTEXITCODE -ne 0) { throw "Jar thieu noi dung moi - DUNG deploy" }

Write-Output ""
Write-Output "########## 4. TAI JAR LEN VPS ##########"

Write-Output "   Tai rwg-user-app jar..."
& cmd /c "`"$PSCP`" -batch -pw $VPS_PW `"$userJar`" ${VPS}:/opt/rwg/bin/rwg-user-app-0.1.0-SNAPSHOT.jar.new 2>&1" | Select-Object -Last 1

Write-Output "   Tai rwg-admin-app jar..."
& cmd /c "`"$PSCP`" -batch -pw $VPS_PW `"$adminJar`" ${VPS}:/opt/rwg/bin/rwg-admin-app-0.1.0-SNAPSHOT.jar.new 2>&1" | Select-Object -Last 1

Write-Output ""
Write-Output "########## 5. DUNG SERVICE & THAY JAR TREN VPS ##########"
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

# Tep tam dat TRONG DU AN, khong phai thu muc lam viec cua mot phien agent: duong dan
# kieu ...\brain\<id>\scratch chi ton tai trong phien do, nen script se hong khi chay lai
# sau nay ma khong ai hieu vi sao.
$remoteFile = "d:\Project\RWG\.deploy-be.sh"

# GHI KHONG BOM. `Set-Content -Encoding utf8` cua PowerShell 5 them BOM vao dau tep, va
# bash doc BOM nhu mot phan cua lenh dau tien -> "bash: line 1: ?set: command not found".
# Hau qua that su khong phai dong bao loi do, ma la `set -eu` KHONG CHAY: script mat che
# do dung-khi-loi, nen mot buoc that bai giua duong se am tham chay tiep va thay jar khi
# service chua kip dung.
[System.IO.File]::WriteAllText($remoteFile, ($remote -replace "`r`n", "`n"))
cmd /c "type `"$remoteFile`" | `"$PLINK`" -ssh -batch -pw $VPS_PW $VPS `"bash -s`" 2>&1"
Remove-Item $remoteFile -Force
