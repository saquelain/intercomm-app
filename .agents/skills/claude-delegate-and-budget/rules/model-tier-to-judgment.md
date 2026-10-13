---
title: Match the Model Tier to the Judgment Required
impact: MEDIUM
impactDescription: too low a tier returns confident errors, too high a tier pays Opus to grep
tags: model, haiku, sonnet, opus, fable, tier
---

## Match the Model Tier to the Judgment Required

**Impact: MEDIUM (too low a tier returns confident errors; too high a tier pays Opus to grep)**

Pick a subagent's model by asking three questions: how much judgment does the task need, how verifiable is its output, and how expensive is a wrong answer? Importance is not one of the questions; a task can be important and still mechanical.

| Tier | Give it | Not |
|------|---------|-----|
| **Haiku** | Mechanical, high-volume, verifiable work: grep-and-summarize, run tests or lint and report, format conversion, classifying files, applying a spelled-out edit across many files | Anything where "it looks fine" would be the output |
| **Sonnet** | Ordinary coding with a clear spec, bounded exploration, first-pass review, docs and tests, a worker in a fan-out | Ambiguous root-cause work, cross-cutting design, the final say |
| **Opus** | Architecture, ambiguous debugging, security review, refactors across modules, synthesis of other agents' reports, final review of anything that ships | Bulk reading, running commands |
| **Fable** | Inherit it if the session already runs on it; otherwise only after the user says yes. Never switch to Fable on your own: ask, with the reason, and wait | Anything the user has not approved it for |

The price gap between tiers is large, so a cheap tier on bulk work is real savings and a strong tier on the decision is cheap insurance.

In Claude Code the built-in `Explore` and `Plan` agents run on the session's model unless you pass `model`, so a cheap sweep needs `model="haiku"` or `"sonnet"` spelled out.

**Incorrect (the session's Opus doing a file inventory):**

```text
Agent(subagent_type="Explore", prompt="List every file that imports from src/legacy and the symbol it uses.")
```

**Correct (Haiku for the inventory; the decision stays with you):**

```text
Agent(subagent_type="Explore", model="haiku", effort="low",
      prompt="List every file that imports from src/legacy, one line each: path, imported symbols.")
-> you decide the migration order
```

Reference: [Claude Code model configuration](https://code.claude.com/docs/en/model-config)
