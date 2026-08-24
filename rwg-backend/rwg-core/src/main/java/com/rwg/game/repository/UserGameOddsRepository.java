package com.rwg.game.repository;

import com.rwg.game.domain.BetType;
import com.rwg.game.domain.UserGameOdds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserGameOddsRepository extends JpaRepository<UserGameOdds, UUID> {

    /**
     * Mọi tỷ lệ riêng của một người ở một bàn.
     *
     * Lấy CẢ BÀN một lượt chứ không từng loại cược: thanh toán một ván chạy vòng qua mọi
     * cược, mỗi cược một truy vấn sẽ thành hàng chục lượt đi lại cơ sở dữ liệu trong 5
     * giây của pha thanh toán.
     */
    List<UserGameOdds> findByUserIdAndTableId(UUID userId, UUID tableId);

    Optional<UserGameOdds> findByUserIdAndTableIdAndBetType(UUID userId, UUID tableId, BetType betType);

    /** Mọi tỷ lệ riêng của một người, mọi bàn — dùng cho màn hình quản trị. */
    List<UserGameOdds> findByUserIdOrderByTableIdAscBetTypeAsc(UUID userId);

    /**
     * Người chơi ở một bàn có tỷ lệ riêng nào không.
     *
     * Dùng để bỏ qua hẳn việc tra cứu khi thanh toán những bàn mà không ai có tỷ lệ
     * riêng, tức là phần lớn trường hợp.
     */
    @Query("SELECT COUNT(o) > 0 FROM UserGameOdds o WHERE o.tableId = :tableId")
    boolean existsAnyForTable(@Param("tableId") UUID tableId);

    void deleteByUserIdAndTableIdAndBetType(UUID userId, UUID tableId, BetType betType);
}
