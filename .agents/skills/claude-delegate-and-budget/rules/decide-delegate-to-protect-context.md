---
title: Delegate Reading, Keep the Conclusion
impact: CRITICAL
impactDescription: the main thread's context window is the one resource you cannot get back
tags: decide, context, exploration, read-only
---

## Delegate Reading, Keep the Conclusion

**Impact: CRITICAL (the main thread's context window is the one resource you cannot get back)**

File dumps, long logs, vendored docs, and wide search sweeps are raw material you need once and then carry forever. When answering a question means reading across many files or directories, delegate the reading to a read-only agent and ask for the conclusion plus `file:line` pointers. You keep the answer, not the pages.

- Use a read-only exploration agent so it cannot drift into editing.
- Ask for pointers, not excerpts; open a pointer yourself only when it drives an edit.
- Name the breadth: "this directory", "every place X is called", "the three packages that import Y".
- A single file read is not a sweep; read it inline.

The same applies to long command output: let an agent run the flaky test suite and return the failing names and the first stack frame of each, instead of pasting two thousand lines into your context.

**Incorrect (sweep done inline, context filled with twelve files):**

```text
Read(a.ts) Read(b.ts) Read(c.ts) ... Read(l.ts)
-> "The auth flow is in b.ts and h.ts."
```

**Correct (sweep delegated, pointers returned):**

```text
Agent(subagent_type="Explore", prompt="How does session refresh work in src/auth?
  Return: the entry point, the files involved (file:line), and where the token is persisted. Under 200 words.")
-> open only the two files the pointers name
```

Reference: [Claude Code subagents: context isolation](https://code.claude.com/docs/en/sub-agents)
