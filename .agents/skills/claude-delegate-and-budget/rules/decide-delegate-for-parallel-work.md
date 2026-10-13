---
title: Delegate Independent Chunks to Run in Parallel
impact: CRITICAL
impactDescription: wall-clock drops by the number of chunks; dependent chains gain nothing
tags: decide, parallel, independent, fan-out
---

## Delegate Independent Chunks to Run in Parallel

**Impact: CRITICAL (wall-clock drops by the number of chunks; dependent chains gain nothing)**

Parallelism is the one thing a subagent does that you cannot: several pieces of work at the same time. It only helps when the pieces are independent:

- No chunk needs another chunk's result to start.
- No two chunks edit the same files; give editing agents separate areas.
- Each chunk can be briefed without the others' output.

Launch every independent agent in the same message so they start together; spawning them one at a time serializes them again. A chain where B needs A's output stays sequential and costs a handoff per link, so do a short chain inline.

A side quest the user asked for that should not block the main thread also qualifies: hand it off, keep going, and fold the result in when it lands.

**Incorrect (three agents, each waiting on the previous one):**

```text
Agent(prompt="Find all call sites of legacyFetch.")       -> wait
Agent(prompt="Given those call sites, decide the order.")  -> wait
Agent(prompt="Given that order, migrate the first one.")   -> wait
```

**Correct (one message, three independent slices, synthesis inline):**

```text
Agent(prompt="Migrate legacyFetch -> apiClient in packages/web. Run its tests. Return files changed + failures.")
Agent(prompt="Same for packages/admin ...")
Agent(prompt="Same for packages/mobile ...")
-> merge the three reports yourself
```

Reference: [Claude Code subagents: foreground vs. background, concurrency](https://code.claude.com/docs/en/sub-agents)
