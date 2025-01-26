Feature: Open company templates resume page and select company info

  Scenario: select company info and navigate to second page
    Given open chrome browser and access to company page
    When select company info
    And take screenshot
    And click next button
    Then second page display