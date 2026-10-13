---
title: Narrow Brief, Low Effort
impact: MEDIUM
impactDescription: high effort on a narrow brief buys scope creep, not accuracy
tags: effort, brief, subagent, scope
---

## Narrow Brief, Low Effort

**Impact: MEDIUM (high effort on a narrow brief buys scope creep, not accuracy)**

Effort and brief width go together. A precise brief (what, where, done, return shape) has already done the thinking; the agent only has to execute, and at low or medium effort it does so with fewer tool calls, less preamble, and a shorter summary. The same brief at high effort tends to wander outside its scope, re-derive what you told it, and return more than you asked for.

The pairings that work:

- **Narrow brief + low effort + cheap model**: inventories, mechanical edits, running checks.
- **Wide brief + high effort + strong model**: "find out why", "design", "review for anything wrong".

The pairings that waste: narrow + high (wandering), and wide + low (a confident shallow answer to an open question). If you find yourself wanting high effort on a narrow task, the brief is probably hiding an open question; surface it and decide whether it is yours to answer.

**Incorrect (narrow task, high effort):**

```text
Agent(effort="high", prompt="Run pnpm test in packages/api and tell me which tests fail.")
```

**Correct (narrow task, low effort):**

```text
Agent(model="haiku", effort="low",
      prompt="Run pnpm test in packages/api. Return: failing test names and the first stack frame of each. Nothing else.")
```
