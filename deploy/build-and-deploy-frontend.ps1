# =============================================================================
# Build frontend RWG va day len VPS.
#
# VI SAO CO SCRIPT NAY thay vi go tay moi lan:
# Lan deploy dau tien toi go tay 7 bien NEXT_PUBLIC_* va SOT MOT bien
# (NEXT_PUBLIC_ADMIN_WS_URL). Bien thieu khong gay loi build - Next.js chi lang le
# dung gia tri mac dinh trong ma nguon, la "http://localhost:8081/ws". Ket qua:
# WebSocket khu quan tri goi ve localhost tren may NGUOI DUNG, va vi la http:// tren
# trang https:// nen trinh duyet chan mixed content -> Chrome bao "Not secure".
#
# Script nay khai bao DAY DU o mot cho va KIEM sau khi build, nen loi do khong the
# lap lai.
# =============================================================================

$ErrorActionPreference = "Stop"

$DOMAIN   = "gentingcasino.pw"
$VPS      = "root@223.130.11.200"
$VPS_PW   = "0904521297t"
$PLINK    = "C:\Program Files\PuTTY\plink.exe"
$PSCP     = "C:\Program Files\PuTTY\pscp.exe"
$FE       = "d:\Project\RWG\rwg-frontend"

# --- TAT CA 8 bien NEXT_PUBLIC_* ma ma nguon doc ---
# Danh sach nay lay tu: grep -o 'process\.env\.[A-Z_]+' trong src/
$env:NEXT_PUBLIC_USER_API_URL      = "https://$DOMAIN/api/v1"
$env:NEXT_PUBLIC_ADMIN_API_URL     = "https://$DOMAIN/admin-api/v1"
$env:NEXT_PUBLIC_USER_BASE_URL     = "https://$DOMAIN"
$env:NEXT_PUBLIC_ADMIN_BASE_URL    = "https://$DOMAIN/admin-api"
$env:NEXT_PUBLIC_WS_URL            = "https://$DOMAIN/ws"
# Ca hai app dung CHUNG duong /ws cua nginx: nginx proxy /ws sang cong 9080 (app
# nguoi choi). Chat relay qua Redis pub/sub day su kien giua hai app, nen nhan su
# noi vao 9080 van nhan duoc tin cua nguoi choi.
$env:NEXT_PUBLIC_ADMIN_WS_URL      = "https://$DOMAIN/ws"
$env:NEXT_PUBLIC_SITE_URL          = "https://$DOMAIN"
$env:NEXT_PUBLIC_ADMIN_SECRET_PATH = "2026"

Write-Output "########## 1. BIEN MOI TRUONG ##########"
Get-ChildItem env:NEXT_PUBLIC_* | ForEach-Object { "   $($_.Name) = $($_.Value)" }

Write-Output ""
Write-Output "########## 2. BUILD ##########"
Push-Location $FE
try {
    npm run build 2>&1 | Select-Object -Last 8 | ForEach-Object { "   $_" }
    if ($LASTEXITCODE -ne 0) { throw "npm run build that bai" }
} finally { Pop-Location }

Write-Output ""
Write-Output "########## 3. KIEM BUNDLE (chan loi sot bien lap lai) ##########"
$bad = @()
foreach ($pat in @("localhost:8080", "localhost:8081", "localhost:3000", "localhost:9080")) {
    $hits = Get-ChildItem "$FE\.next\static" -Recurse -File -Include "*.js" |
            Select-String -Pattern $pat -SimpleMatch -List
    if ($hits) {
        $bad += "$pat (trong $($hits.Count) tep)"
        Write-Output "   !!! CON $pat"
        $hits | Select-Object -First 2 | ForEach-Object { "       $($_.Filename)" }
    } else {
        Write-Output "   OK khong con $pat"
    }
}
if ($bad.Count -gt 0) {
    throw "Bundle con URL localhost: $($bad -join ', '). Kiem lai bien NEXT_PUBLIC_*."
}

$hasDomain = Get-ChildItem "$FE\.next\static" -Recurse -File -Include "*.js" |
             Select-String -Pattern $DOMAIN -SimpleMatch -List
if (-not $hasDomain) { throw "Bundle KHONG chua $DOMAIN - bien moi truong chua vao build" }
Write-Output "   OK bundle co $DOMAIN"

Write-Output ""
Write-Output "########## 4. DONG GOI ##########"
$stage = "d:\Project\RWG\.deploy-tmp"
$pkg   = "d:\Project\RWG\.deploy-fe.tar.gz"
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
if (Test-Path $pkg)   { Remove-Item $pkg -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null

# BO .next/cache va .next/dev: cache build va dev build cua may dev, khong dung
# duoc o production va chiem ~330 MB. Dung robocopy chu khong Copy-Item vi
# Copy-Item -Exclude khong loai tru duoc THU MUC long sau.
robocopy "$FE\.next" "$stage\.next" /E /XD cache dev /NFL /NDL /NJH /NJS /NP | Out-Null
robocopy "$FE\public" "$stage\public" /E /NFL /NDL /NJH /NJS /NP | Out-Null
Copy-Item "$FE\package.json","$FE\package-lock.json","$FE\next.config.ts" $stage

# --- .env.production: bien moi truong cho LUC CHAY, khong chi luc build ---
#
# VI SAO CAN: `next start` DOC LAI next.config.ts moi lan khoi dong. Moi phan cua
# config co doc process.env (remotePatterns, rewrites, redirects) se duoc tinh lai o
# thoi diem do - khong dung gia tri da co luc build.
#
# Lan truoc thieu buoc nay: images.remotePatterns suy tu NEXT_PUBLIC_USER_BASE_URL,
# build ra dung "gentingcasino.pw" va nam trong required-server-files.json, nhung
# tien trinh rwg-web khong co bien nao nen config tinh lai thanh "localhost:8080".
# Ket qua: anh banner tai len bao '"url" parameter is not allowed', dung nhu khi
# chua sua gi - trong khi ban build thi hoan toan dung.
#
# Sinh tu CHINH cac bien da khai o dau script, khong go lai tay: hai danh sach roi
# nhau se lech nhau ngay lan dau ai do them mot bien moi.
$envLines = Get-ChildItem env:NEXT_PUBLIC_* | Sort-Object Name |
            ForEach-Object { "$($_.Name)=$($_.Value)" }
[System.IO.File]::WriteAllText("$stage\.env.production", ($envLines -join "`n") + "`n")
Write-Output "   .env.production: $($envLines.Count) bien"

$sizeMB = [math]::Round((Get-ChildItem "$stage\.next" -Recurse -File | Measure-Object Length -Sum).Sum/1MB)
Write-Output "   .next: $sizeMB MB"
# Sau khi loai bo .next/dev (310 MB dev build), bundle production chuan chi khoang 15-25 MB.
# Kiem nguong > 10 MB de dam bao bo build khong bi thieu.
if ($sizeMB -lt 10) { throw ".next chi $sizeMB MB - nghi thieu (binh thuong ~16-25 MB). Kiem dung luong o dia." }

tar -czf $pkg -C $stage .
$pkgMB = [math]::Round((Get-Item $pkg).Length/1MB, 1)
Write-Output "   goi: $pkgMB MB"
Remove-Item $stage -Recurse -Force

Write-Output ""
Write-Output "########## 5. TAI LEN ##########"
& cmd /c "`"$PSCP`" -batch -pw $VPS_PW `"$pkg`" ${VPS}:/opt/rwg/ 2>&1" | Select-Object -Last 1
Remove-Item $pkg -Force

Write-Output ""
Write-Output "########## 6. TRIEN KHAI TREN VPS ##########"
$remote = @'
set -eu
systemctl stop rwg-web
rm -rf /opt/rwg/frontend/.next.old
[ -d /opt/rwg/frontend/.next ] && mv /opt/rwg/frontend/.next /opt/rwg/frontend/.next.old
mkdir -p /opt/rwg/frontend
tar -xzf /opt/rwg/.deploy-fe.tar.gz -C /opt/rwg/frontend
rm -f /opt/rwg/.deploy-fe.tar.gz
chown -R rwg:rwg /opt/rwg/frontend
SIZE=$(du -sm /opt/rwg/frontend/.next | cut -f1)
echo "   .next tren VPS: ${SIZE} MB"
[ "$SIZE" -lt 10 ] && { echo "   THIEU - khoi phuc ban cu"; rm -rf /opt/rwg/frontend/.next; mv /opt/rwg/frontend/.next.old /opt/rwg/frontend/.next; systemctl start rwg-web; exit 1; }
cd /opt/rwg/frontend && sudo -u rwg /opt/node-22/bin/npm ci --omit=dev --no-audit --no-fund 2>&1 | tail -2
systemctl start rwg-web
for i in $(seq 1 60); do curl -fsS -o /dev/null http://127.0.0.1:9000/ 2>/dev/null && { echo "   LEN SAU $i GIAY"; break; }; sleep 1; done
echo "   rwg-web: $(systemctl is-active rwg-web)"
rm -rf /opt/rwg/frontend/.next.old
'@
$remote = $remote -replace "`r`n", "`n"
$tmpSh = "d:\Project\RWG\.deploy-remote.sh"
[System.IO.File]::WriteAllText($tmpSh, $remote)
& cmd /c "type `"$tmpSh`" | `"$PLINK`" -ssh -batch -pw $VPS_PW $VPS `"bash -s`" 2>&1"
Remove-Item $tmpSh -Force

Write-Output ""
Write-Output "########## 7. KIEM TU BEN NGOAI ##########"
foreach ($p in @("/", "/login", "/admin/2026/login")) {
    $code = (& cmd /c "`"$PLINK`" -ssh -batch -pw $VPS_PW $VPS `"curl -sS -o /dev/null -w '%{http_code}' https://$DOMAIN$p`" 2>&1")
    Write-Output "   https://$DOMAIN$p -> HTTP $code"
}

Write-Output ""
Write-Output "########## 8. KIEM ANH BANNER QUA IMAGE OPTIMIZER ##########"
# VI SAO KIEM RIENG BUOC NAY: cac duong dan o buoc 7 tra HTTP 200 ngay ca khi anh
# banner hoan toan khong hien duoc - trang chu van dung, chi rieng o anh la hong.
# Loi do tung len that va khong buoc nao trong script phat hien ra.
#
# Anh banner di qua /_next/image, va duong dan do bi chan neu images.remotePatterns
# khong khop host. Kiem bang mot anh THAT tren dia thay vi ten gan cung: ten tep la
# UUID sinh luc tai len nen khong the biet truoc.
$checkImg = @'
set -eu
IMG=$(ls -t /opt/rwg/media/*.jpg /opt/rwg/media/*.png /opt/rwg/media/*.webp 2>/dev/null | head -1)
if [ -z "$IMG" ]; then echo "   BO QUA - chua co anh banner nao tren dia"; exit 0; fi
NAME=$(basename "$IMG")
RAW="https://__DOMAIN__/uploads/media/$NAME"
echo "   anh thu: $NAME"
DIRECT=$(curl -sS -o /dev/null -w '%{http_code}' "$RAW")
echo "   tai truc tiep      -> HTTP $DIRECT"
ENC=$(printf '%s' "$RAW" | sed 's|:|%3A|g; s|/|%2F|g')
OPT=$(curl -sS -o /dev/null -w '%{http_code}' "https://__DOMAIN__/_next/image?url=$ENC&w=640&q=75")
echo "   qua image optimizer -> HTTP $OPT"
[ "$OPT" = "200" ] || { echo "   LOI: optimizer tra $OPT, anh banner se hien thanh o hong"; exit 1; }
echo "   OK anh banner hien duoc"
'@
$checkImg = ($checkImg -replace "__DOMAIN__", $DOMAIN) -replace "`r`n", "`n"
$tmpChk = "d:\Project\RWG\.deploy-check-img.sh"
[System.IO.File]::WriteAllText($tmpChk, $checkImg)
& cmd /c "type `"$tmpChk`" | `"$PLINK`" -ssh -batch -pw $VPS_PW $VPS `"bash -s`" 2>&1"
Remove-Item $tmpChk -Force
