# Editing the n8n workflows from Claude Code

Three separate things can stop an n8n change from taking effect, and two of them look like
success. All three cost real time on 2026-09-20, so they are written down here.

**If any of these recurs, say so explicitly and tell Chris.** Do not quietly fall back to handing
over a file to paste — that is what happened for several hours before anyone realised the writes
were being refused rather than applied.

---

## 1. Auto mode silently refuses the write

**Symptom.** `update_workflow` returns:

```
Permission for this action was denied by the Claude Code auto mode classifier.
Reason: [Modify Shared Resources]
```

**What it is not.** Not credentials, not connectivity, not n8n permissions. Reads, execution
history and Home Assistant writes all work in the same session. The MCP token reports
`workflow:update` in its scopes.

**Cause.** In auto mode a classifier decides each action instead of asking, and it both approves
and denies on the user's behalf. Chris is never shown a prompt, so from his side it looks like
nothing was attempted.

**Fix.** An explicit allow rule overrides the classifier. In `.claude/settings.local.json`:

```json
{
  "permissions": {
    "allow": [
      "mcp__f61f7000-ac2f-4753-8ec9-b9f7fa0d1cc6__ha_eval_template",
      "mcp__4847ea87-5940-430f-8436-e54f69378f04__update_workflow",
      "mcp__4847ea87-5940-430f-8436-e54f69378f04__publish_workflow"
    ]
  }
}
```

Verified working in auto mode with no prompt, and it took effect immediately without a new
session. Deliberately excluded: `execute_workflow`, which runs a workflow for real and can switch
heads, and `restore_workflow_version`, which rolls back without asking. Also excluded: archiving,
unpublishing and the data-table delete tools.

**Claude cannot apply this fix itself.** Editing its own permission rules is refused as
`[Self-Modification]`, which is correct and must not be worked around. Hand Chris the exact JSON
and let him paste it.

**Watch for a broken file.** A missing comma makes the whole file invalid JSON, and then *no*
rules apply, including ones that previously worked. Validate before concluding anything:

```bash
node -e "JSON.parse(require('fs').readFileSync('.claude/settings.local.json','utf8')); console.log('VALID')"
```

---

## 2. `update_workflow` only writes the DRAFT

**Symptom.** The update returns `appliedOperations: 1` with no warnings, and the change is
genuinely saved — but the schedule keeps running the old code.

**Cause.** n8n separates the draft from the published version. The update result says nothing
about which one is live.

**Fix.** Call `publish_workflow` afterwards, then confirm with a fresh read:

- `activeVersionId` must equal `versionId`
- `activeVersion.sameAsDraft` must be `true`

Never conclude a change is live from the update response alone. Re-read the workflow.

---

## 3. The connector ID can change

The `4847ea87-5940-430f-8436-e54f69378f04` in those rules is the n8n connector's ID on this
machine. If the connector is reinstalled the ID changes, the allow rules stop matching, and the
symptom looks exactly like item 1 again. Check the current ID against the tool names before
assuming anything else is wrong.

---

## Verifying a Watchdog change actually works

State alone is not proof, because the schedule and a manual run produce the same result. The
schedule trigger fires at **31 seconds past the minute**, so a helper written at `:31` is the
recurring schedule and not something Claude did by hand. That signature is what confirmed the
override fix was live rather than a side effect of testing.

A change to drift or conflict logic takes two ticks to show: one to observe the difference and
start the timer, the next to act. Expect roughly two minutes, not 45 seconds — the hold is a
debounce requiring two sightings, and the tick interval governs the wall-clock response.
