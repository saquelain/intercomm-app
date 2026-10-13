---
title: Scale Effort to Stakes, Not to Length
impact: MEDIUM
impactDescription: effort buys depth on hard problems and only verbosity on easy ones
tags: effort, thinking, stakes, levels
---

## Scale Effort to Stakes, Not to Length

**Impact: MEDIUM (effort buys depth on hard problems and only verbosity on easy ones)**

Effort sets how deeply the model thinks and how many tool calls and words it spends. On a hard problem that is the difference between a shallow guess and the answer. On an easy one it adds exploration, preamble, and tool calls without changing the result. The stake is the cost of being wrong, not the size of the task: a 200-file rename is big and low-stakes; a 3-line change to token validation is small and high-stakes.

| Effort | Use for |
|--------|---------|
| `low` | Lookups, mechanical edits, running commands and relaying results, subagents with a narrow brief |
| `medium` | Routine features with a clear spec, docs, tests, first-pass review |
| `high` | Debugging, design decisions, reviews, anything the user will ship without re-reading |
| `xhigh` / `max` | Subtle correctness, security, a retry after a good brief failed at `high` (`model-cost-per-completed-task`) |

Set it per subagent call, where you know the stakes, or in a reusable agent file. Defaults differ by model (several current models default to `medium`), so do not assume a subagent is thinking hard unless you asked for it. The main thread's effort is the session setting, which is the user's (`effort-flag-session-mismatch`).

**Incorrect (a rename gets the same depth as a security review):**

```text
Agent(effort="max", prompt="Rename getUser -> fetchUser across src/; run tsc; return files + output.")
```

**Correct (effort follows the stake of each piece):**

```text
Agent(model="haiku", effort="low", prompt="Rename getUser -> fetchUser across src/; run tsc; return files + output.")
Agent(effort="xhigh", prompt="Review the src/auth/token.ts diff for validation bypasses. Return verdict + findings (file:line).")
```

Reference: [Claude Code model configuration: effort levels](https://code.claude.com/docs/en/model-config)
