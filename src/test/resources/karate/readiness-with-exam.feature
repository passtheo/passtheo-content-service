Feature: Readiness score includes exam countdown and predicted ready date

  Background:
    * url baseUrl

  Scenario: Readiness response includes examCountdownDays and predictedReadyDate fields
    * header X-Tenant-ID = tenantId
    * header X-Keycloak-User-ID = userId
    * header X-User-Roles = learnerRole
    Given path '/api/progress/readiness'
    And param productCode = 'auto-b'
    When method get
    Then status 200
    And match response.success == true
    And match response.data.readinessScore == '#number'
    And assert response.data.readinessScore >= 0 && response.data.readinessScore <= 100
    And match response.data.readinessLabel == '#string'
    And match response.data.examCountdownDays == '##number'
    And match response.data.predictedReadyDate == '##string'

  Scenario: Readiness examCountdownDays is null when user has no exam date
    * header X-Tenant-ID = tenantId
    * header X-Keycloak-User-ID = userWithoutExamDate
    * header X-User-Roles = learnerRole
    Given path '/api/progress/readiness'
    And param productCode = 'auto-b'
    When method get
    Then status 200
    And match response.data.examCountdownDays == '##number'
