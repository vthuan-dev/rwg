package com.rwg.game.repository;

import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Repository bàn chơi cấu hình. */
@Repository
public interface GameTableRepository extends JpaRepository<GameTable, UUID> {

    List<GameTable> findByStatus(GameTableStatus status);
}
