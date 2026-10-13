---
title: Ask for a Decision-Shaped Return
impact: HIGH
impactDescription: you read the summary, not the transcript, so shape the summary
tags: brief, output-format, summary, confidence
---

## Ask for a Decision-Shaped Return

**Impact: HIGH (you read the summary, not the transcript, so shape the summary)**

The agent's final message is all that comes back, and it lands in your context. An open-ended "report what you find" returns pages you now have to carry. Ask instead for the shape you need to act on:

- **A verdict or answer first**, then supporting evidence as `file:line` pointers.
- **A size cap** ("under 200 words", "at most 3 findings"), so the summary stays a summary.
- **What was not checked** and how confident it is, so silence is never read as "all clear".
- **For agents that edit**: files changed, how it verified (command and result), and what is left.
- **For fan-out workers**: the same structure from every worker, so you can merge them by eye.

The user never sees the agent's report, only your reply. Relay what matters in your own words; a verdict-shaped return makes that a sentence, not a paste.

**Incorrect (open-ended; returns a transcript):**

```text
"... review the PR and tell me everything you notice."
```

**Correct (verdict, capped, pointers, gaps):**

```text
"... Return exactly: (1) ship / don't ship, (2) up to 3 findings, each with file:line and why it matters,
  (3) what you did not verify. Under 250 words."
```
