---
title: Judge Cost per Completed Task
impact: MEDIUM
impactDescription: a cheap request that needs a retry or a re-check costs more than the expensive one
tags: model, cost, retries, verification, escalation
---

## Judge Cost per Completed Task

**Impact: MEDIUM (a cheap request that needs a retry or a re-check costs more than the expensive one)**

The unit of cost is the finished task, not the request. A cheap agent's bill includes everything it causes afterwards: your time verifying a vague report, a second run with a better brief, or rework after acting on a wrong conclusion. Count all of it before calling a tier "cheaper".

Signs the tier was too low:

- Findings are vague ("looks fine", "should work") or point at paths that do not exist.
- It missed something obvious in the files it was told to read.
- You re-read the files anyway after its report; you paid twice.

Signs the tier was too high:

- A strong model spent its run on grep, formatting, or running a command and relaying output.

**The escalation ladder** when a run comes back thin or wrong, in this order, one step per retry:

1. **Brief**: a bad brief fails at every tier (`brief-like-a-peer`).
2. **Effort**: one level up on the same model.
3. **Tier**: one tier up, as far as Opus. Fable is never an automatic step: if Opus has failed with a good brief at high effort, ask the user whether to try Fable and wait for the answer.

**Incorrect (counting the request, not the task):**

```text
Haiku explore -> vague report -> you read all 12 files yourself -> edit
   "Haiku was cheaper."   (you paid for Haiku and for the full inline read)
```

**Correct (counting the task):**

```text
Sonnet explore with a precise brief -> file:line report -> you open 2 files -> edit
   One run, two reads, done.
```
