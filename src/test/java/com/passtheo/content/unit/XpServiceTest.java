package com.passtheo.content.unit;

import com.passtheo.content.domain.entity.UserXp;
import com.passtheo.content.domain.valueobject.XpResult;
import com.passtheo.content.repository.UserXpRepository;
import com.passtheo.content.service.XpService;
import com.passtheo.shared.core.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for XpService — level calculation, XP granting, edge cases.
 */
@ExtendWith(MockitoExtension.class)
class XpServiceTest {

    @Mock private UserXpRepository userXpRepository;

    private XpService xpService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PRODUCT = "auto-b";

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        xpService = new XpService(userXpRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void calculateLevel_zeroXp_returnsLevel1() {
        assertThat(XpService.calculateLevel(0)).isEqualTo(1);
    }

    @Test
    void calculateLevel_99Xp_returnsLevel1() {
        assertThat(XpService.calculateLevel(99)).isEqualTo(1);
    }

    @Test
    void calculateLevel_exactThreshold100_returnsLevel2() {
        assertThat(XpService.calculateLevel(100)).isEqualTo(2);
    }

    @Test
    void calculateLevel_299Xp_returnsLevel2() {
        assertThat(XpService.calculateLevel(299)).isEqualTo(2);
    }

    @Test
    void calculateLevel_300Xp_returnsLevel3() {
        assertThat(XpService.calculateLevel(300)).isEqualTo(3);
    }

    @Test
    void calculateLevel_1000Xp_returnsLevel5() {
        // Level 5 threshold = 5 * 4 * 50 = 1000
        assertThat(XpService.calculateLevel(1000)).isEqualTo(5);
    }

    @Test
    void calculateLevel_4500Xp_returnsLevel10() {
        // Level 10 threshold = 10 * 9 * 50 = 4500
        assertThat(XpService.calculateLevel(4500)).isEqualTo(10);
    }

    @Test
    void levelThreshold_level1_returns0() {
        assertThat(XpService.levelThreshold(1)).isEqualTo(0);
    }

    @Test
    void levelThreshold_level2_returns100() {
        assertThat(XpService.levelThreshold(2)).isEqualTo(100);
    }

    @Test
    void levelThreshold_level5_returns1000() {
        assertThat(XpService.levelThreshold(5)).isEqualTo(1000);
    }

    @Test
    void levelThreshold_level10_returns4500() {
        assertThat(XpService.levelThreshold(10)).isEqualTo(4500);
    }

    @Test
    void grantXp_newUser_createsRecordWithCorrectXp() {
        when(userXpRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(Optional.empty());
        when(userXpRepository.save(any(UserXp.class))).thenAnswer(inv -> inv.getArgument(0));

        XpResult result = xpService.grantXp(USER_ID, PRODUCT, 50);

        assertThat(result.xpEarned()).isEqualTo(50);
        assertThat(result.totalXp()).isEqualTo(50);
        assertThat(result.currentLevel()).isEqualTo(1);
        assertThat(result.leveledUp()).isFalse();
        assertThat(result.xpForNextLevel()).isEqualTo(100); // Level 2 threshold
    }

    @Test
    void grantXp_existingUser_addsXpToTotal() {
        UserXp existing = new UserXp(USER_ID, PRODUCT);
        existing.setTenantId(TENANT_ID);
        existing.setTotalXp(80);
        existing.setCurrentLevel(1);
        when(userXpRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(Optional.of(existing));
        when(userXpRepository.save(any(UserXp.class))).thenAnswer(inv -> inv.getArgument(0));

        XpResult result = xpService.grantXp(USER_ID, PRODUCT, 10);

        assertThat(result.xpEarned()).isEqualTo(10);
        assertThat(result.totalXp()).isEqualTo(90);
        assertThat(result.currentLevel()).isEqualTo(1);
        assertThat(result.leveledUp()).isFalse();
    }

    @Test
    void grantXp_crossesLevelThreshold_levelsUp() {
        UserXp existing = new UserXp(USER_ID, PRODUCT);
        existing.setTenantId(TENANT_ID);
        existing.setTotalXp(90);
        existing.setCurrentLevel(1);
        when(userXpRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(Optional.of(existing));
        when(userXpRepository.save(any(UserXp.class))).thenAnswer(inv -> inv.getArgument(0));

        XpResult result = xpService.grantXp(USER_ID, PRODUCT, 20);

        assertThat(result.xpEarned()).isEqualTo(20);
        assertThat(result.totalXp()).isEqualTo(110);
        assertThat(result.currentLevel()).isEqualTo(2);
        assertThat(result.leveledUp()).isTrue();
        assertThat(result.previousLevel()).isEqualTo(1);
        assertThat(result.xpForNextLevel()).isEqualTo(300); // Level 3 threshold
    }

    @Test
    void grantXp_zeroAmount_doesNotModify() {
        XpResult result = xpService.grantXp(USER_ID, PRODUCT, 0);

        assertThat(result.xpEarned()).isEqualTo(0);
        assertThat(result.totalXp()).isEqualTo(0);
        assertThat(result.currentLevel()).isEqualTo(1);
    }

    @Test
    void grantXp_negativeAmount_doesNotModify() {
        XpResult result = xpService.grantXp(USER_ID, PRODUCT, -10);

        assertThat(result.xpEarned()).isEqualTo(0);
        verify(userXpRepository, never()).save(any());
    }

    @Test
    void grantXp_multipleGrants_accumulatesCorrectly() {
        UserXp xp = new UserXp(USER_ID, PRODUCT);
        xp.setTenantId(TENANT_ID);
        xp.setTotalXp(0);
        xp.setCurrentLevel(1);
        when(userXpRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(Optional.of(xp));
        when(userXpRepository.save(any(UserXp.class))).thenAnswer(inv -> inv.getArgument(0));

        xpService.grantXp(USER_ID, PRODUCT, 50);
        assertThat(xp.getTotalXp()).isEqualTo(50);

        xpService.grantXp(USER_ID, PRODUCT, 60);
        assertThat(xp.getTotalXp()).isEqualTo(110);
        assertThat(xp.getCurrentLevel()).isEqualTo(2);
    }

    @Test
    void getXp_noRecord_returnsDefaults() {
        when(userXpRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(Optional.empty());

        XpResult result = xpService.getXp(USER_ID, PRODUCT);

        assertThat(result.totalXp()).isEqualTo(0);
        assertThat(result.currentLevel()).isEqualTo(1);
        assertThat(result.xpForNextLevel()).isEqualTo(100);
        assertThat(result.leveledUp()).isFalse();
    }
}
