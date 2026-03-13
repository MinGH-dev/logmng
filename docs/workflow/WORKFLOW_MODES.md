# Workflow Modes

This document explains when to prefer a more test-driven loop versus a more discovery-driven loop during Step 4.

## 1. TDD

Prefer TDD when:

- the requirement is clearly defined
- the expected behavior is easy to express as tests
- the affected area already has stable test coverage

Typical loop:

1. write or extend a failing test
2. implement the smallest change that makes it pass
3. refactor while keeping tests green

## 2. Discovery-driven development

Prefer a discovery-driven loop when:

- the affected area is legacy or poorly understood
- the current behavior must be preserved carefully
- you need to inspect and stabilize the system before introducing stronger tests

Typical loop:

1. analyze the current implementation
2. preserve or document important existing behavior
3. improve the implementation in small steps
4. add or extend tests as understanding improves

## 3. Selection rule

- Choose TDD for new features or well-isolated logic.
- Choose discovery-driven work for legacy fixes or fragile areas.
- In both modes, the requirement doc and §3 test plan still come first.

## References

- `docs/workflow/WORKFLOW_CHECKLIST.md`
- `docs/workflow/DEVELOPMENT_WORKFLOW.md`
