# =============================================================================
# RWG Backend - kiểm tra môi trường dev (Windows / PowerShell 7)
# Cách chạy:  pwsh scripts/dev-check.ps1
# Kiểm tra: Java 21, Maven, service MySQL, cổng 3306 (MySQL) và 6379 (Redis).
# KHÔNG cài đặt gì - chỉ kiểm tra và báo trạng thái.
# =============================================================================
$ErrorActionPreference = 'Continue'
$failed = 0

function Write-Check($label, $ok, $detail) {
    $icon = if ($ok) { '[OK] ' } else { '[FAIL]' }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host "$icon $label" -ForegroundColor $color
    if ($detail) { Write-Host "       $detail" }
    if (-not $ok) { $script:failed++ }
}

function Test-Port([int]$Port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $iar = $c.BeginConnect('127.0.0.1', $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(800, $false) -and $c.Connected
        $c.Close()
        return $ok
    } catch { return $false }
}

Write-Host '===== RWG dev-check =====' -ForegroundColor Cyan

# --- Java ---
$javaOk = $false; $javaDetail = 'Không tìm thấy java trong PATH'
try {
    $v = & java -version 2>&1 | Select-Object -First 1
    if ($v -match '"21') { $javaOk = $true; $javaDetail = "$v" }
    else { $javaDetail = "Cần Java 21, hiện có: $v" }
} catch {}
Write-Check 'Java 21' $javaOk $javaDetail

# --- Maven ---
$mvnOk = $false; $mvnDetail = 'Không tìm thấy mvn trong PATH'
try {
    # KHÔNG pipe dòng mvn — pipe làm hỏng $LASTEXITCODE (biến nó thành exit code
    # của lệnh cuối pipe, vd Select-Object, luôn = 0). Chạy trực tiếp để
    # $LASTEXITCODE phản ánh đúng kết quả của mvn.
    & mvn -version *> $null
    if ($LASTEXITCODE -eq 0) {
        $mvnOk = $true
        $mvnDetail = "$(& mvn -version 2>&1 | Select-Object -First 1)"
    }
} catch {}
Write-Check 'Maven' $mvnOk $mvnDetail

# --- MySQL service ---
$svcOk = $false; $svcDetail = 'Không tìm thấy service MySQL nào'
try {
    $svc = Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue |
           Select-Object -First 1
    if ($svc) {
        $svcDetail = "$($svc.Name) - $($svc.Status)"
        $svcOk = ($svc.Status -eq 'Running')
        if (-not $svcOk) { $svcDetail += " (chưa chạy: Start-Service $($svc.Name))" }
    }
} catch {}
Write-Check 'Service MySQL' $svcOk $svcDetail

# --- Cổng 3306 (MySQL) ---
$mysqlPortOk = Test-Port 3306
Write-Check 'Cổng 3306 (MySQL)' $mysqlPortOk `
    ($(if ($mysqlPortOk) { 'localhost:3306 đang mở' } else { 'Không kết nối được localhost:3306 - xem docs/DEV-SETUP.md' }))

# --- mysql client (chỉ cảnh báo, không bắt buộc) + kiểm tra major version ---
$mysqlCli = Get-Command mysql -ErrorAction SilentlyContinue
if ($mysqlCli) {
    $ver = & mysql --version 2>&1
    Write-Host '[INFO] mysql client: ' $ver
    # WARN nếu MySQL major khác 8.x (dự án chuẩn hóa MySQL 8.4 LTS - DECISIONS.md).
    # Chỉ cảnh báo, KHÔNG tính là FAIL (client có thể khác server).
    if ("$ver" -match '(\d+)\.(\d+)') {
        $mysqlMajor = [int]$Matches[1]
        if ($mysqlMajor -ne 8) {
            Write-Host "[WARN] mysql client major=$mysqlMajor khác 8.x - dự án yêu cầu MySQL 8.x (khuyến nghị 8.4 LTS). Kiểm tra cả phiên bản SERVER đang chạy." -ForegroundColor Yellow
        }
    }
} else {
    Write-Host '[INFO] mysql client không có trong PATH (không bắt buộc)' -ForegroundColor DarkGray
}

# --- Cổng 6379 (Redis - optional) ---
$redisOk = Test-Port 6379
if ($redisOk) {
    Write-Check 'Cổng 6379 (Redis)' $true 'localhost:6379 đang mở'
} else {
    Write-Host '[SKIP] Cổng 6379 (Redis) không mở - OPTIONAL: app tự dùng fallback in-memory' -ForegroundColor Yellow
}

Write-Host '=========================' -ForegroundColor Cyan
if ($failed -eq 0) {
    Write-Host 'Tất cả kiểm tra bắt buộc PASS. Chạy app: mvn spring-boot:run "-Dspring-boot.run.profiles=dev"' -ForegroundColor Green
} else {
    Write-Host "$failed kiểm tra FAIL - xem docs/DEV-SETUP.md để khắc phục." -ForegroundColor Red
}
exit $failed
