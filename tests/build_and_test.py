import subprocess
import os
import sys
import time

# Ensure we are in the project root
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(PROJECT_ROOT)

print("=== STARTING PSYCHIATRY SIMULATION TEST SUITE ===")

# 1. Build the project
print("\nBuilding project...")
build_process = subprocess.run(["./gradlew", "classes"], cwd=PROJECT_ROOT)
if build_process.returncode != 0:
    print("Build failed. Exiting.")
    sys.exit(1)

# 2. Run Happy Path
print("\n--- Running HAPPY PATH tests ---")
subprocess.run(["python3", "tests/test_runner.py"], cwd=PROJECT_ROOT)

# 3. Run Unhappy Path
print("\n--- Running UNHAPPY PATH tests ---")
subprocess.run(["python3", "tests/test_error_paths.py"], cwd=PROJECT_ROOT)

print("\n=== ALL TESTS COMPLETED ===")
