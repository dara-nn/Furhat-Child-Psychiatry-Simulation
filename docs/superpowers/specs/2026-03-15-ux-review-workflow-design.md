# UX Review Workflow — Design Spec
**Date:** 2026-03-15
**Project:** Furhat Child Psychiatry Simulation

---

## Goal

Every time a code change is saved (committed) locally, automatically have Claude review the conversation flow files and report any UX problems — such as dead ends, missing silence handling, or unclear user instructions — before the commit is finalised.

---

## Trigger

- **When:** Every local `git commit`
- **How:** A git pre-commit hook (a script git runs automatically before saving the commit)
- **Where it runs:** On the developer's local machine only; not on any server or shared system

---

## Scope of Review

Claude reads **all `.kt` files inside the `flow/` directory** on every commit, regardless of which files changed. This ensures the full conversation is always reviewed as a whole, not just the parts that were recently edited.

Files reviewed:
- `src/main/kotlin/furhatos/app/openaichat/flow/` (all `.kt` files, recursively)

---

## What Claude Checks

Claude acts as a UX reviewer for a voice-based conversation system and looks for:

1. **Dead ends** — conversation screens with no exit path (user is permanently stuck)
2. **Missing silence handling** — steps where the user saying nothing is not handled
3. **Unclear user instructions** — Furhat does not tell the user what they can say next
4. **Unrecognised response recovery** — if a user says something unexpected, does the conversation recover gracefully or break?
5. **Navigation gaps** — can the user always return to a previous step if they change their mind?

---

## Report Format

Claude returns:
- A **short summary** at the top (overall pass/concern/fail and how many issues were found)
- **Detailed explanation** for each issue: what the problem is, where it occurs, and why it matters for the user's experience

---

## Developer Experience

1. Developer runs `git commit` as normal
2. The review script runs automatically — no extra command needed
3. Claude's report is printed in the terminal
4. The developer is prompted: **"Commit anyway? (y/n)"**
   - `y` — commit is saved as normal
   - `n` — commit is cancelled; developer can fix issues and try again

---

## Files to Create

### `scripts/pre-commit-review.sh`
The review script. It:
- Finds all `.kt` files under `flow/`
- Concatenates them with file path headers for context
- Sends them to Claude CLI (`claude -p "<prompt>"`) with the UX review instructions
- Prints the review output
- Prompts the developer to commit or abort
- Exits with code `0` (allow commit) or `1` (block commit) based on the response

### `scripts/install-hooks.sh`
A one-time setup script. It:
- Creates a `.git/hooks/pre-commit` file that calls `scripts/pre-commit-review.sh`
- Makes both scripts executable
- Prints confirmation: "Hook installed. UX review will run on every commit."

---

## Setup Instructions (for any developer)

Run once, from the project root:
```bash
./scripts/install-hooks.sh
```

After that, the review runs automatically on every `git commit`. No further action needed.

---

## Upgrading to Option B (Shareable Hook)

This design uses Option A (hook installed locally). To upgrade to Option B:
- Move `scripts/pre-commit-review.sh` to a committed location (already done — it is tracked in git)
- Update `install-hooks.sh` to symlink rather than copy (one-line change)
- Add a note to the project README: "Run `./scripts/install-hooks.sh` once after cloning"

---

## Claude Review Prompt

The prompt sent to Claude will be:

```
You are a UX reviewer for a voice-based conversation assistant built on the Furhat robot platform.
The assistant is used by child psychiatry trainees to practise clinical interviews with AI-powered child patient personas.

Review the following Kotlin conversation flow files. For each screen/state in the conversation, check:
1. Dead ends — is there any state the user can reach with no way to continue or exit?
2. Missing silence handling — does each state handle the case where the user says nothing (onNoResponse)?
3. Unclear prompts — does Furhat always tell the user what they can say or do next?
4. Unrecognised response recovery — if the user says something unexpected, does the conversation recover gracefully?
5. Navigation gaps — can the user always go back or change their mind at key decision points?

Output format:
- Start with a one-line summary: PASS, CONCERNS, or ISSUES FOUND, with a count of problems.
- Then list each problem with: the state/screen it occurs in, what the problem is, and why it matters for the user's experience.
- If no problems are found, say so clearly.

Files:
```

---

## Constraints

- Requires `claude` CLI to be installed and authenticated on the developer's machine
- Does not call any external API directly — uses the existing Claude Code CLI session
- Does not affect the build, test, or deploy pipeline
- Does not block commits when Claude CLI is unavailable (fails open with a warning)
