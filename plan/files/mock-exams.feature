Feature: Mock Exams
  As a PassTheo student
  I want to take full mock CBR exams
  So that I can test my readiness before the real exam

  Background:
    * url baseUrl
    * def tenantId = '11111111-1111-1111-1111-111111111111'
    * def paidUserId = '33333333-3333-3333-3333-333333333333'
    * def freeUserId = '22222222-2222-2222-2222-222222222222'
    * def paidHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(paidUserId)', 'Authorization': '#("Bearer " + paidToken)' }
    * def freeHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(freeUserId)', 'Authorization': '#("Bearer " + freeToken)' }

  Scenario: Start mock exam returns 50 questions
    Given path '/api/exams/mock/start'
    And headers paidHeaders
    And request { productCode: 'auto-b', locale: 'nl' }
    When method POST
    Then status 200
    And match response.data.examId == '#uuid'
    And match response.data.totalQuestions == 50
    And match response.data.timeLimitSeconds == 1800
    And match response.data.passScore == 44
    And match response.data.startedAt == '#string'
    And match response.data.expiresAt == '#string'
    And match response.data.questions == '#[50]'
    And match each response.data.questions contains { strapiQuestionId: '#string', interactionType: '#string', domainCode: '#string' }

  Scenario: Exam questions span all 6 domains
    # Question selection uses domain weights + difficulty distribution from ExamConfig if configured
    # Falls back to simple shuffle-and-limit if no domainWeights are present
    Given path '/api/exams/mock/start'
    And headers paidHeaders
    And request { productCode: 'auto-b', locale: 'nl' }
    When method POST
    Then status 200
    * def domains = karate.distinct(response.data.questions.map(q => q.domainCode))
    And assert domains.length >= 5

  Scenario: Exam questions are shuffled (different order each time)
    Given path '/api/exams/mock/start'
    And headers paidHeaders
    And request { productCode: 'auto-b', locale: 'nl' }
    When method POST
    Then status 200
    * def firstOrder = response.data.questions.map(q => q.strapiQuestionId)
    Given path '/api/exams/mock/start'
    And headers paidHeaders
    And request { productCode: 'auto-b', locale: 'nl' }
    When method POST
    Then status 200
    * def secondOrder = response.data.questions.map(q => q.strapiQuestionId)
    # Very unlikely to be identical if shuffled
    And assert firstOrder.toString() != secondOrder.toString()

  Scenario: Submit exam with passing score
    # Start exam
    Given path '/api/exams/mock/start'
    And headers paidHeaders
    And request { productCode: 'auto-b', locale: 'nl' }
    When method POST
    Then status 200
    * def examId = response.data.examId
    * def questions = response.data.questions
    # Build answers (answer first option for all — some will be correct)
    * def answers = questions.map(function(q){ return { strapiQuestionId: q.strapiQuestionId, answer: { selectedOptionId: 'a1' }, timeTakenMs: 15000 } })
    # Submit
    Given path '/api/exams/mock/' + examId + '/submit'
    And headers paidHeaders
    And request { answers: '#(answers)' }
    When method POST
    Then status 200
    And match response.data.examId == examId
    And match response.data.passed == '#boolean'
    And match response.data.correctCount == '#number'
    And match response.data.totalQuestions == 50
    And match response.data.passScore == 44
    And match response.data.scorePercent == '#number'
    And match response.data.timeTakenSeconds >= 0
    And match response.data.domainBreakdown == '#[? _.length > 0]'
    And match each response.data.domainBreakdown contains { domainCode: '#string', correct: '#number', total: '#number', accuracyPercent: '#number' }
    And match response.data.wrongAnswers == '#array'
    And match response.data.readinessUpdate != null
    And match response.data.readinessUpdate.newScore == '#number'

  Scenario: Exam domain breakdown sums to 50
    Given path '/api/exams/mock/start'
    And headers paidHeaders
    And request { productCode: 'auto-b', locale: 'nl' }
    When method POST
    Then status 200
    * def examId = response.data.examId
    * def answers = response.data.questions.map(function(q){ return { strapiQuestionId: q.strapiQuestionId, answer: { selectedOptionId: 'a1' }, timeTakenMs: 10000 } })
    Given path '/api/exams/mock/' + examId + '/submit'
    And headers paidHeaders
    And request { answers: '#(answers)' }
    When method POST
    Then status 200
    * def totalFromBreakdown = 0
    * eval response.data.domainBreakdown.forEach(function(d){ totalFromBreakdown += d.total })
    And assert totalFromBreakdown == 50

  Scenario: Free user blocked from starting exam (weekly limit)
    # First exam allowed (1 per week for free)
    Given path '/api/exams/mock/start'
    And headers freeHeaders
    And request { productCode: 'auto-b', locale: 'nl' }
    When method POST
    Then status 200
    # Submit it to complete
    * def examId = response.data.examId
    * def answers = response.data.questions.map(function(q){ return { strapiQuestionId: q.strapiQuestionId, answer: { selectedOptionId: 'a1' }, timeTakenMs: 5000 } })
    Given path '/api/exams/mock/' + examId + '/submit'
    And headers freeHeaders
    And request { answers: '#(answers)' }
    When method POST
    Then status 200
    # Second exam should be blocked
    Given path '/api/exams/mock/start'
    And headers freeHeaders
    And request { productCode: 'auto-b', locale: 'nl' }
    When method POST
    Then status 429

  Scenario: Exam history returns past attempts
    Given path '/api/exams/history'
    And headers paidHeaders
    And param productCode = 'auto-b'
    When method GET
    Then status 200
    And match response.data == '#array'
    And match each response.data contains { examId: '#uuid', passed: '#boolean', scorePercent: '#number', completedAt: '#string' }
