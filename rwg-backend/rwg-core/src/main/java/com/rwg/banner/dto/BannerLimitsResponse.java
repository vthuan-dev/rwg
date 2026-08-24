package com.rwg.banner.dto;

/**
 * Giới hạn hiện hành của khu banner, để khu quản trị hiển thị và kiểm trước.
 *
 * VÌ SAO TRẢ VỀ CẢ {@code currentCount}: trang quản trị cần biết "đang có bao nhiêu
 * trên tối đa bao nhiêu" để hiện nhãn 3/4 và tắt nút khi đã đủ. Đếm từ danh sách đã
 * tải về thì sai khi có phân trang — trang đầu chỉ có 10 bản ghi nên với trần cao hơn
 * 10 con số sẽ lệch.
 *
 * Dung lượng trả về theo BYTE chứ không phải chuỗi "50MB": frontend cần con số để so
 * với {@code file.size} trước khi gửi. Nhãn hiển thị thì frontend tự dựng từ byte.
 *
 * @param maxCount        số banner tối đa được phép tồn tại
 * @param currentCount    số banner đang có, tính CẢ banner đang tắt
 * @param maxImageBytes   dung lượng tối đa một ảnh
 * @param maxVideoBytes   dung lượng tối đa một video
 */
public record BannerLimitsResponse(
        int maxCount,
        long currentCount,
        long maxImageBytes,
        long maxVideoBytes
) {
}
