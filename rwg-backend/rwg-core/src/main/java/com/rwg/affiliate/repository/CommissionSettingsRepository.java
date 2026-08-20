package com.rwg.affiliate.repository;

import com.rwg.affiliate.domain.CommissionSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommissionSettingsRepository extends JpaRepository<CommissionSettings, Short> {
}
