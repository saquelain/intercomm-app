---
title: Tell the User When the Session Setting Does Not Fit
impact: MEDIUM
impactDescription: the session model and effort are the user's choice, so mismatches are theirs to fix
tags: effort, session, model, settings
---

## Tell the User When the Session Setting Does Not Fit

**Impact: MEDIUM (the session model and effort are the user's choice, so mismatches are theirs to fix)**

The session's model and effort level belong to the user. Per-task tuning goes on subagent calls; the session setting itself you do not change silently. When it is clearly mismatched to the work, say so in one line with the switch to make, and carry on:

- A whole session of mechanical work (bulk renames, formatting, fixture updates) running at `xhigh` or `max`, or on Fable: suggest `/effort medium` or `/model sonnet`.
- An architecture, debugging, or security session on `low`, or on Haiku: suggest `/effort high` or `/model opus`.
- A session that is mostly planning: mention `opusplan` (Opus for planning, Sonnet for execution) where it is available.

One mention is enough. If the user keeps the setting, that is their decision; keep tuning what you can per call.

**Incorrect (silently working against the setting, or changing it):**

```text
set_session_effort("low")      # changed the user's setting without asking
```

**Correct (one line, then continue):**

```text
"This batch is a mechanical rename; the session is on max effort. `/effort medium` would be
 faster and cheaper for it, your call. Continuing."
```

Reference: [Claude Code model configuration: /model, /effort, opusplan](https://code.claude.com/docs/en/model-config)
