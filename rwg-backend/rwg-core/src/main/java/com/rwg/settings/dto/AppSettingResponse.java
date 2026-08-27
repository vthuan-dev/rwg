package com.rwg.settings.dto;

import java.time.Instant;

/**
 * Một mục cấu hình chữ trả cho khu quản trị.
 *
 * Kèm {@code updatedAt} và {@code updatedByUsername}: người vận hành cần biết nội dung
 * khách đang đọc được sửa lần cuối khi nào và bởi ai — không có thông tin đó thì mỗi lần
 * nội dung sai là một cuộc tranh luận không có căn cứ.
 */
public record AppSettingResponse(
        String key,
        String value,
        Instant updatedAt,
        /** null với giá trị được seed lúc cài đặt, chưa ai sửa. */
        String updatedByUsername
) {
}
