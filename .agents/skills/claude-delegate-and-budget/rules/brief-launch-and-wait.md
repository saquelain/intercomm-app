---
title: Launch in the Background, Then Wait
impact: HIGH
impactDescription: duplicated or predicted work wastes both runs
tags: brief, background, waiting, duplication
---

## Launch in the Background, Then Wait

**Impact: HIGH (duplicated or predicted work wastes both runs)**

Run subagents in the background by default so the user can interject and you can keep working on anything that does not depend on them. Go foreground only when your very next action needs the result and nothing else is useful in the meantime.

Once work is delegated:

- **Do not also do it yourself**, and do not touch the files it is editing. Two writers on one file is a merge conflict at best.
- **Never predict or fabricate its result.** If the user asks before it reports, say it is still running.
- **Continue the same agent** (send it a follow-up) when the next question builds on what it already read; a new agent starts from zero. One-shot explorers cannot be continued, so give them the whole question up front.
- **Isolate editing agents** (a worktree) when you will also be editing, so neither clobbers the other.
- **Verify before reporting done.** When an editing agent says it finished, read its diff and run the check yourself; its summary is a claim, and the user hears it from you.

**Incorrect (delegated, then redone and pre-announced):**

```text
Agent(prompt="Audit src/payments for unhandled promise rejections.", run_in_background=true)
Grep(pattern="\.then\(", path="src/payments")            # doing the same audit inline
"The audit will probably find the issue in checkout.ts."  # predicting the result
```

**Correct (delegated, other work continues, result awaited):**

```text
Agent(prompt="Audit src/payments for unhandled promise rejections. Return a file:line list, under 150 words.",
      run_in_background=true)
-> meanwhile: write the test harness the fix will need (different files)
-> on notification: read the list; verify the two pointers that drive edits
```

Reference: [Claude Code subagents: background subagents and resuming](https://code.claude.com/docs/en/sub-agents)
