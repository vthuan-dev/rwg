package com.rwg;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * @SpringBootConfiguration dùng cho toàn bộ test trong rwg-core.
 * Quét đầy đủ com.rwg (mọi controller + service + background job) như monolith cũ,
 * để các @SpringBootTest hiện có chạy KHÔNG cần chỉnh sửa.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoreTestApplication {
}
