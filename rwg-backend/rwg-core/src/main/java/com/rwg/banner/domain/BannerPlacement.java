package com.rwg.banner.domain;

/**
 * Nơi một banner được hiển thị.
 *
 * VÌ SAO PHẢI TÁCH thay vì để chung một danh sách phẳng: hai loại ảnh này khác nhau
 * về bản chất, và trộn lẫn gây ra hậu quả rất cụ thể — nhân sự tải ảnh bảng thưởng
 * (dọc, nhiều chữ số liệu) sẽ thấy nó xuất hiện luôn trên carousel trang chủ, hoặc
 * một ảnh quảng cáo ngang 16/9 bị gửi cho mọi khách trong khung chat hỗ trợ.
 *
 * Trước khi có cột này, ảnh khuyến mãi chat được GÁN CỨNG trong mã nguồn
 * ({@code ChatPromoMessages.tsx}), nên mỗi lần đổi ảnh là một lần build và triển khai
 * lại toàn bộ frontend.
 */
public enum BannerPlacement {

    /**
     * Carousel ở đầu trang chủ người chơi.
     *
     * Nhiều banner chạy vòng, tỉ lệ 16/9 nằm ngang, nhận cả video và ảnh.
     */
    HOME_CAROUSEL,

    /**
     * Ảnh khuyến mãi gửi tự động khi khách mở khung Trò chuyện trực tiếp.
     *
     * CHỈ MỘT ảnh được dùng thật — ảnh ACTIVE đầu tiên theo thứ tự hiển thị. Vẫn cho
     * lưu nhiều bản để nhân sự chuẩn bị trước cho đợt sau rồi chỉ cần bật lên, thay vì
     * phải xoá ảnh đang chạy mới tải được ảnh mới.
     *
     * Hiển thị theo tỉ lệ THẬT của ảnh, không cắt về 16/9: ảnh loại này thường là bảng
     * số liệu, và phần bị cắt lại đúng là phần khách cần đọc.
     */
    CHAT_PROMO
}
