---
title: Use a Fresh Agent for Unanchored Review
impact: CRITICAL
impactDescription: a reviewer who has seen your reasoning tends to confirm it
tags: decide, review, anchoring, second-opinion
---

## Use a Fresh Agent for Unanchored Review

**Impact: CRITICAL (a reviewer who has seen your reasoning tends to confirm it)**

Review by the author is anchored: you already believe the code works. A fresh agent has no anchor only if you do not hand it one. An agent given your hypothesis tends to return it confirmed, so give it the code and the question, not your conclusion.

- For a review: the diff, the intent, and "find what is wrong", not "confirm this is fine".
- For a diagnosis: the symptom and the evidence; ask for ranked candidates with evidence for each.
- For a decision: the options stated symmetrically, with the same level of detail each.
- Ask for what it could not verify, so silence is not mistaken for approval.

Once it reports, verify any finding that will change what you do; its confidence is not evidence.

**Incorrect (hypothesis handed over, confirmation returned):**

```text
Agent(prompt="I think the flaky test is a race in useCart. Confirm that and suggest a fix.")
```

**Correct (symptom and evidence handed over, neutral question):**

```text
Agent(prompt="cart.test.ts fails ~1 in 5 runs with 'expected 2 items, got 1' (log: /tmp/cart-fail.log).
  Diff under review: git diff main...HEAD. Find the cause. Return the top 2 candidates with evidence
  (file:line), which one you would bet on, and what you could not check.")
```
