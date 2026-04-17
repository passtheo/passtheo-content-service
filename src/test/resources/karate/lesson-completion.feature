Feature: Lesson Completion Tracking
  As a PassTheo student
  I want my lesson reading to be tracked
  So that I can earn XP and achievements for studying

  Background:
    * url baseUrl
    * def tenantId = '11111111-1111-1111-1111-111111111111'
    * def freshUserId = '77777777-7777-7777-7777-777777777777'
    * def freshHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(freshUserId)', 'Authorization': '#("Bearer " + freshToken)' }

  # ─── FIRST COMPLETION ───

  Scenario: First lesson completion grants XP and triggers first_lesson achievement
    # Pick any known lesson slug from the seeded Strapi content.
    * def lessonSlug = 'verbodsborden-basis'
    Given path '/api/content/lessons/' + lessonSlug + '/complete'
    And headers freshHeaders
    And request { productCode: 'auto-b', topicCode: 'verbodsborden', timeSpentSeconds: 180 }
    When method POST
    Then status 200
    And match response.success == true
    And match response.data.lessonSlug == lessonSlug
    And match response.data.isCompleted == true
    And match response.data.completedAt == '#string'
    And match response.data.xpUpdate.xpEarned == '#number'
    And assert response.data.xpUpdate.xpEarned >= 20
    And match response.data.xpUpdate.totalXp == '#number'
    And match response.data.xpUpdate.currentLevel == '#number'
    And match response.data.xpUpdate.leveledUp == '#boolean'
    And match response.data.newAchievements == '#array'
    # first_lesson achievement should be granted on first-ever completion
    And match response.data.newAchievements[*].code contains 'first_lesson'

  # ─── IDEMPOTENCY ───

  Scenario: Re-completing a lesson returns success with zero XP and no new achievements
    * def lessonSlug = 'verbodsborden-basis'
    # Complete once — may trigger achievements
    Given path '/api/content/lessons/' + lessonSlug + '/complete'
    And headers freshHeaders
    And request { productCode: 'auto-b', topicCode: 'verbodsborden', timeSpentSeconds: 60 }
    When method POST
    Then status 200
    # Complete again — should be idempotent
    Given path '/api/content/lessons/' + lessonSlug + '/complete'
    And headers freshHeaders
    And request { productCode: 'auto-b', topicCode: 'verbodsborden', timeSpentSeconds: 30 }
    When method POST
    Then status 200
    And match response.data.isCompleted == true
    And match response.data.xpUpdate.xpEarned == 0
    And match response.data.newAchievements == '#[0]'

  # ─── UNCOMPLETE ───

  Scenario: Uncomplete reverses the completion flag without refunding XP
    * def lessonSlug = 'verbodsborden-basis'
    # Complete first
    Given path '/api/content/lessons/' + lessonSlug + '/complete'
    And headers freshHeaders
    And request { productCode: 'auto-b', topicCode: 'verbodsborden', timeSpentSeconds: 120 }
    When method POST
    Then status 200
    # Uncomplete
    Given path '/api/content/lessons/' + lessonSlug + '/complete'
    And headers freshHeaders
    And param productCode = 'auto-b'
    When method DELETE
    Then status 200
    And match response.success == true
    # Fetch progress and confirm the row still exists but isCompleted=false
    Given path '/api/content/lessons/progress'
    And headers freshHeaders
    And param productCode = 'auto-b'
    And param topicCode = 'verbodsborden'
    When method GET
    Then status 200
    And match response.data[?(@.lessonSlug=='verbodsborden-basis')].isCompleted[0] == false

  # ─── PROGRESS LIST ───

  Scenario: Progress listing returns only lessons the user has interacted with
    Given path '/api/content/lessons/progress'
    And headers freshHeaders
    And param productCode = 'auto-b'
    And param topicCode = 'verbodsborden'
    When method GET
    Then status 200
    And match response.data == '#array'
    And match each response.data contains { lessonSlug: '#string', isCompleted: '#boolean', timeSpentSeconds: '#number' }

  Scenario: Progress listing is empty for a topic the user has never touched
    Given path '/api/content/lessons/progress'
    And headers freshHeaders
    And param productCode = 'auto-b'
    And param topicCode = 'gevaarherkenning-basis'
    When method GET
    Then status 200
    And match response.data == '#[0]'
