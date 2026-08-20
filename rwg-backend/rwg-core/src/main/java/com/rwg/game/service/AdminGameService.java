package com.rwg.game.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import com.rwg.game.dto.GameTableResponse;
import com.rwg.game.dto.UpdateTableLimitsRequest;
import com.rwg.game.dto.UpdateTableStatusRequest;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.identity.service.AuditTrailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quản trị bàn chơi (chặng 6).
 *
 * VÌ SAO CẦN: trước đây không có API nào cho game ở khu quản trị. Bàn đang lỗi thì
 * cách duy nhất để tắt là sửa DB bằng tay hoặc khởi động lại app — cả hai đều tệ khi
 * đang có người chơi và tiền thật trên bàn.
 *
 * ===== HAI TIẾN TRÌNH, KHÔNG GỌI HÀM NHAU =====
 * Service này chạy ở app admin (8081), còn vòng lặp bàn chạy ở app player (8080).
 * Nên "tắt bàn" ở đây CHỈ ghi trạng thái vào DB; RoundScheduler bên app player đọc
 * lại trạng thái ở đầu mỗi vòng và tự dừng. Vì vậy tắt bàn có ĐỘ TRỄ tối đa một
 * vòng chơi — đó là lựa chọn có ý thức: dừng ở biên vòng thì không phải hủy vòng
 * đang chạy và hoàn tiền hàng loạt cho mọi người đang cược.
 *
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
@Service
public class AdminGameService {

    private final GameTableRepository tableRepository;
    private final AuditTrailService audit;
    private final ObjectMapper objectMapper;

    public AdminGameService(GameTableRepository tableRepository,
                            AuditTrailService audit,
                            ObjectMapper objectMapper) {
        this.tableRepository = tableRepository;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /**
     * Toàn bộ bàn, gồm cả bàn đã tắt.
     *
     * Khác {@code GameQueryService.listActiveTables} (chỉ trả ACTIVE cho người chơi):
     * admin phải thấy được bàn đã tắt, nếu không thì tắt rồi sẽ không tìm lại để bật.
     */
    @Transactional(readOnly = true)
    public List<GameTableResponse> listAllTables() {
        return tableRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Bật/tắt bàn. */
    @Transactional
    public GameTableResponse changeStatus(UUID tableId, UpdateTableStatusRequest request,
                                          UUID adminId, String ip) {
        GameTable table = requireTable(tableId);
        GameTableStatus newStatus = GameTableStatus.valueOf(request.status());
        GameTableStatus oldStatus = table.getStatus();

        if (oldStatus == newStatus) {
            // Key RIÊNG cho bàn chơi: error.invalid_status_transition.same_status hiện có
            // nội dung nói về TÀI KHOẢN ("The account is already..."), dùng lại sẽ trả
            // thông điệp sai ngữ cảnh cho người vận hành.
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    ErrorCode.INVALID_STATUS_TRANSITION.defaultMessage(),
                    Map.of("status", newStatus.name()),
                    "error.invalid_status_transition.table_same_status");
        }

        table.changeStatus(newStatus);
        tableRepository.saveAndFlush(table);

        audit.record(adminId, null, AuditTrailService.ADMIN_TABLE_STATUS_CHANGED,
                "GAME_TABLE", tableId.toString(),
                Map.of("oldStatus", oldStatus.name(),
                        "newStatus", newStatus.name(),
                        "reason", request.reason()), ip);

        return toResponse(table);
    }

    /**
     * Đổi hạn mức cược. Không ảnh hưởng cược ĐÃ đặt — chỉ áp cho cược mới, vì cược
     * cũ đã trừ ví theo hạn mức lúc đó và viết lại sẽ làm lệch sổ.
     */
    @Transactional
    public GameTableResponse updateLimits(UUID tableId, UpdateTableLimitsRequest request,
                                          UUID adminId, String ip) {
        GameTable table = requireTable(tableId);
        BigDecimal oldMin = table.getMinBet();
        BigDecimal oldMax = table.getMaxBet();

        BigDecimal newMin = new BigDecimal(request.minBet());
        BigDecimal newMax = new BigDecimal(request.maxBet());
        try {
            table.updateLimits(newMin, newMax);
        } catch (IllegalArgumentException invalid) {
            // Domain đã chặn min > max; đổi sang lỗi API có i18n thay vì để 500.
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "minBet"), "validation.table.limits.invalid");
        }
        tableRepository.saveAndFlush(table);

        audit.record(adminId, null, AuditTrailService.ADMIN_TABLE_LIMITS_CHANGED,
                "GAME_TABLE", tableId.toString(),
                Map.of("oldMinBet", oldMin.toPlainString(),
                        "newMinBet", newMin.toPlainString(),
                        "oldMaxBet", oldMax.toPlainString(),
                        "newMaxBet", newMax.toPlainString(),
                        "reason", request.reason()), ip);

        return toResponse(table);
    }

    private GameTable requireTable(UUID tableId) {
        return tableRepository.findById(tableId)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_TABLE_NOT_FOUND));
    }

    private GameTableResponse toResponse(GameTable table) {
        Map<String, String> name;
        try {
            name = objectMapper.readValue(table.getNameI18n(),
                    new TypeReference<Map<String, String>>() { });
        } catch (RuntimeException malformed) {
            // Tên bàn hỏng KHÔNG được làm sập cả màn quản trị — đó chính là lúc admin
            // cần vào để sửa. Trả map rỗng để phần còn lại vẫn dùng được.
            name = Map.of();
        }
        return new GameTableResponse(
                table.getId().toString(),
                table.getGameType(),
                name,
                table.getStatus().name(),
                table.getMinBet().toPlainString(),
                table.getMaxBet().toPlainString(),
                table.getCurrency());
    }
}
