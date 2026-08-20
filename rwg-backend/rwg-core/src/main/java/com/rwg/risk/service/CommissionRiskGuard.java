package com.rwg.risk.service;

import com.rwg.risk.domain.AccountLink;
import com.rwg.risk.repository.AccountLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cổng chặn dòng hoa hồng cho tuyến dưới bị xác định là CHÍNH đại lý (chặng 7).
 *
 * Tách riêng khỏi {@link AccountLinkDetector} vì hai việc khác nhau hẳn: detector
 * GHI dữ liệu lúc đăng ký, còn lớp này chỉ ĐỌC và được gọi từ luồng tiền của job
 * hoa hồng. Job affiliate tiêm lớp này qua ObjectProvider nên module affiliate
 * KHÔNG phụ thuộc cứng vào risk.
 */
@Service
public class CommissionRiskGuard {

    private final AccountLinkRepository linkRepository;

    public CommissionRiskGuard(AccountLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    /**
     * Loại khỏi danh sách tuyến dưới những người bị liên kết với chính đại lý.
     *
     * VÌ SAO LOẠI: turnover của họ là cược thật, nhưng trả hoa hồng cho nó là trả
     * tiền cho chính người đã cược — thiệt hại thuần cho sàn, không phải chi phí
     * marketing. Đây là điểm chặn duy nhất; tài khoản KHÔNG bị khoá.
     *
     * Luật "liên kết nào giữ tiền" nằm trong {@link AccountLink#blocksCommission()}
     * để job và API admin không thể hiểu khác nhau.
     *
     * @return danh sách đã lọc; trả về chính input nếu không có gì bị loại
     */
    @Transactional(readOnly = true)
    public List<UUID> excludeLinked(UUID agentId, List<UUID> descendantIds) {
        if (descendantIds.isEmpty()) {
            return descendantIds;
        }
        Set<UUID> blocked = linkRepository.findAllForUser(agentId).stream()
                .filter(AccountLink::blocksCommission)
                .map(link -> link.otherThan(agentId))
                .collect(Collectors.toSet());

        if (blocked.isEmpty()) {
            return descendantIds;
        }
        return descendantIds.stream()
                .filter(id -> !blocked.contains(id))
                .toList();
    }
}
