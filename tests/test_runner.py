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

class TestState:
    chat_idx = 0

def run_test():
    state = TestState()
    print("--- Starting Happy Path Test ---")
    
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
        while True:
            line = process.stdout.readline()
            if not line:
                break
            
            print(line.strip())
            
            if "Flow unknown going to state Idle" in line:
                time.sleep(2)
                say("Wake up")

            elif ">>> ROBOT_LISTENING: INITIAL_INTERACTION" in line:
                time.sleep(1)
                say("Yes, I would like to try it.")
            
            elif ">>> ROBOT_LISTENING: CHOOSE_MODE" in line:
                time.sleep(1)
                say("I want to browse the ready-made cases.")
            
            elif ">>> ROBOT_LISTENING: BROWSE_PERSONAS" in line:
                time.sleep(1)
                say("I want to talk to Helmi.")
            
            elif ">>> ROBOT_LISTENING: MAIN_CHAT" in line:
                time.sleep(2)
                # Simulate clinical interview questions
                dialogue = [
                    "Hi Helmi, how are you feeling today?",
                    "It's okay to feel a bit nervous. Can you tell me what's been on your mind lately?",
                    "I see. How does that make you feel when you're at school?",
                    "Stop session."
                ]
                
                if state.chat_idx < len(dialogue):
                    say(dialogue[state.chat_idx])
                    state.chat_idx += 1
                else:
                    print("Dialogue finished.")
            
            elif ">>> ROBOT_LISTENING: AFTER_CHAT" in line:
                time.sleep(1)
                say("No, I am done for today.")
                time.sleep(2)
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
