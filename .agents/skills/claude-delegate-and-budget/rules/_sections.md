# Sections

This file defines all sections, their ordering, impact levels, and descriptions.
The section ID (in parentheses) is the filename prefix used to group rules.

---

## 1. Decide (decide)

**Impact:** CRITICAL  
**Description:** Whether a piece of work stays in the main thread or goes to a subagent. A needless handoff loses context twice; a missing one fills the main thread with raw reading or serializes work that could run in parallel.

## 2. Brief (brief)

**Impact:** HIGH  
**Description:** How to hand work off and take it back: the brief is the subagent's whole world, and its summary is all the main thread gets.

## 3. Model (model)

**Impact:** MEDIUM  
**Description:** Which model tier a subagent runs on, judged by the judgment the task needs and the cost of a wrong answer, not by how important the task feels.

## 4. Effort (effort)

**Impact:** MEDIUM  
**Description:** How much thinking a task gets, scaled to the stakes of being wrong and the width of the brief, not to the length of the task.
