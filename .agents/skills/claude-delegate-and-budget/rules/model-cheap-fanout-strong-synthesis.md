---
title: Cheap Fan-Out, Strong Synthesis
impact: MEDIUM
impactDescription: readers are many and cheap, the decider is one and strong
tags: model, fan-out, map-reduce, synthesis
---

## Cheap Fan-Out, Strong Synthesis

**Impact: MEDIUM (readers are many and cheap; the decider is one and strong)**

Large reading jobs split naturally: give each cheap worker one slice (a package, a directory, a log file, one review dimension) and the same structured return shape, then merge, resolve conflicts, and decide in the main thread on the session's model. The cheap tier reads; the strong tier judges.

- Slices should not overlap, so no two workers report the same thing differently.
- Identical return shapes let you merge by eye instead of reconciling prose.
- Synthesis stays inline: it needs your full context and the user's intent, which no worker has.
- Do not let a cheap worker synthesize other workers' reports, and do not spend the strong model on a slice.

**Incorrect (one strong agent reads everything, then also decides):**

```text
Agent(model="opus", prompt="Read all 40 route handlers, find inconsistent error handling, and propose the fix.")
```

**Correct (cheap workers per slice, you decide):**

```text
Agent(model="haiku", effort="low", prompt="In src/routes/users/*: list each handler and how it reports errors (throw / res.status / next(err)). One line per handler.")
Agent(model="haiku", effort="low", prompt="Same for src/routes/orders/*")
Agent(model="haiku", effort="low", prompt="Same for src/routes/billing/*")
-> you merge the three lists, spot the pattern, and propose the fix
```
