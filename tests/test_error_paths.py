import subprocess
import os
import sys
import time
import signal

# Configuration
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VOICE = "Samantha"

def say(text):
    print(f"TEST_AGENT: Saying '{text}'")
    subprocess.run(["say", "-v", VOICE, text])

def run_test():
    print("--- Starting Unhappy Path Test ---")
    
    # Kill any existing Furhat processes
    subprocess.run(["pkill", "-f", "furhatos.app.openaichat.MainKt"], capture_output=True)
    
    # Start the skill
    process = subprocess.Popen(
        ["./gradlew", "run"],
        cwd=PROJECT_ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1
    )

    assert process.stdout is not None, "Failed to capture stdout."

    try:
        no_response_count = 0
        while True:
            line = process.stdout.readline()
            if not line:
                break
            
            print(line.strip())
            
            if "Flow unknown going to state Idle" in line:
                time.sleep(2)
                say("Wake up")
            
            elif ">>> ROBOT_LISTENING: INITIAL_INTERACTION" in line:
                if no_response_count == 0:
                    print("TEST_AGENT: Simulating SILENCE...")
                    time.sleep(12)  # Wait longer than the robot's timeout
                    no_response_count += 1
                else:
                    say("Yes please.")
            
            elif ">>> ROBOT_LISTENING: CHOOSE_MODE" in line:
                say("I want to create a custom case.")
            
            elif ">>> ROBOT_LISTENING: DESCRIBE_CASE" in line:
                say("I want a depressed 15-year-old who is failing school.")
                # Wait for generation and transition to MAIN_CHAT
                time.sleep(10)

            elif ">>> ROBOT_LISTENING: MAIN_CHAT" in line:
                say("Exit simulation.")
                break
            
            elif ">>> ROBOT_LISTENING: AFTER_CHAT" in line:
                say("Goodbye.")
                break

    except KeyboardInterrupt:
        print("\nStopping...")
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
        subprocess.run(["pkill", "-f", "furhatos.app.openaichat.MainKt"], capture_output=True)

if __name__ == "__main__":
    run_test()
