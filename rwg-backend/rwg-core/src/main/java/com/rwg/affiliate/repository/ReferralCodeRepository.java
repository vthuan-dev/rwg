package com.rwg.affiliate.repository;

import com.rwg.affiliate.domain.ReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralCodeRepository extends JpaRepository<ReferralCode, String> {

    Optional<ReferralCode> findByUserId(UUID userId);

    boolean existsByCode(String code);
}
