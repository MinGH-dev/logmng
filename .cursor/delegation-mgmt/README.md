# Delegation Management (위임 관리)

This directory contains **subagents and docs for managing subagent delegation** — i.e. how Main, Requirements, Frontend, Backend, QA, etc. hand off work. It is **separate** from the product-development Cursor setup and from the **development docs** in `docs/`.

## Document separation (문서 구분)

| 구분 | 위치 | 용도 |
|------|------|------|
| **개발 관련 문서** | **`docs/`** | 요건·워크플로우·계약·템플릿 등 개발 시 참조하는 문서. |
| **서브에이전트 개선·위임 관리 문서** | **`.cursor/delegation-mgmt/`** (here) | 위임 흐름 개선, 부작용·대응 분석, 점진적 개선 백로그·로그. |

Subagent-improvement and delegation-policy docs are **not** placed under `docs/`; they are managed only here under `delegation-mgmt/`.

## Separation from product Cursor setup

| Purpose | Location | Contents |
|--------|----------|----------|
| **Product development** | `.cursor/agents/`, `.cursor/skills/`, `.cursor/rules/`, `.cursor/commands/` | Frontend, Backend, QA, Requirements, Release, etc.; workflow rules; verify, commit, slash commands. |
| **Delegation management** | **`.cursor/delegation-mgmt/`** (this tree) | Subagent(s) and docs that **improve delegation flow** (who calls whom, handoff content, side-effect mitigation). No product code or product commands. |

- **Do not** put product agents/skills/rules/commands inside `delegation-mgmt/`.
- **Do not** put delegation-management agents in `.cursor/agents/`; keep them under `delegation-mgmt/agents/` so policy and improvement work stay isolated.

## Contents

```
delegation-mgmt/
├── README.md                    # This file
├── agents/                      # Subagent definitions for delegation management
│   └── DelegationManager.mdc    # Role: propose and apply small delegation improvements
└── docs/                        # Analysis and incremental improvement
    ├── ANALYSIS-SIDE-EFFECTS-AND-MITIGATION.md   # Side effects + mitigation strategies
    ├── IMPROVEMENT-BACKLOG.md   # Small, incremental improvement items
    └── IMPROVEMENT-LOG.md       # Log of applied improvements (date, what, why)
```

## How to use

- **DelegationManager** subagent: Use when you want to **improve the delegation flow** (e.g. "Step 4 always via Requirements", handoff clarity, reducing side effects). It reads `docs/` here and proposes **small, incremental** changes to workflow docs and delegation policy — not to product code.
- **Incremental improvements**: Prefer one small change at a time; record each in `docs/IMPROVEMENT-LOG.md` and tick off items in `docs/IMPROVEMENT-BACKLOG.md`.

## References

- Main delegation table and flow: `docs/workflow/SUBAGENT-DELEGATION.md`
- Collaboration sequence: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
- Project README flow chart: `README.md` (서브에이전트 위임 흐름)
