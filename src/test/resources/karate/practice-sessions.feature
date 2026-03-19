Feature: Practice Sessions
  As a PassTheo student
  I want to start practice sessions, answer questions, and see feedback
  So that I can learn and improve my CBR theory knowledge

  Background:
    * url baseUrl
    * def tenantId = '11111111-1111-1111-1111-111111111111'
    * def paidUserId = '33333333-3333-3333-3333-333333333333'
    * def freeUserId = '22222222-2222-2222-2222-222222222222'
    * def paidHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(paidUserId)', 'Authorization': '#("Bearer " + paidToken)' }
    * def freeHeaders = { 'X-Tenant-ID': '#(tenantId)', 'X-Keycloak-User-ID': '#(freeUserId)', 'Authorization': '#("Bearer " + freeToken)' }

  # ─── Start Session ───

  Scenario: Paid user starts practice session for a domain
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 10, locale: 'nl' }
    When method POST
    Then status 200
    And match response.data.sessionId == '#uuid'
    And match response.data.status == 'IN_PROGRESS'
    And match response.data.totalQuestions == 10
    And match response.data.answeredCount == 0
    And match response.data.currentQuestion != null
    And match response.data.currentQuestion.strapiQuestionId == '#string'
    And match response.data.currentQuestion.questionText == '#string'
    And match response.data.currentQuestion.interactionType == '#string'
    And match response.data.currentQuestion.questionOrder == 1

  Scenario: Paid user starts session for all domains (mixed)
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: null, sessionType: 'PRACTICE', questionCount: 15, locale: 'nl' }
    When method POST
    Then status 200
    And match response.data.totalQuestions == 15

  Scenario: Free user starts session for free preview domain
    Given path '/api/practice/sessions'
    And headers freeHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 10, locale: 'nl' }
    When method POST
    Then status 200
    And match response.data.status == 'IN_PROGRESS'

  Scenario: Free user blocked from locked domain
    Given path '/api/practice/sessions'
    And headers freeHeaders
    And request { productCode: 'auto-b', domainCode: 'snelheid', sessionType: 'PRACTICE', questionCount: 10, locale: 'nl' }
    When method POST
    Then status 403
    And match response.title == '#string'

  Scenario: Session returns correct question types
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 10, locale: 'nl' }
    When method POST
    Then status 200
    And match response.data.currentQuestion.interactionType == '#? _ == "multiple_choice" || _ == "yes_no" || _ == "fill_in_number" || _ == "tap_on_image" || _ == "drag_checkmark" || _ == "drag_numbers"'

  Scenario: WEAK_REVIEW session prioritizes struggling questions
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'voorrang', sessionType: 'WEAK_REVIEW', questionCount: 10, locale: 'nl' }
    When method POST
    Then status 200
    And match response.data.status == 'IN_PROGRESS'

  # ─── Submit Answer ───

  Scenario: Submit correct multiple choice answer
    # Start session first
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 5, locale: 'nl' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    * def questionId = response.data.currentQuestion.strapiQuestionId
    * def correctOption = response.data.currentQuestion.answerOptions[0].id
    # Submit answer
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionId)', answer: { selectedOptionId: '#(correctOption)' }, timeTakenMs: 5000 }
    When method POST
    Then status 200
    And match response.data.isCorrect == '#boolean'
    And match response.data.correctAnswer != null
    And match response.data.explanation != null
    And match response.data.explanation.text == '#string'
    And match response.data.masteryUpdate != null
    And match response.data.masteryUpdate.previousLevel == '#string'
    And match response.data.masteryUpdate.newLevel == '#string'
    And match response.data.sessionProgress.answeredCount == 1
    And match response.data.sessionProgress.totalQuestions == 5

  Scenario: Submit answer returns next question
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 5, locale: 'nl' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    * def questionId = response.data.currentQuestion.strapiQuestionId
    # Submit first answer
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionId)', answer: { selectedOptionId: 'a1' }, timeTakenMs: 3000 }
    When method POST
    Then status 200
    And match response.data.nextQuestion != null
    And match response.data.nextQuestion.questionOrder == 2
    And match response.data.nextQuestion.strapiQuestionId != questionId

  Scenario: Last answer returns null nextQuestion
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
    And match response.data.nextQuestion == '#null'

  # ─── Resume + Complete ───

  Scenario: Resume session after app close
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 5, locale: 'nl' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    # Close app, come back
    Given path '/api/practice/sessions/' + sessionId
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data.sessionId == sessionId
    And match response.data.status == 'IN_PROGRESS'
    And match response.data.currentQuestion != null

  Scenario: Complete session returns summary
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 1, locale: 'nl' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    * def questionId = response.data.currentQuestion.strapiQuestionId
    # Answer the only question
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionId)', answer: { selectedOptionId: 'a1' }, timeTakenMs: 3000 }
    When method POST
    Then status 200
    # Complete session
    Given path '/api/practice/sessions/' + sessionId + '/complete'
    And headers paidHeaders
    When method POST
    Then status 200
    And match response.data.status == 'COMPLETED'
    And match response.data.totalQuestions == 1
    And match response.data.accuracyPercent == '#number'
    And assert response.data.timeSpentSeconds >= 0
    And match response.data.masteryChanges != null
    And match response.data.streakUpdate != null
    And assert response.data.streakUpdate.currentStreak >= 0
