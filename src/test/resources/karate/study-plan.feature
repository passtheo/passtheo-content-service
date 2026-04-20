Feature: Study plan generation with exam date fallback

  Background:
    * url baseUrl

  Scenario: Generate study plan without exam date uses user-profile fallback (30-day default)
    * header X-Tenant-ID = tenantId
    * header X-Keycloak-User-ID = userId
    * header X-User-Roles = learnerRole
    Given path '/api/study-plan'
    And request { productCode: 'auto-b', dailyQuestionTarget: 20 }
    When method post
    Then status 200
    And match response.success == true
    And match response.data.totalDays == '#number'
    And match response.data.status == 'ACTIVE'

  Scenario: Generate study plan with explicit exam date uses that date
    * header X-Tenant-ID = tenantId
    * header X-Keycloak-User-ID = userId
    * header X-User-Roles = learnerRole
    Given path '/api/study-plan'
    And request { productCode: 'auto-b', examDate: '#(futureDate)', dailyQuestionTarget: 20 }
    When method post
    Then status 200
    And match response.success == true
    And match response.data.totalDays == '#number'

  Scenario: Get active study plan returns plan with days
    * header X-Tenant-ID = tenantId
    * header X-Keycloak-User-ID = userId
    * header X-User-Roles = learnerRole
    Given path '/api/study-plan'
    And param productCode = 'auto-b'
    When method get
    Then status 200
    And match response.success == true
    And match response.data.days == '#array'

  Scenario: POST /preview returns a PREVIEW plan without persisting
    * header X-Tenant-ID = tenantId
    * header X-Keycloak-User-ID = userId
    * header X-User-Roles = learnerRole
    Given path '/api/study-plan/preview'
    And request { productCode: 'auto-b', examDate: '#(futureDate)' }
    When method post
    Then status 200
    And match response.success == true
    And match response.data.planId == '#null'
    And match response.data.status == 'PREVIEW'
    And match response.data.totalDays == '#number'
    And match response.data.dailyQuestionTarget == '#number'
    And match response.data.days == '#array'

  Scenario: POST /preview does not modify the active plan
    # `configure headers` makes the auth headers sticky for every request in this scenario.
    # `header X-Foo` is consumed by a single request, so a multi-request scenario needs this.
    * configure headers = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(userId)', 'X-User-Roles': '#(learnerRole)' }
    # Establish a baseline plan so we can prove the preview call leaves it untouched.
    Given path '/api/study-plan'
    And request { productCode: 'auto-b', examDate: '#(futureDate)', dailyQuestionTarget: 20 }
    When method post
    Then status 200
    * def baselinePlanId = response.data.planId
    * def baselineExamDate = response.data.examDate
    # Call preview with a different exam date
    Given path '/api/study-plan/preview'
    And request { productCode: 'auto-b', examDate: '#(farFutureDate)' }
    When method post
    Then status 200
    And match response.data.status == 'PREVIEW'
    And match response.data.planId == '#null'
    # The active plan must be unchanged. Note: the baseline's examDate matches the user-service
    # WireMock stub's examDate (see karate-config.js), so the reconcile-on-read path detects no
    # drift and the stored plan is returned as-is.
    Given path '/api/study-plan'
    And param productCode = 'auto-b'
    When method get
    Then status 200
    And match response.data.planId == baselinePlanId
    And match response.data.examDate == baselineExamDate
    And match response.data.status == 'ACTIVE'

  Scenario: GET /api/study-plan reconciles examDate drift from user-service profile
    # Uses a dedicated test userId with its own deterministic user-service stub
    # (KarateRunner.configureUserServiceMocks) that returns examDate=2026-05-15.
    * def driftUserId = '44444444-4444-4444-4444-444444444444'
    * configure headers = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(driftUserId)', 'X-User-Roles': '#(learnerRole)' }
    # Seed a plan with an examDate that clearly differs from the profile's. On GET the
    # service must detect the drift, regenerate against the authoritative profile
    # examDate, and return the fresh plan.
    Given path '/api/study-plan'
    And request { productCode: 'auto-b', examDate: '2030-12-31', dailyQuestionTarget: 20 }
    When method post
    Then status 200
    * def stalePlanId = response.data.planId
    And match response.data.examDate == '2030-12-31'

    Given path '/api/study-plan'
    And param productCode = 'auto-b'
    When method get
    Then status 200
    And match response.success == true
    And match response.data.examDate == '2026-05-15'
    And match response.data.planId != stalePlanId
    And match response.data.status == 'ACTIVE'
    * def reconciledPlanId = response.data.planId

    # Second GET is idempotent: stored examDate now matches the profile, no further regeneration.
    Given path '/api/study-plan'
    And param productCode = 'auto-b'
    When method get
    Then status 200
    And match response.data.examDate == '2026-05-15'
    And match response.data.planId == reconciledPlanId
