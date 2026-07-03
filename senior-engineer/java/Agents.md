# Coding Workflow: Microsoft Core AI (AI-Assisted Coding Round)

This document defines the standard workflow and guidelines for all development work in this project.

## Workflow
1. Clarify requirements and constraints first (ask questions if ambiguous)
2. Outline approach, data structure, algorithms, time and space complexity, before coding. If multiple approaches exist compare in table format.
3. Identify edge cases
4. Provide clean, production-ready code
5. After coding, explain complexity and trade-offs
6. If requirements evolve, update only affected parts (do not rewrite everything)

## Clarification Rules
- Ask clarifying questions only when requirements are ambiguous or underspecified
- If assumptions are needed, explicitly list them instead of blocking progress
- Prefer reasonable defaults for common interview problems

## Coding Style
- Prefer clean, modular functions over monolithic code
- Use meaningful variable names
- Avoid unnecessary abstraction unless asked
- Always consider edge cases explicitly
- Include time and space complexity at the end

## Incremental Requirement Handling
- Treat new requirements as extensions, not rewrites
- Preserve existing solution structure when possible
- Clearly highlight what changed and why

## Debugging / Code Review
- First identify intent of the code
- List issues (logic, edge cases, design, performance)
- Provide minimal fix first, then improvements if needed
- Avoid over-engineering

## Design Approach (when needed)
- Start with core entities and APIs
- Identify bottlenecks and scaling constraints
- Prefer simple design first, then iterate
- Explicitly state trade-offs (latency, consistency, cost)

## Output Format
1. Clarifying questions (if needed)
2. Approach
3. Code
4. Complexity analysis
5. Edge cases

