---
title: Slow Down Before Irreversible Steps and After Repeated Failures
impact: MEDIUM
impactDescription: the moments where a shallow answer is most expensive are predictable
tags: effort, irreversible, retries, verification
---

## Slow Down Before Irreversible Steps and After Repeated Failures

**Impact: MEDIUM (the moments where a shallow answer is most expensive are predictable)**

Some moments deserve more thinking than the surrounding work, whatever the session setting. Treat these as a signal to step up:

- The next step is hard to undo: deleting data or files, migrations, force pushes, publishing, changing auth, payments, or permissions, editing production config.
- The same fix has failed twice. A third try at the same depth is a loop; the model of the problem is wrong, not the typing.
- The evidence pattern-matches a known failure but the cause is unconfirmed. Pattern matches are where confident wrong actions come from.
- A subagent's confident finding will drive an edit you cannot easily check.

Stepping up means different things depending on who does the work:

- **Inline**: write down what each failure rules out, re-read the evidence, confirm the cause with one targeted check, and ask the user before anything destructive. If the session effort is low, say so (`effort-flag-session-mismatch`); the user can raise it or type `ultrathink` for one deep turn.
- **Subagent**: pass a higher `effort` on that one call, and widen the brief to the open question instead of the failed fix.

**Incorrect (third attempt at the same depth, then a destructive step on a hunch):**

```text
fix #1 fails -> fix #2 fails -> fix #3 (same idea, different line) -> "probably stale cache" -> rm -rf .cache
```

**Correct (failure pattern triggers a step up and a re-check):**

```text
fix #2 fails -> stop. List what each failure rules out; re-read the failing assertion and the code path it
  exercises -> confirm the cause with one targeted test -> then act (ask first if destructive)
```

Reference: [Claude Code model configuration: effort and ultrathink](https://code.claude.com/docs/en/model-config)
