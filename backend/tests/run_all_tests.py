"""
AETHER X - Automated Test Suite Runner
Discovers and executes all unit and live integration tests.
Reports summary of passes, failures, and live API connectivity.
"""

import os
import sys
import time
import unittest
from pathlib import Path

# Add backend directory to sys.path
backend_dir = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(backend_dir))

from tests.test_gemini_provider import TestGeminiProvider
from tests.test_provider_manager import TestProviderManager
from tests.test_agents import TestSpecializedAgents
from tests.test_security_and_health import TestSecurityAndHealth


def run_aether_test_suite():
    print("=" * 70)
    print("       AETHER X - AUTOMATED TEST SUITE & LIVE API VERIFICATION       ")
    print("=" * 70)

    loader = unittest.TestLoader()
    suite = unittest.TestSuite()

    suite.addTests(loader.loadTestsFromTestCase(TestSecurityAndHealth))
    suite.addTests(loader.loadTestsFromTestCase(TestProviderManager))
    suite.addTests(loader.loadTestsFromTestCase(TestSpecializedAgents))
    suite.addTests(loader.loadTestsFromTestCase(TestGeminiProvider))

    runner = unittest.TextTestRunner(verbosity=2)
    start_time = time.time()
    result = runner.run(suite)
    elapsed = time.time() - start_time

    print("\n" + "=" * 70)
    print(f"Total Tests Run : {result.testsRun}")
    print(f"Successes       : {result.testsRun - len(result.failures) - len(result.errors)}")
    print(f"Failures        : {len(result.failures)}")
    print(f"Errors          : {len(result.errors)}")
    print(f"Elapsed Time    : {elapsed:.2f}s")
    print("=" * 70)

    if result.wasSuccessful():
        print(">>> ALL TESTS PASSED SUCCESSFULLY! <<<")
        return 0
    else:
        print(">>> SOME TESTS FAILED! CHECK DETAILS ABOVE <<<")
        return 1


if __name__ == "__main__":
    exit_code = run_aether_test_suite()
    sys.exit(exit_code)
