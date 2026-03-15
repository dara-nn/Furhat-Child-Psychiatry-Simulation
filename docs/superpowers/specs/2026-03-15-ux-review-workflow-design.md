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

The path is always resolved relative to the git repository root (using `git rev-parse --show-toplevel`), so the script works correctly regardless of which directory the developer runs `git commit` from.

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
- A **short summary** at the top using one of three labels, always including a problem count:
  - `PASS — 0 problems`
  - `CONCERNS — 1 problem`
  - `ISSUES FOUND — 2 problems`
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
The review script. It must begin with `#!/usr/bin/env bash` (a shebang line that tells the system how to run it). It:
- Resolves the repo root using `git rev-parse --show-toplevel` so paths work from any directory
- Finds all `.kt` files under `flow/` (relative to the repo root)
- Concatenates them into a single string, each prefixed with a `--- FILE: <path> ---` header for context
- Builds the full prompt string by combining the review instructions and the file content, then pipes it to Claude:
  ```bash
  printf '%s\n\n%s' "$PROMPT" "$FILE_CONTENTS" | claude
  ```
  (`printf` is used instead of `echo` to ensure newlines are inserted correctly on macOS)
- Prints the review output
- Prompts the developer (terminal only): **"Commit anyway? (y/n)"** — reads response from `/dev/tty`
- Exits with code `0` (allow commit) or `1` (block commit) based on the response

> **Note:** The interactive prompt requires a terminal. If you commit from a GUI git client (e.g. VS Code Source Control, GitKraken), the prompt cannot display. In that case, use `git commit --no-verify` to skip the hook, or commit from the terminal.

### `scripts/install-hooks.sh`
A one-time setup script. It must begin with `#!/usr/bin/env bash`. It:
- Removes any existing `.git/hooks/pre-commit` file first (to avoid conflicts)
- **Copies** (not symlinks) `scripts/pre-commit-review.sh` to `.git/hooks/pre-commit`
- Makes both scripts executable with `chmod +x`
- Prints confirmation: "Hook installed. UX review will run on every commit."

> Option A uses a copy so the hook works without any path dependency. This means if `pre-commit-review.sh` is updated later, `install-hooks.sh` must be re-run to pick up the changes.

---

## Setup Instructions (for any developer)

Run once, from the project root:
```bash
./scripts/install-hooks.sh
```

After that, the review runs automatically on every `git commit`. No further action needed.

---

## Upgrading to Option B (Shareable Hook)

This design uses Option A (hook installed locally via a copy). To upgrade to Option B:
- `scripts/pre-commit-review.sh` is already tracked in git (done as part of Option A implementation)
- Remove any existing `.git/hooks/pre-commit` file, then change `install-hooks.sh` to create a symlink instead of a copy:
  ```bash
  rm -f .git/hooks/pre-commit
  ln -s "$(git rev-parse --show-toplevel)/scripts/pre-commit-review.sh" .git/hooks/pre-commit
  ```
- Any future updates to the script are then immediately picked up — no need to re-run the install script
- Add a note to the project README: "Run `./scripts/install-hooks.sh` once after cloning"

---

## Claude Review Prompt

The full prompt sent to Claude (file contents appended after `Files:`) is:

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
- Start with a one-line summary using exactly one of these labels and always including a problem count:
    PASS — 0 problems
    CONCERNS — N problem(s)
    ISSUES FOUND — N problem(s)
- Then list each problem with: the state/screen it occurs in, what the problem is, and why it matters for the user's experience.
- If no problems are found, say so clearly and use the PASS label.

Files:
[CONCATENATED FILE CONTENTS APPENDED HERE BY THE SCRIPT]
```

---

## Constraints

- Requires `claude` CLI to be installed and authenticated on the developer's machine
- Does not call any external API directly — uses the existing Claude Code CLI session
- Does not affect the build, test, or deploy pipeline
- Designed for terminal use; interactive `y/n` prompt does not work in GUI git clients (use `git commit --no-verify` to bypass)

## Failure Handling

The hook must not silently break commits. The following failure modes are handled explicitly:

| Situation | Behaviour |
|---|---|
| `claude` is not found on PATH | Print warning: "⚠ Claude CLI not found — skipping UX review." Allow commit (exit 0). |
| `claude` exits with a non-zero error code | Print warning: "⚠ UX review failed — Claude returned an error. Skipping." Allow commit (exit 0). |
| `claude` is found but not authenticated | Claude CLI will print its own auth error; the hook treats this as a non-zero exit and falls back to the warning above. |
| No `.kt` files found in `flow/` | Print warning: "⚠ No flow files found to review." Allow commit (exit 0). |
| File content too large for context window | Claude CLI will return an error; the hook falls back to the non-zero exit handling above. Note: at the current codebase size (~6 flow files), this is not a concern. |

In all failure cases the hook **fails open** (allows the commit) rather than blocking work.
