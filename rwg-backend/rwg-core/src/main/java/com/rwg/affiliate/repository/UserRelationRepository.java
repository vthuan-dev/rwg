package com.rwg.affiliate.repository;

import com.rwg.affiliate.domain.UserRelation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRelationRepository extends JpaRepository<UserRelation, UUID> {

    /** Tuyến trên của một user ở mọi cấp (tối đa 2 dòng). */
    List<UserRelation> findByDescendantId(UUID descendantId);

    /** Tuyến dưới của một đại lý ở một cấp — phân trang cho màn admin. */
    Page<UserRelation> findByAncestorIdAndLevel(UUID ancestorId, short level, Pageable pageable);

    long countByAncestorIdAndLevel(UUID ancestorId, short level);

    /**
     * Toàn bộ id tuyến dưới của một đại lý ở một cấp — job hoa hồng dùng để gom
     * turnover. Trả id thuần (không load entity) vì job chỉ cần khóa để JOIN.
     */
    @Query("select r.descendantId from UserRelation r "
            + "where r.ancestorId = :ancestorId and r.level = :level")
    List<UUID> findDescendantIds(@Param("ancestorId") UUID ancestorId, @Param("level") short level);

    /**
     * Các đại lý có ít nhất 1 tuyến dưới — job chỉ quét những người này thay vì
     * toàn bộ bảng users.
     */
    @Query("select distinct r.ancestorId from UserRelation r")
    List<UUID> findAllAgentIds();

    /**
     * Dùng để CHẶN VÒNG LẶP quan hệ giới thiệu: kiểm tra candidate có đang là
     * tuyến dưới của user hay không. Nếu có mà vẫn cho phép candidate làm tuyến
     * trên thì hai người thành tuyến trên của nhau -> job hoa hồng tự trả chéo.
     */
    boolean existsByAncestorIdAndDescendantId(UUID ancestorId, UUID descendantId);
}
