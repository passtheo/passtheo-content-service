package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.StudyPlanDay;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for StudyPlanDay entities.
 */
@Repository
public interface StudyPlanDayRepository extends JpaRepository<StudyPlanDay, UUID> {

    /**
     * Finds all days for a plan ordered by day number.
     *
     * @param planId the plan ID
     * @return list of plan days
     */
    List<StudyPlanDay> findByPlanIdOrderByDayNumberAsc(@Nonnull UUID planId);

    /**
     * Finds today's plan day.
     *
     * @param planId   the plan ID
     * @param planDate today's date
     * @return today's plan day if found
     */
    Optional<StudyPlanDay> findByPlanIdAndPlanDate(@Nonnull UUID planId, @Nonnull LocalDate planDate);

    /**
     * Deletes all plan days for a plan.
     *
     * @param planId the plan ID
     */
    void deleteByPlanId(@Nonnull UUID planId);
}
