---
title: Brief Like a Peer, Not a Prompt
impact: HIGH
impactDescription: the brief is the only context the agent will ever have
tags: brief, handoff, scope, context
---

## Brief Like a Peer, Not a Prompt

**Impact: HIGH (the brief is the only context the agent will ever have)**

The agent starts from zero: it does not see your conversation, your earlier tool results, or what you have already ruled out. Everything it needs has to be in the brief, and everything you have settled has to be marked settled so it does not reopen it. Write it the way you would brief a capable colleague who just walked in:

1. **Goal and definition of done**: what to produce and how you will know it is finished.
2. **What is already ruled out**: hypotheses you discarded and why, so it does not redo them.
3. **Pointers, not content**: file paths, commands, doc URLs, a log path. Do not retype files into the prompt.
4. **Scope and boundaries**: which directory, what not to touch, whether it may edit, run, install, or commit.
5. **Conventions**: the branch, the style skill to follow, the test command.
6. **Return shape**: see `brief-ask-for-a-decision-shape`.

If you cannot write a clear brief, you do not understand the task well enough to hand it off. Do a little more yourself until you can.

**Incorrect (goal only; the agent must rediscover everything):**

```text
Agent(prompt="Fix the login bug.")
```

**Correct (goal, ruled out, pointers, scope, done, return shape):**

```text
Agent(prompt="Users get logged out after ~5 min on Safari only. Not a cookie SameSite issue
  (checked: Lax is set, see src/auth/cookies.ts:40). Repro: pnpm test:e2e --grep safari-session.
  Start from src/auth/refresh.ts and the storage fallback in src/lib/storage.ts. Edit only src/auth.
  Done when the e2e test passes 3 times in a row. Do not commit.
  Return: root cause (file:line), files changed, test output summary, anything left uncertain.")
```
