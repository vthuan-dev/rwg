package com.rwg.report.api;

import com.rwg.common.PageResponse;
import com.rwg.report.dto.LedgerBetResponse;
import com.rwg.report.dto.LedgerOverviewResponse;
import com.rwg.report.dto.PlayerLedgerResponse;
import com.rwg.report.service.PlayerLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * API báo cáo sổ sách (READ-ONLY).
 *
 * PHÂN QUYỀN HẸP HƠN KHU QUẢN TRỊ CHUNG: chỉ {@code ADMIN} và {@code FINANCE}. Báo cáo
 * này phơi ra toàn bộ lịch sử tiền của một người chơi — số dư, tiền nạp, tiền rút, thắng
 * thua từng game. {@code SUPPORT} và {@code RISK} không có nghiệp vụ nào cần tới nó.
 * Matcher tương ứng nằm trong {@code SecurityConfig} và phải đứng TRƯỚC matcher chung
 * {@code /api/v1/admin/**}, vì Spring lấy quy tắc khớp đầu tiên.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Admin - Reports", description = "Sổ sách người chơi — yêu cầu ROLE_ADMIN hoặc ROLE_FINANCE")
@SecurityRequirement(name = "bearerAuth")
public class AdminReportController {

    private static final int MAX_PAGE_SIZE = 200;

    private final PlayerLedgerService ledgerService;

    public AdminReportController(PlayerLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/ledger/overview")
    @Operation(summary = "Bảng tổng quan sổ sách: một dòng mỗi người chơi có hoạt động trong kỳ")
    public LedgerOverviewResponse overview(@RequestParam(required = false) String month,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return ledgerService.overview(month, keyword, sort, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/players/{userId}/ledger")
    @Operation(summary = "Sổ sách một người chơi trong tháng (nạp/rút/điều chỉnh + thắng thua theo game)")
    public PlayerLedgerResponse ledger(@PathVariable UUID userId,
                                       @RequestParam(required = false) String month) {
        return ledgerService.monthlyLedger(userId, month);
    }

    @GetMapping("/players/{userId}/bets")
    @Operation(summary = "Chi tiết từng ván cược của người chơi tại một game trong tháng")
    public PageResponse<LedgerBetResponse> bets(@PathVariable UUID userId,
                                                @RequestParam String gameType,
                                                @RequestParam(required = false) String month,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        // Sắp xếp đã nằm trong câu truy vấn (createdAt desc) nên PageRequest KHÔNG được
        // truyền Sort: truyền vào sẽ sinh hai mệnh đề ORDER BY và Hibernate báo lỗi.
        var pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.unsorted());
        return PageResponse.from(
                ledgerService.betsForGame(userId, gameType, month, pageable),
                LedgerBetResponse::of);
    }

    @GetMapping(value = "/players/{userId}/ledger.csv", produces = "text/csv")
    @Operation(summary = "Cùng dữ liệu sổ sách, dạng CSV để mở bằng Excel")
    public ResponseEntity<byte[]> ledgerCsv(@PathVariable UUID userId,
                                            @RequestParam(required = false) String month) {
        PlayerLedgerResponse data = ledgerService.monthlyLedger(userId, month);
        byte[] body = toCsv(data);

        String filename = "so-sach-" + data.username() + "-" + data.periodFrom() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /**
     * Dựng CSV bằng tay thay vì dùng thư viện.
     *
     * Tệp này chỉ có hai khối nhỏ và một quy tắc thoát ký tự; thêm một phụ thuộc cho
     * việc đó là không đáng.
     *
     * BOM UTF-8 Ở ĐẦU TỆP LÀ BẮT BUỘC: thiếu nó, Excel trên Windows đọc tệp theo mã hoá
     * cục bộ và mọi dấu tiếng Việt trong tên game hay tên người chơi thành ký tự rác.
     * Đây là hành vi của Excel, không phải lỗi mã hoá của ta.
     */
    private byte[] toCsv(PlayerLedgerResponse d) {
        StringBuilder sb = new StringBuilder();

        sb.append("Sổ sách người chơi\n");
        sb.append("Người chơi,").append(esc(d.username())).append('\n');
        sb.append("Kỳ,").append(esc(d.periodFrom() + " → " + d.periodTo())).append('\n');
        sb.append("Múi giờ,").append(esc(d.timezone())).append('\n');
        sb.append("Đơn vị,").append(esc(d.currency())).append('\n');
        sb.append('\n');

        sb.append("DÒNG TIỀN VÀO / RA\n");
        sb.append("Số dư đầu kỳ,").append(esc(d.openingBalance())).append('\n');
        sb.append("Nạp qua cổng,").append(esc(d.depositViaGateway())).append('\n');
        sb.append("Admin cộng,").append(esc(d.adminCredit())).append('\n');
        sb.append("Admin trừ,").append(esc(d.adminDebit())).append('\n');
        sb.append("Rút thành công,").append(esc(d.withdrawalSettled())).append('\n');
        sb.append("Số dư cuối kỳ,").append(esc(d.closingBalance())).append('\n');
        sb.append('\n');

        sb.append("THẮNG / THUA THEO GAME\n");
        sb.append("Game,Số ván,Tiền cược,Tiền thắng,Lãi lỗ,Đang treo\n");
        for (PlayerLedgerResponse.GameLine g : d.games()) {
            sb.append(esc(g.gameType())).append(',')
                    .append(g.betCount()).append(',')
                    .append(esc(g.stake())).append(',')
                    .append(esc(g.payout())).append(',')
                    .append(esc(g.net())).append(',')
                    .append(esc(g.pendingStake())).append('\n');
        }
        sb.append("TỔNG,,").append(esc(d.totalStake())).append(',')
                .append(esc(d.totalPayout())).append(',')
                .append(esc(d.totalNet())).append(',')
                .append(esc(d.totalPending())).append('\n');

        // \uFEFF = BOM UTF-8.
        return ("\uFEFF" + sb).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Thoát một ô CSV.
     *
     * Bọc nháy kép khi ô chứa dấu phẩy, nháy kép hoặc dấu xuống dòng, và nhân đôi nháy
     * kép bên trong — đúng RFC 4180. Tên người chơi do người dùng tự đặt nên hoàn toàn
     * có thể chứa dấu phẩy, và một ô không thoát sẽ làm lệch mọi cột phía sau.
     */
    private String esc(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
