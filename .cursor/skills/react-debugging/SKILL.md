# React Debugging Methodology

Use when: fixing a React UI bug where user interaction (click, toggle, input) does not produce the expected visual or state change. Applies to Frontend, Frontend-Auth, Frontend-ActivityLog, Frontend-Log subagents.

---

## Checklist (follow in order)

### Step 1 — Full data flow trace

Trace the **complete path** from user gesture to UI output:

1. **User action** (click, change) → **event handler** (onChange, onClick) → **state updater** (setState, dispatch) → **parent callback** (onChange prop) → **parent state setter** (useState, useReducer).
2. **Parent state** → **props** passed back to child → **derived state** (useMemo, normalize, transform functions) → **rendered value** (checked, value, className).

At each step, verify the value is what you expect. Do not stop at "the handler is called" — continue all the way to "the rendered value reflects the change."

### Step 2 — Normalize/transform audit

If the component has a **normalize**, **transform**, or **mapX** function (often in `useMemo`) that converts incoming props to internal state:

- **Check that the function preserves all relevant fields** from the input. A common bug: the transform copies only some fields (e.g. `screenId`, `scope`) but drops others (e.g. `write`, `approve`), causing nullish-coalescing (`??`) to reset them to defaults on every render.
- **Test with a concrete example**: if `selectedScreens` contains `{ screenId: 'x', write: false }`, trace through the normalize function line by line. Does the output have `write: false` or does it fall through to a default?

### Step 3 — Controlled input identity

For controlled inputs (`checked={value}`, `value={state}`):

- Identify **which derived state** drives the input's `checked`/`value`.
- Verify that this derived state **updates correctly after a state change** (not reset by a normalize/transform on re-render).
- Check for `?? defaultValue` patterns where the left side could be `undefined` due to a transform that strips the field.

### Step 4 — Single-gesture single-update

Verify that one user gesture triggers **exactly one** state update:

- **label + input**: If a `<label htmlFor="X">` wraps or is associated with `<input id="X">`, clicking the label triggers the input. If both the label's `onClick` and the input's `onChange`/`onClick` update state, one click produces two updates that may cancel each other.
- **stopPropagation**: Check whether a parent element's `onClick` also fires and triggers a competing update.
- **Event object reuse**: In synthetic events, `e.target.checked` may reflect the browser's internal state, not React's controlled state.

### Step 5 — 3-strike rule (escalate scope)

If the **same symptom persists** after 2 fix attempts targeting the same layer (e.g. event handlers, CSS, label/input association):

- **Stop fixing the same layer.** The root cause is likely elsewhere.
- **Move up**: investigate the data flow (Step 1–3) — normalize functions, parent state, props derivation.
- **Move down**: investigate the rendering — does the DOM actually receive the new value? Use `console.log` at the render level (inside the JSX or just before return) to verify.
- Explicitly state in the bugfix document: "2 attempts on [layer] failed; escalating to [data flow / parent state / rendering]."

---

## Common React bugs (quick reference)

| Pattern | Symptom | Root cause |
|---------|---------|------------|
| Normalize strips fields | Click updates state but UI reverts immediately | `useMemo(normalize(props))` recreates the value without preserving the changed field |
| label + input double fire | Click toggles then immediately un-toggles | Both label `onClick` and input `onChange` update state; second update uses stale closure |
| Functional updater with stale closure | State appears to not update | `setState(() => next)` where `next` is captured from a stale render |
| Controlled input with ?? default | Input always shows default value | `checked={item?.write ?? true}` where `item.write` is `undefined` because transform dropped it |

---

## When to use this skill

- **Trigger**: Any bug where "user clicks X but nothing happens" or "state updates but UI doesn't change."
- **Do not use for**: API errors, build failures, routing issues, or backend bugs.
