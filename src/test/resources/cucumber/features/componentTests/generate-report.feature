@Component
Feature: Test analytics for scan report operation

  Scenario Outline: do get results operation for a create scan.
    Given Mocked CxService environment with mocked services data
    When generate report and get report content from scan with <expectedScanID>
    Then we should see the scan result ID
    Examples:
      | expectedScanID  |
      | 123             |

