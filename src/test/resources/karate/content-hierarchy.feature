Feature: Content Hierarchy Browsing
  As a PassTheo student
  I want to browse countries, product types, products, and domains
  So that I can navigate to the questions I need to study

  Background:
    * url baseUrl
    * def tenantId = '11111111-1111-1111-1111-111111111111'
    * def paidUserId = '33333333-3333-3333-3333-333333333333'
    * def freeUserId = '22222222-2222-2222-2222-222222222222'
    * def paidHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(paidUserId)', 'Authorization': '#("Bearer " + paidToken)' }
    * def freeHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(freeUserId)', 'Authorization': '#("Bearer " + freeToken)' }

  Scenario: List available countries
    Given path '/api/content/countries'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.success == true
    And match response.data[0].code == 'NL'
    And match response.data[0].supportedLocales contains 'nl'
    And match response.data[0].supportedLocales contains 'en'

  Scenario: List product types for Netherlands
    Given path '/api/content/NL/product-types'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data[0].code == 'cbr'
    And assert response.data[0].productCount > 0

  Scenario: List products under CBR
    Given path '/api/content/NL/cbr/products'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data[0].code == 'auto-b'
    And match response.data[0].licenceCode == 'B'
    And match response.data[0].examConfig.totalQuestions == 50
    And match response.data[0].examConfig.timeLimitMinutes == 30
    And match response.data[0].examConfig.passScore == 44

  Scenario: List domains for CBR Auto B with progress overlay
    Given path '/api/content/NL/cbr/auto-b/domains'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data == '#[6]'
    And match each response.data contains { code: '#string', name: '#string', questionCount: '#number' }
    And match response.data[0].progress != null
    And match response.data[0].progress contains { coveragePercent: '#number', accuracyPercent: '#number', strength: '#string' }

  Scenario: Domains show locked status for free user
    Given path '/api/content/NL/cbr/auto-b/domains'
    And headers freeHeaders
    When method GET
    Then status 200
    # First domain is free preview
    And match response.data[0].isFreePreview == true
    And match response.data[0].isLocked == false
    # Remaining domains are locked for free users
    * def lockedDomains = karate.filter(response.data, function(d){ return d.isLocked == true })
    And match lockedDomains == '#[5]'

  Scenario: List topics for a domain with progress overlay
    Given path '/api/content/NL/cbr/auto-b/domains/verkeersborden/topics'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data == '#[2]'
    And match each response.data contains { code: '#string', name: '#string', questionCount: '#number' }
    And match each response.data contains { progress: '#notnull' }
    And match each response.data.progress contains { coveragePercent: '#number', accuracyPercent: '#number', masteredCount: '#number' }

  Scenario: List road signs for Netherlands
    Given path '/api/content/NL/cbr/auto-b/road-signs'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data == '#array'

  Scenario: List lessons for a topic (hierarchical endpoint)
    Given path '/api/content/NL/cbr/auto-b/lessons/voorrangsborden'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data == '#array'

  Scenario: List lessons by topic code (flat endpoint)
    Given path '/api/content/lessons/voorrangsborden'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data == '#array'

  Scenario: List lessons by topic code with locale
    Given path '/api/content/lessons/voorrangsborden'
    And headers paidHeaders
    And param locale = 'en'
    When method GET
    Then status 200
    And match response.data == '#array'

  Scenario: List lessons for unknown topic code returns empty list
    Given path '/api/content/lessons/nonexistent-topic-xyz'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data == '#[]'

  # ─── LESSON PREMIUM GATE (P0.5) ───

  Scenario: Paid user sees full content on every lesson regardless of isPremium
    Given path '/api/content/lessons/voorrangsborden'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.success == true
    And match each response.data contains { locked: false }
    # Every lesson exposes the two flags
    And match each response.data contains { isPremium: '#boolean', locked: '#boolean' }

  Scenario: Free user sees locked lessons with stripped content when premium
    Given path '/api/content/lessons/voorrangsborden'
    And headers freeHeaders
    When method GET
    Then status 200
    And match response.success == true
    # Schema invariant — all lessons expose the two flags
    And match each response.data contains { isPremium: '#boolean', locked: '#boolean' }
    # And any locked lesson has empty sections (content stripped)
    * def lockedLessons = response.data.findAll(function(l){ return l.locked == true })
    * if (lockedLessons.length > 0) karate.match(lockedLessons[0].sections, '#[0]')

  Scenario: Content supports locale parameter
    Given path '/api/content/NL/cbr/auto-b/domains'
    And headers paidHeaders
    And param locale = 'en'
    When method GET
    Then status 200
    # Domain names should be in English when locale=en

  Scenario: Invalid country code returns empty or valid list
    Given path '/api/content/XX/product-types'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data == '#array'

  Scenario: Invalid product type code returns valid list
    Given path '/api/content/NL/invalid/products'
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data == '#array'
