package com.passtheo.content.dto.response;

/**
 * XP earned from a study activity (practice session or exam).
 *
 * @param xpEarned       XP earned in this activity
 * @param totalXp        cumulative XP after this activity
 * @param currentLevel   current level after this activity
 * @param xpForNextLevel XP threshold for the next level
 * @param leveledUp      whether a level-up occurred
 */
public record XpUpdateDto(
    int xpEarned,
    int totalXp,
    int currentLevel,
    int xpForNextLevel,
    boolean leveledUp
) {}
