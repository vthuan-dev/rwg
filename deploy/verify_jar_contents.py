"""Kiem tra jar Spring Boot co chua class va migration MOI hay khong.

VI SAO CAN SCRIPT NAY: lan deploy truoc `mvn package` bao BUILD SUCCESS nhung jar KHONG
duoc dong goi lai (tien trinh spring-boot:run dang giu khoa tep trong target/ tren
Windows). Jar cu duoc tai len VPS, service khoi dong sach, health-check tra 200 - moi
buoc deu bao thanh cong, va endpoint moi van 401.

Kiem tra dau ra thay vi tin vao ma tra ve cua build la cach duy nhat bat duoc loai loi
nay. Class nam trong jar LONG (BOOT-INF/lib/rwg-core.jar), nen phai mo hai lop.

Dung: python verify_jar_contents.py <duong-dan-jar> [<duong-dan-jar> ...]
Thoat 1 neu bat ky jar nao thieu thu can co.
"""
import io
import sys
import zipfile

# Nhung thu PHAI co trong jar sau khi build. Them vao day khi co tinh nang moi can
# xac nhan da thuc su vao jar.
REQUIRED = [
    "com/rwg/banner/domain/BannerPlacement.class",
    "db/migration/V20260826_02__them_placement_cho_banners.sql",
    # Lich su dang nhap cua khach tren khu quan tri. KHONG co migration di kem: ca
    # users.last_login_at va audit_log deu da co tu truoc, tinh nang nay chi lo du lieu
    # san co ra API. Nen class DTO nay la dau hieu DUY NHAT xac nhan ma moi vao jar.
    "com/rwg/identity/dto/LoginHistoryEntryResponse.class",
]

NESTED_CORE = "BOOT-INF/lib/rwg-core-0.1.0-SNAPSHOT.jar"


def entries_of(jar_path):
    """Tra ve tap hop moi entry, tinh ca entry trong jar rwg-core long ben trong."""
    with zipfile.ZipFile(jar_path) as outer:
        names = set(outer.namelist())
        if NESTED_CORE in names:
            with zipfile.ZipFile(io.BytesIO(outer.read(NESTED_CORE))) as inner:
                names |= set(inner.namelist())
        else:
            print(f"   CANH BAO: khong thay {NESTED_CORE} trong jar")
    return names


def main(paths):
    failed = False

    for path in paths:
        print(f"{path.replace(chr(92), '/').split('/')[-1]}")
        try:
            names = entries_of(path)
        except Exception as broken:
            print(f"   LOI DOC JAR: {broken}")
            failed = True
            continue

        for required in REQUIRED:
            if required in names:
                print(f"   OK      {required}")
            else:
                print(f"   THIEU   {required}")
                failed = True
        print()

    if failed:
        print("KET QUA: JAR KHONG DAY DU -> dung tien trinh java dang chay roi build lai")
        return 1

    print("KET QUA: jar day du, an toan de tai len")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
