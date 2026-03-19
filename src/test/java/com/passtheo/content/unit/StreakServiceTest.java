package com.passtheo.content.unit;

import com.passtheo.content.domain.entity.Streak;
import com.passtheo.content.domain.valueobject.StreakResult;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.content.service.StreakService;
import com.passtheo.shared.core.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StreakService — streak tracking, freeze slots, milestones.
 */
@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock private StreakRepository streakRepository;

    private StreakService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PRODUCT_CODE = "auto-b";

    @BeforeEach
    void setUp() {
        service = new StreakService(streakRepository);
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateStreak_firstStudyDay_createsStreakOfOne() {
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.empty());
        when(streakRepository.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        StreakResult result = service.updateStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.longestStreak()).isEqualTo(1);
        assertThat(result.totalStudyDays()).isEqualTo(1);
        assertThat(result.isNewDay()).isTrue();
    }

    @Test
    void updateStreak_consecutiveDay_extendsStreak() {
        Streak existing = createStreak(3, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.of(existing));
        when(streakRepository.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        StreakResult result = service.updateStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.currentStreak()).isEqualTo(4);
        assertThat(result.isNewDay()).isTrue();
    }

    @Test
    void updateStreak_sameDay_noChange() {
        Streak existing = createStreak(5, LocalDate.now(ZoneOffset.UTC));
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.of(existing));
        when(streakRepository.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        StreakResult result = service.updateStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.currentStreak()).isEqualTo(5);
        assertThat(result.isNewDay()).isFalse();
    }

    @Test
    void updateStreak_missedOneDay_withFreezeSlot_maintainsStreak() {
        Streak existing = createStreak(5, LocalDate.now(ZoneOffset.UTC).minusDays(2));
        existing.setFreezeSlotsAvailable(2);
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.of(existing));
        when(streakRepository.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        StreakResult result = service.updateStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.currentStreak()).isEqualTo(6);
        assertThat(result.freezeSlotsAvailable()).isEqualTo(1);
        assertThat(result.freezeSlotsUsed()).isEqualTo(1);
    }

    @Test
    void updateStreak_missedOneDay_withoutFreezeSlot_resetsToOne() {
        Streak existing = createStreak(5, LocalDate.now(ZoneOffset.UTC).minusDays(2));
        existing.setFreezeSlotsAvailable(0);
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.of(existing));
        when(streakRepository.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        StreakResult result = service.updateStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.currentStreak()).isEqualTo(1);
    }

    @Test
    void updateStreak_missedMultipleDays_resetsToOne() {
        Streak existing = createStreak(10, LocalDate.now(ZoneOffset.UTC).minusDays(5));
        existing.setFreezeSlotsAvailable(3);
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.of(existing));
        when(streakRepository.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        StreakResult result = service.updateStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.currentStreak()).isEqualTo(1);
    }

    @Test
    void updateStreak_longestStreak_updatesWhenNewRecord() {
        Streak existing = createStreak(5, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        existing.setLongestStreak(5);
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.of(existing));
        when(streakRepository.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        StreakResult result = service.updateStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.longestStreak()).isEqualTo(6);
    }

    @Test
    void updateStreak_freezeSlotAward_at7Days() {
        Streak existing = createStreak(6, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        existing.setFreezeSlotsAvailable(0);
        existing.setFreezeSlotsUsed(0);
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.of(existing));
        when(streakRepository.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        StreakResult result = service.updateStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.currentStreak()).isEqualTo(7);
        assertThat(result.freezeSlotsAvailable()).isEqualTo(1);
    }

    @Test
    void getStreak_freshUser_returnsZeros() {
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT_CODE))
                .thenReturn(Optional.empty());

        StreakResult result = service.getStreak(USER_ID, PRODUCT_CODE);

        assertThat(result.currentStreak()).isEqualTo(0);
        assertThat(result.longestStreak()).isEqualTo(0);
        assertThat(result.studiedToday()).isFalse();
        assertThat(result.freezeSlotsAvailable()).isEqualTo(0);
    }

    private Streak createStreak(int currentStreak, LocalDate lastStudyDate) {
        Streak streak = new Streak(USER_ID, PRODUCT_CODE);
        streak.setTenantId(TENANT_ID);
        streak.setCurrentStreak(currentStreak);
        streak.setLongestStreak(currentStreak);
        streak.setLastStudyDate(lastStudyDate);
        streak.setTotalStudyDays(currentStreak);
        streak.setFreezeSlotsAvailable(0);
        streak.setFreezeSlotsUsed(0);
        return streak;
    }
}
