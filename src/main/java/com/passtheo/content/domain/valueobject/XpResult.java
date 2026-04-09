package com.passtheo.content.domain.valueobject;

/**
 * Result of an XP grant operation.
 *
 * @param xpEarned       XP earned in this operation
 * @param totalXp        total XP after grant
 * @param currentLevel   current level after grant
 * @param xpForNextLevel XP threshold for the next level
 * @param leveledUp      whether a level-up occurred
 * @param previousLevel  level before this grant
 */
public record XpResult(
    int xpEarned,
    int totalXp,
    int currentLevel,
    int xpForNextLevel,
    boolean leveledUp,
    int previousLevel
) { }
