@contract
Feature: Contract verification — passtheo-content-service provider

  Background:
    * url baseUrl

  # ─── GET /internal/products/catalog — 200 with products ─────────────────────

  Scenario: GET /internal/products/catalog returns a flat product list
    Given path '/internal/products/catalog'
    And param locale = 'nl'
    When method GET
    Then status 200
    # Contract: { success: true, data: [{ code, name, productTypeCode, countryCode, active }] }
    And match response.success == true
    And match response.data == '#array'
    And match each response.data == { code: '#string', name: '#string', productTypeCode: '#string', countryCode: '#string', active: '#boolean' }
    # At least one product expected from the StrapiClient mock in KarateContractRunner.
    And match response.data[0].code == '#notnull'

  # ─── GET /internal/products/catalog — default locale ────────────────────────

  Scenario: GET /internal/products/catalog defaults locale to 'nl'
    Given path '/internal/products/catalog'
    When method GET
    Then status 200
    And match response.success == true
    And match response.data == '#array'
