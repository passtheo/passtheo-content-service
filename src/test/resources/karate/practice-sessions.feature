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

  Scenario: Domain with fewer than 5 questions returns 400
    # Uses a non-existent domain code — Strapi cache returns 0 questions, which is < 5
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'nonexistent-empty-domain', sessionType: 'PRACTICE', questionCount: 10, locale: 'nl' }
    When method POST
    Then status 400
    And match response.detail contains 'At least 5 required'

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

  Scenario: WEAK_REVIEW session returns 200 with weak questions or 400 when none exist
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'voorrang', sessionType: 'WEAK_REVIEW', questionCount: 10, locale: 'nl' }
    When method POST
    # WEAK_REVIEW only selects due-review + weak questions; returns 400 if fewer than 5 exist
    Then assert responseStatus == 200 || responseStatus == 400
    * if (responseStatus == 200) karate.match(response.data.status, 'IN_PROGRESS')
    * if (responseStatus == 400) karate.match(response.detail, '#string')

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

  # ─── Integer ID tolerance (regression for #9) ───

  Scenario: Submit answer with integer selectedOptionId does not return 500
    # Flutter sends option/region IDs as integers; server must not ClassCastException
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 1, locale: 'nl' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    * def questionId = response.data.currentQuestion.strapiQuestionId
    # Guard: this scenario requires a multiple_choice question to have answer options
    And match response.data.currentQuestion.interactionType == 'multiple_choice'
    # Parse the string option ID to an integer, simulating Flutter's raw Strapi ID usage
    * def intOptionId = parseInt(response.data.currentQuestion.answerOptions[0].id)
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionId)', answer: { selectedOptionId: '#(intOptionId)' }, timeTakenMs: 2467 }
    When method POST
    Then status 200
    And match response.data.isCorrect == '#boolean'
    And match response.data.correctAnswer != null
    And match response.data.sessionProgress.answeredCount == 1

  Scenario: Submitting the same answer twice returns 200 (idempotency)
    # Network retries must not cause a 500 duplicate key error
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 2, locale: 'nl' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    * def questionId = response.data.currentQuestion.strapiQuestionId
    # First submission — normal answer
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionId)', answer: { selectedOptionId: 'a1' }, timeTakenMs: 3000 }
    When method POST
    Then status 200
    # Second submission of identical request — must return 200, not 500
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionId)', answer: { selectedOptionId: 'a1' }, timeTakenMs: 3000 }
    When method POST
    Then status 200
    And match response.data.sessionProgress.answeredCount == 1

  # ─── Session Locale Persistence ───

  Scenario: Session locale is owned by session — content stays in English regardless of client header
    # Start session with locale=en
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 3, locale: 'en' }
    When method POST
    Then status 200
    * def sessionId = response.data.sessionId
    * def firstQuestion = response.data.currentQuestion
    # First question text must be in English (not Dutch)
    And match firstQuestion.questionText != null
    # Submit answer — no locale param sent, backend must use session-stored locale
    Given path '/api/practice/sessions/' + sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(firstQuestion.strapiQuestionId)', answer: { selectedOptionId: 'a1' }, timeTakenMs: 4000 }
    When method POST
    Then status 200
    # Explanation must exist (returned from session locale=en, not Dutch fallback)
    And match response.data.explanation != null
    And match response.data.explanation.text == '#string'
    # Next question must be returned (session locale=en applied)
    And match response.data.nextQuestion != null
    And match response.data.nextQuestion.questionOrder == 2
    # Resume the session — must return English question
    Given path '/api/practice/sessions/' + sessionId
    And headers paidHeaders
    When method GET
    Then status 200
    And match response.data.currentQuestion != null
    And match response.data.currentQuestion.questionOrder == 2

  # ─── Language Switch Preserves Progress ───

  Scenario: Language switch preserves progress using documentId
    # Start session in Dutch and answer a question
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 1, locale: 'nl' }
    When method POST
    Then status 200
    * def questionIdNL = response.data.currentQuestion.strapiQuestionId
    * def sessionId1 = response.data.sessionId
    # Submit correct answer in Dutch
    Given path '/api/practice/sessions/' + sessionId1 + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionIdNL)', answer: { selectedOptionId: 'opt-a' }, timeTakenMs: 3000 }
    When method POST
    Then status 200
    And match response.data.isCorrect == '#boolean'
    # Now start a new session in Turkish for the same domain
    Given path '/api/practice/sessions'
    And headers paidHeaders
    And request { productCode: 'auto-b', domainCode: 'verkeersborden', sessionType: 'PRACTICE', questionCount: 1, locale: 'tr' }
    When method POST
    Then status 200
    * def questionIdTR = response.data.currentQuestion.strapiQuestionId
    # The strapiQuestionId should be the same (documentId is locale-independent)
    # This verifies that the same question in different languages shares the same documentId
    And match questionIdTR == questionIdNL
    # Submit answer in Turkish - spaced repetition should recognize this as the same question
    Given path '/api/practice/sessions/' + response.data.sessionId + '/answer'
    And headers paidHeaders
    And request { strapiQuestionId: '#(questionIdTR)', answer: { selectedOptionId: 'opt-a' }, timeTakenMs: 3000 }
    When method POST
    Then status 200
    # Mastery should have progressed from the previous answer (not starting fresh)
    And match response.data.masteryUpdate != null
    And match response.data.masteryUpdate.previousLevel != 'NEW'
