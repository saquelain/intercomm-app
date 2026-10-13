---
title: Inherit by Default, Downgrade Only for Bounded Work
impact: MEDIUM
impactDescription: a downgrade is safe only when a wrong answer is cheap to catch
tags: model, inherit, downgrade, upgrade
---

## Inherit by Default, Downgrade Only for Bounded Work

**Impact: MEDIUM (a downgrade is safe only when a wrong answer is cheap to catch)**

The user chose the session model; that is the default for every subagent. Move off it only with a reason.

**Downgrade** (Haiku or Sonnet) when all three hold:

- The brief is precise: what, where, and the return shape are spelled out.
- The output is verifiable: a test passes, a grep confirms, a diff is reviewable in a minute.
- A wrong answer is cheap: you would catch it on the next step, not in production.

**Upgrade** (pass `model="opus"`) when a judgment-heavy subagent task lands in a Sonnet or Haiku session, or when a good brief has already failed at the current tier (`model-cost-per-completed-task`). "Feels important" is not a reason. Fable is not an upgrade you make yourself: ask the user first, every time.

Never downgrade the agent that makes the final decision or writes the summary you will act on. Downgrade the readers, keep the decider (`model-cheap-fanout-strong-synthesis`).

**Incorrect (downgrade on an unbounded, unverifiable task):**

```text
Agent(model="haiku", prompt="Figure out why checkout is slow sometimes and fix it.")
```

**Correct (bounded and verifiable, so Haiku is safe):**

```text
Agent(model="haiku", effort="low",
      prompt="Rename `getUser` to `fetchUser` across src/ (imports, calls, tests). Run `pnpm tsc --noEmit`.
              Return: files changed, and the tsc output (first 20 lines if it fails).")
```

Reference: [Claude Code subagents: model selection](https://code.claude.com/docs/en/sub-agents)
