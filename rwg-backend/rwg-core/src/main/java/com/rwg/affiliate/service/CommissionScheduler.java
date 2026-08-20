package com.rwg.affiliate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Bộ hẹn giờ chốt hoa hồng hàng ngày.
 *
 * CHỈ BẬT Ở MỘT APP DUY NHẤT (rwg-user-app đặt
 * {@code rwg.commission.scheduler-enabled: true}). {@code matchIfMissing = false}
 * nên app admin — dù có quét package affiliate để phục vụ API quản trị — sẽ KHÔNG
 * tạo bean này. Hai instance cùng chi tiền là rủi ro thật; tuy đã có
 * uq_commission_runs_agent_period_level chặn chi trùng, việc để hai scheduler chạy
 * song song vẫn gây tranh chấp và log lỗi nhiễu vô ích.
 *
 * Chốt theo NGÀY UTC HÔM QUA: ngày hôm nay chưa kết thúc, chốt sớm sẽ thiếu
 * turnover và phải bù. Dùng UTC cho nhất quán với hạn mức rút tiền theo ngày.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "rwg.commission.scheduler-enabled", havingValue = "true")
public class CommissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommissionScheduler.class);

    private final CommissionJob job;

    public CommissionScheduler(CommissionJob job) {
        this.job = job;
    }

    /**
     * Mặc định 01:00 UTC mỗi ngày — đủ trễ để mọi vòng chơi cuối ngày đã settle xong.
     */
    @Scheduled(cron = "${rwg.commission.cron:0 0 1 * * *}", zone = "UTC")
    public void chotHoaHongHomQua() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        try {
            CommissionJob.RunSummary summary = job.runForDate(yesterday, null);
            log.info("Chốt hoa hồng tự động xong: {}", summary);
        } catch (RuntimeException failure) {
            // Không để exception làm chết scheduler thread — ngày sau vẫn phải chạy.
            // Admin có thể chạy lại tay cho ngày lỗi (idempotent).
            log.error("Chốt hoa hồng tự động THẤT BẠI cho ngày {} — cần chạy lại tay",
                    yesterday, failure);
        }
    }
}
