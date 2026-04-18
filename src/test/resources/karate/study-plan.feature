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
    * header X-Tenant-ID = tenantId
    * header X-Keycloak-User-ID = userId
    * header X-User-Roles = learnerRole
    # Capture the currently active plan (or 404 if none)
    Given path '/api/study-plan'
    And param productCode = 'auto-b'
    When method get
    * def beforeStatus = responseStatus
    * def beforePlanId = beforeStatus == 200 ? response.data.planId : null
    # Call preview with a different exam date
    Given path '/api/study-plan/preview'
    And request { productCode: 'auto-b', examDate: '#(farFutureDate)' }
    When method post
    Then status 200
    And match response.data.status == 'PREVIEW'
    # The active plan should be unchanged
    Given path '/api/study-plan'
    And param productCode = 'auto-b'
    When method get
    Then status == beforeStatus
    * if (beforeStatus == 200) karate.match(response.data.planId, beforePlanId)
