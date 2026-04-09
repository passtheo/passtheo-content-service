package com.passtheo.content.domain.entity;

import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * Tracks cumulative experience points and level for a user per product.
 */
@Entity
@Table(name = "user_xp")
public class UserXp extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "total_xp", nullable = false)
    private int totalXp;

    @Column(name = "current_level", nullable = false)
    private int currentLevel;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected UserXp() { }

    /**
     * Creates a new XP record for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     */
    public UserXp(UUID keycloakUserId, String productCode) {
        this.keycloakUserId = keycloakUserId;
        this.productCode = productCode;
        this.totalXp = 0;
        this.currentLevel = 1;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(UUID keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public int getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(int totalXp) {
        this.totalXp = totalXp;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int currentLevel) {
        this.currentLevel = currentLevel;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
