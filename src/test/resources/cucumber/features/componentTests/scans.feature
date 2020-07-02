@Component
Feature: Test analytics for get scan result operation

  Background:
    Given Mocked CxService environment with mocked services data

  Scenario Outline: do get results operation for a create scan.
    When create scan with <expectedScanID>
    Then we should see the scan result ID
    Examples:
      | expectedScanID  |
      | 123             |
