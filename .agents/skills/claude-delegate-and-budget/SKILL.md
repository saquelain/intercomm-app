---
name: claude-delegate-and-budget
description: Decides whether to do work inline or hand it to a subagent, which model tier the subagent gets, and what effort (thinking) level each piece runs at, so tokens and wall-clock go where they change the outcome. Use when planning any multi-step or multi-file task, before spawning any agent, when writing a subagent definition, or when the user says "use a subagent", "parallelize this", "spawn agents", "use haiku/sonnet/opus", "which model", "think harder", "ultrathink", "effort level", "this is slow", "burning tokens", "too expensive", or "be efficient".
when_to_use: |
  - Planning how to execute a task that spans several files, several independent chunks, or a long investigation, even if the user never mentions agents
  - About to spawn a subagent, pick its model, or set its effort; or writing a `.claude/agents/*.md` definition
  - User mentions subagents, parallel or background agents, model tiers (haiku, sonnet, opus, fable), thinking or effort levels, "ultrathink", cost, speed, or token usage
  - A delegated result came back thin or wrong, a fix has failed twice, or the session feels slow or expensive for the work it is doing
  - Do NOT use for: choosing a model for an application the user is building (API/SDK model selection), or one- or two-tool-call tasks that need no planning
license: MIT
metadata:
  author: piplupjs
  version: "1.0.0"
---

# Claude Delegate and Budget

The scarce resources are the main thread's context window, wall-clock, and tokens. Keep judgment-heavy work in the main thread, push reading-heavy and independent work to subagents, give each piece the cheapest model and lowest effort that can finish it correctly, and judge cost per completed task. Not for picking models inside an app the user is building.

## Workflow

1. **Shape**: split the task into chunks; mark each as independent or dependent, reading-heavy or judgment-heavy, verifiable or not.
2. **Decide**: inline by default; delegate only for parallel independent chunks, context protection, or fresh eyes (`decide-*`).
3. **Brief**: goal, what is ruled out, pointers, scope, return shape (`brief-*`).
4. **Model**: tier to the judgment needed and the cost of a wrong answer; inherit when unsure; Fable only if the user says yes (`model-*`).
5. **Effort**: scale to stakes and brief width, never to task length (`effort-*`).
6. **Launch and wait**: independent agents in one message, background by default; never redo or predict their work; verify before reporting done.

## Decision Table

Model and effort columns apply to subagents; inline work uses the session setting, which is the user's.

| Work | Where | Model | Effort |
|------|-------|-------|--------|
| A handful of tool calls, target known; a quick check | Inline | session | session |
| Sweep many files, return pointers | Read-only subagent | haiku / sonnet | low |
| Long or noisy test run; return failure names | Subagent | haiku | low |
| Spelled-out edit across many files | Subagent | haiku / sonnet | low |
| Bounded implementation with a clear spec | Subagent | sonnet | medium |
| Independent review of a diff | Subagent | inherit | high |
| Ambiguous debugging, design, cross-cutting refactor | Inline | session | session (flag if low) |
| Security, data migration, irreversible step, 2 failed attempts | Inline, re-verify, ask | session | session (flag if low) |
| Merging subagent reports into a decision | Inline | session | session |

## Claude Code Mapping

| Decision | Where it goes |
|----------|---------------|
| Model per call | `model` on the Agent call (`haiku`, `sonnet`, `opus`, `fable`, or a full ID); `model:` in `.claude/agents/*.md` (`inherit` = session model) |
| Effort per call | `effort` on the Agent call (`low`…`max`), using the levels in the table above; `effort:` in the agent file |
| Session level | The user's `/model` and `/effort`, and `ultrathink` typed in their prompt for one deep turn; suggest, don't change |
| Built-ins | `Explore` (read-only) and `Plan` run on the session model unless `model` is passed; pass `haiku` for cheap sweeps |

## Rule Categories by Priority

| Priority | Category | Impact | Prefix |
|----------|----------|--------|--------|
| 1 | Decide | CRITICAL | `decide-` |
| 2 | Brief | HIGH | `brief-` |
| 3 | Model | MEDIUM | `model-` |
| 4 | Effort | MEDIUM | `effort-` |

## Quick Reference

### 1. Decide (CRITICAL)

- `decide-inline-by-default`: A handful of tool calls, a known target, or a result you need next: do it yourself
- `decide-delegate-for-parallel-work`: Only independent chunks get faster; launch them in one message
- `decide-delegate-to-protect-context`: Delegate sweeps and long output; keep the conclusion and `file:line` pointers
- `decide-delegate-for-fresh-eyes`: Review and diagnosis by an agent that was given the evidence, not your hypothesis

### 2. Brief (HIGH)

- `brief-like-a-peer`: Goal, what is ruled out, pointers not content, scope, conventions, done
- `brief-ask-for-a-decision-shape`: Verdict first, capped length, pointers, what was not checked; relay what matters to the user
- `brief-launch-and-wait`: Background by default; never redo, predict, or collide with delegated work; verify edits before reporting done

### 3. Model (MEDIUM)

- `model-tier-to-judgment`: Haiku for mechanical and verifiable, Sonnet for bounded coding, Opus for judgment; Fable only after asking the user, never on your own
- `model-inherit-when-unsure`: Session model by default; downgrade only for precise, verifiable, cheap-to-be-wrong work
- `model-cost-per-completed-task`: Count retries and your re-checks; when a run fails: brief, then effort, then tier up to Opus
- `model-cheap-fanout-strong-synthesis`: Cheap workers per slice with one return shape; you merge and decide

### 4. Effort (MEDIUM)

- `effort-scale-to-stakes`: Effort follows the cost of being wrong, not the size of the task
- `effort-low-for-narrow-briefs`: Narrow brief + low effort + cheap model; wide brief + high effort + strong model
- `effort-raise-before-irreversible`: Slow down and re-verify before destructive actions, after two failures, and on unconfirmed pattern matches
- `effort-flag-session-mismatch`: Say in one line when the session setting does not fit; never change it silently

## How to Use

Read individual rule files for the full rule and examples:

```
rules/decide-inline-by-default.md
```

Section metadata lives in `rules/_sections.md`; new rules start from `rules/_template.md`.
