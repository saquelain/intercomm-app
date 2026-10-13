---
title: Do the Work Yourself by Default
impact: CRITICAL
impactDescription: every subagent costs a context handoff in both directions
tags: decide, inline, subagent, overhead
---

## Do the Work Yourself by Default

**Impact: CRITICAL (every subagent costs a context handoff in both directions)**

A fresh agent knows only what the brief says, and you see only the summary it sends back. Both handoffs drop detail, you cannot watch it work, and its mistakes arrive in the same confident tone as its findings. That overhead is paid before the agent does anything useful, so it only pays off when the work is big enough to amortize it.

Stay inline when any of these hold:

- The whole job is a handful of tool calls (roughly five or fewer) and you know where to look.
- You would have to explain half your current context for the agent to do it right.
- The result feeds your very next step, or the user may want to redirect mid-way.
- It is a check you could run yourself: a grep, a test run, reading one file.

When in doubt, don't spawn. A subagent earns its place for parallel independent chunks (`decide-delegate-for-parallel-work`), reading you don't want in your context (`decide-delegate-to-protect-context`), or a review that must not be anchored on your reasoning (`decide-delegate-for-fresh-eyes`).

**Incorrect (a one-line lookup handed off):**

```text
Agent(prompt="Find where formatDate is defined and tell me the file.")
```

**Correct (one tool call, done):**

```text
Grep(pattern="function formatDate|const formatDate", type="ts")
```

Reference: [Claude Code subagents: when to use the main conversation vs. subagents](https://code.claude.com/docs/en/sub-agents)
