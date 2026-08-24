import { NextResponse } from "next/server";

/**
 * Danh sách ngân hàng Việt Nam, lấy từ VietQR.
 *
 * VÌ SAO CÓ ROUTE TRUNG GIAN thay vì gọi thẳng từ trình duyệt:
 *
 * 1. **Cache.** Danh sách ngân hàng gần như không đổi. Cache 24 giờ ở tầng máy chủ nghĩa
 *    là dù hàng nghìn người mở trang, VietQR chỉ nhận một yêu cầu mỗi ngày.
 * 2. **Chịu lỗi.** Khi VietQR sập, người dùng vẫn thấy danh sách trong cache thay vì một
 *    ô chọn trống trơn không lưu được hồ sơ.
 * 3. **Không phụ thuộc bên thứ ba từ trình duyệt.** Trình duyệt chỉ gọi origin của mình.
 *
 * KHÔNG CHUYỂN TIẾP NGUYÊN PHẢN HỒI: chỉ giữ bốn trường cần dùng. Phản hồi gốc còn `id`,
 * `bin`, `transferSupported`, `lookupSupported`, `swift_code`… là chi tiết nội bộ của
 * VietQR, gửi hết xuống trình duyệt vừa nặng vừa buộc giao diện vào lược đồ của họ.
 */

/** Thời gian cache, giây. 24 giờ. */
const CACHE_SECONDS = 86_400;

const VIETQR_URL = "https://api.vietqr.io/v2/banks";

interface VietQRBank {
  code: string;
  bin: string;
  shortName: string;
  name: string;
  logo: string;
}

export interface BankOption {
  /** Mã ngân hàng dạng chứ, ví dụ "VCB". Đây là giá trị lưu vào `bank_accounts.bank_code`. */
  code: string;
  /**
   * Mã BIN dạng số, ví dụ "970436".
   *
   * GIỮ LẠI dù giao diện không dùng để lưu: các tài khoản liên kết từ trước đang lưu
   * BIN thay vì mã chứ. Thiếu trường này thì những tài khoản đó hiện "970436" thay vì
   * "Vietcombank" — một con số vô nghĩa với người dùng đang muốn kiểm lại xem tiền sẽ
   * vào đúng ngân hàng nào.
   */
  bin: string;
  /** Tên ngắn để hiển thị, ví dụ "Vietcombank". */
  shortName: string;
  /** Tên đầy đủ, dùng cho tooltip hoặc tìm kiếm. */
  name: string;
  /** URL logo do VietQR phục vụ. */
  logo: string;
}

export async function GET() {
  try {
    const res = await fetch(VIETQR_URL, {
      next: { revalidate: CACHE_SECONDS },
    });

    if (!res.ok) {
      return errorResponse(`VietQR trả HTTP ${res.status}`);
    }

    const json: unknown = await res.json();

    // Kiểm từng bước thay vì tin vào kiểu: đây là dữ liệu từ bên thứ ba, và nếu họ đổi
    // lược đồ thì phải trả lỗi rõ ràng để giao diện chuyển sang ô gõ mã bằng tay, chứ
    // không đẩy một mảng rỗng xuống rồi để người dùng ngồi nhìn ô chọn trống.
    if (
      typeof json !== "object" ||
      json === null ||
      !("data" in json) ||
      !Array.isArray((json as { data: unknown }).data)
    ) {
      return errorResponse("VietQR trả cấu trúc không như mong đợi");
    }

    const banks: BankOption[] = ((json as { data: VietQRBank[] }).data)
      .filter((b) => typeof b?.code === "string" && typeof b?.shortName === "string")
      .map((b) => ({
        code: b.code,
        bin: b.bin ?? "",
        shortName: b.shortName,
        name: b.name ?? b.shortName,
        logo: b.logo ?? "",
      }));

    if (banks.length === 0) {
      return errorResponse("VietQR trả danh sách rỗng");
    }

    return NextResponse.json({ banks });
  } catch (cause) {
    return errorResponse(cause instanceof Error ? cause.message : "Không gọi được VietQR");
  }
}

/**
 * Lỗi trả về 503 kèm `banks: []`.
 *
 * Dùng 503 chứ không 200: giao diện phải phân biệt được "chưa lấy được danh sách" với
 * "danh sách thật sự rỗng" để quyết định có chuyển sang ô gõ mã bằng tay hay không.
 * Trả 200 với mảng rỗng sẽ khiến người dùng thấy một ô chọn không có gì mà không hiểu vì sao.
 */
function errorResponse(reason: string) {
  return NextResponse.json(
    { banks: [], error: reason },
    { status: 503 },
  );
}
