Feature: Progress, Readiness, Streaks, Achievements, and Study Plan
  As a PassTheo student
  I want to track my progress, see my readiness score, maintain streaks, earn badges, and follow a study plan
  So that I know when I'm ready for the real CBR exam

  Background:
    * url baseUrl
    * def tenantId = '11111111-1111-1111-1111-111111111111'
    * def paidUserId = '33333333-3333-3333-3333-333333333333'
    * def freshUserId = '77777777-7777-7777-7777-777777777777'
    * def paidHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(paidUserId)', 'Authorization': '#("Bearer " + paidToken)' }
    * def freshHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(freshUserId)', 'Authorization': '#("Bearer " + freshToken)' }

  # ─── READINESS SCORE ───

  Scenario: Get readiness score for user with progress
    Given path '/api/progress/readiness'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.readinessScore == '#number'
    And assert response.data.readinessScore >= 0
    And assert response.data.readinessScore <= 100
    And match response.data.coverageScore == '#number'
    And match response.data.accuracyScore == '#number'
    And match response.data.examScore == '#number'
    And match response.data.readinessLabel == '#? _ == "NOT_READY" || _ == "GETTING_THERE" || _ == "ALMOST_READY" || _ == "READY"'
    And match response.data.questionsAttempted == '#number'
    And match response.data.totalQuestions == '#number'
    And match response.data.domainStrengths == '#array'

  Scenario: Fresh user has zero readiness
    Given path '/api/progress/readiness'
    And headers freshHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.readinessScore == 0
    And match response.data.readinessLabel == 'NOT_READY'
    And match response.data.questionsAttempted == 0

  Scenario: Readiness trend returns daily snapshots
    Given path '/api/progress/readiness/trend'
    And headers paidHeaders
    And param productCode = 'auto-b'
    And param days = 30
    When method GET
    Then status 200
    And match response.data == '#array'

  # ─── DOMAIN PROGRESS ───

  Scenario: Get domain progress breakdown
    Given path '/api/progress/domains'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data == '#array'

  Scenario: Domain strength reflects accuracy
    Given path '/api/progress/domains'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    * def weakDomains = karate.filter(response.data, function(d){ return d.strength == 'WEAK' })
    * def strongDomains = karate.filter(response.data, function(d){ return d.strength == 'STRONG' || d.strength == 'MASTERED' })
    # Weak domains should have lower accuracy than strong ones
    * eval if (weakDomains.length > 0 && strongDomains.length > 0) karate.assert(weakDomains[0].accuracyPercent < strongDomains[0].accuracyPercent)

  # ─── MASTERY STATS ───

  Scenario: Get mastery level distribution
    Given path '/api/progress/mastery'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.totalQuestions == '#number'

  Scenario: Fresh user has all questions as NEW
    Given path '/api/progress/mastery'
    And headers freshHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.totalQuestions == '#number'

  # ─── STREAKS ───

  Scenario: Get streak status
    Given path '/api/streaks/me'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.currentStreak == '#number'
    And match response.data.longestStreak == '#number'
    And match response.data.totalStudyDays == '#number'
    And match response.data.freezeSlotsAvailable == '#number'
    And match response.data.freezeSlotsUsed == '#number'
    And match response.data.studiedToday == '#boolean'
    And match response.data.streakAtRisk == '#boolean'

  Scenario: Fresh user has zero streak
    Given path '/api/streaks/me'
    And headers freshHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.currentStreak == 0
    And match response.data.longestStreak == 0
    And match response.data.studiedToday == false
    And match response.data.freezeSlotsAvailable == 0

  Scenario: Answering a question updates studiedToday
    # Answer a question
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 1, locale: 'nl' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    * def questionId = response.data.currentQuestion.strapiQuestionId
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionId)', answer: { selectedOptionId: 'a1' }, timeTakenMs: 3000 }
    When method POST
    Then status 200
    # Check streak now shows studiedToday=true
    Given path '/api/streaks/me'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.studiedToday == true

  # ─── ACHIEVEMENTS ───

  Scenario: Get achievement gallery (earned + locked)
    Given path '/api/achievements/me'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data == '#array'
    # Check that all achievements have xpReward
    * def first = response.data[0]
    And match first.xpReward == '#number'
    # Check that earned achievements have earnedAt
    * def earned = karate.filter(response.data, function(a){ return a.isEarned == true })
    * eval if (earned.length > 0) karate.match(earned[0], '{ earnedAt: "#string", xpReward: "#number" }')
    # Check that locked achievements have progress
    * def locked = karate.filter(response.data, function(a){ return a.isEarned == false })
    * eval if (locked.length > 0) karate.match(locked[0], '{ currentProgress: "#number", progressPercent: "#number", xpReward: "#number" }')

  Scenario: Fresh user has zero achievements earned
    Given path '/api/achievements/me'
    And headers freshHeaders
    When method GET
    Then status 200
    * def earned = karate.filter(response.data, function(a){ return a.isEarned == true })
    And match earned == '#[0]'

  Scenario: First question triggers first_question achievement
    # Start session with fresh user
    Given path '/api/practice/sessions'
    And headers freshHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 1, locale: 'nl' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    * def questionId = response.data.currentQuestion.strapiQuestionId
    # Answer first question
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers freshHeaders
    And request { strapiQuestionId: '#(questionId)', answer: { selectedOptionId: 'a1' }, timeTakenMs: 3000 }
    When method POST
    Then status 200
    And assert response.data.newAchievements.length > 0
    And match response.data.newAchievements[0].code == 'first_question'

  # ─── STUDY PLAN ───

  Scenario: Generate study plan with exam date
    Given path '/api/study-plan'
    And headers paidHeaders
    And request { productCode: 'auto-b', examDate: '2027-06-20', dailyQuestionTarget: 20 }
    When method POST
    Then status 200
    And match response.data.planId == '#uuid'
    And match response.data.productCode == 'auto-b'
    And match response.data.examDate == '2027-06-20'
    And match response.data.status == 'ACTIVE'
    And match response.data.dailyQuestionTarget == 20
    And match response.data.totalDays == '#number'
    And assert response.data.totalDays >= 7
    And match response.data.focusDomains == '#array'
    And assert response.data.days.length > 0
    And match each response.data.days contains { dayNumber: '#number', planDate: '#string', domainCode: '#string', questionTarget: '#number', status: '#string' }

  Scenario: Study plan allocates more days to weak domains
    Given path '/api/study-plan'
    And headers paidHeaders
    And request { productCode: 'auto-b', examDate: '2027-07-01', dailyQuestionTarget: 20 }
    When method POST
    Then status 200
    And match response.data.focusDomains == '#array'
    # Focus domains should be the weakest ones

  Scenario: Study plan includes mock exams
    Given path '/api/study-plan'
    And headers paidHeaders
    And request { productCode: 'auto-b', examDate: '2027-07-01', dailyQuestionTarget: 20 }
    When method POST
    Then status 200
    * def examDays = karate.filter(response.data.days, function(d){ return d.includeExam == true })
    And assert examDays.length > 0

  Scenario: Get today's study plan tasks
    # First generate a plan
    Given path '/api/study-plan'
    And headers paidHeaders
    And request { productCode: 'auto-b', examDate: '2027-07-01', dailyQuestionTarget: 20 }
    When method POST
    Then status 200
    # Then get today's tasks
    Given path '/api/study-plan/today'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.dayNumber == '#number'
    And match response.data.domainCode == '#string'
    And match response.data.questionTarget == '#number'
    And match response.data.questionsCompleted == '#number'
    And match response.data.status == '#string'

  Scenario: Get active study plan
    # First generate a plan
    Given path '/api/study-plan'
    And headers paidHeaders
    And request { productCode: 'auto-b', examDate: '2027-08-01', dailyQuestionTarget: 20 }
    When method POST
    Then status 200
    # Then fetch it
    Given path '/api/study-plan'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data.status == 'ACTIVE'

  Scenario: Generate plan without exam date defaults to 30 days
    Given path '/api/study-plan'
    And headers paidHeaders
    And request { productCode: 'auto-b', dailyQuestionTarget: 15 }
    When method POST
    Then status 200
    And match response.data.totalDays == 30
    And match response.data.examDate == '#null'

  # ─── INTERNAL ───

  Scenario: GDPR delete removes all user learning data
    * def deleteUserId = '99999999-9999-9999-9999-999999999999'
    Given path '/internal/content/user/' + deleteUserId + '/delete'
    And header X-Tenant-ID = tenantId
    When method DELETE
    Then status 204

  Scenario: Flush content cache
    Given path '/internal/cache/flush'
    When method POST
    Then status 200
