package com.rwg.common.geo;

/**
 * Vị trí địa lý suy ra từ một địa chỉ IP.
 *
 * KHÔNG nằm trong package {@code ..dto..} vì đây không phải hình dạng dữ liệu của một
 * endpoint — nó là kết quả nội bộ của {@link GeoIpLookupService}, được ghi vào cơ sở dữ
 * liệu rồi mới xuất hiện trong DTO của chat.
 *
 * MỌI TRƯỜNG ĐỀU CÓ THỂ NULL. Dịch vụ tra IP thường xuyên chỉ biết quốc gia mà không
 * biết tỉnh, hoặc không biết gì cả (IP mạng nội bộ, IP của dịch vụ ẩn danh). Ép các
 * trường thành bắt buộc sẽ buộc chỗ gọi phải bỏ cả kết quả chỉ vì thiếu một phần, trong
 * khi "biết nước, không biết tỉnh" vẫn hữu ích cho người trả lời hỗ trợ.
 */
public record GeoLocation(
        /** Mã ISO 3166-1 alpha-2, ví dụ VN / KH / TH. */
        String countryCode,
        String countryName,
        /** Tỉnh / thành phố trực thuộc / vùng. */
        String region,
        String city,
        /** Nhà mạng hoặc ISP — dấu hiệu nhận ra VPN và mạng doanh nghiệp. */
        String isp
) {

    /**
     * Kết quả "đã tra nhưng không xác định được".
     *
     * CÓ MỘT GIÁ TRỊ RIÊNG cho trường hợp này thay vì trả null: chỗ gọi cần phân biệt
     * "tra xong, IP này không ra gì" với "chưa tra". Trả null cho cả hai sẽ khiến hệ
     * thống gọi lại dịch vụ ngoài mãi mãi cho những IP chắc chắn không bao giờ ra kết quả.
     */
    public static GeoLocation unknown() {
        return new GeoLocation(null, null, null, null, null);
    }

    /** Có biết được gì hay không — dùng để quyết định có hiện phần vị trí trên giao diện. */
    public boolean hasAnyData() {
        return countryCode != null || region != null || city != null;
    }
}
