# Agent — Quick Start

The **Agent** turns what you want to do into a set of steps that run automatically on your target device — either over SSH (Terminal mode) or by typing on a wireless keyboard (HID mode).

You describe the task in plain English. The Agent plans the steps. You approve. It executes.

---

## 1. Before you start

Three things must be configured before the Agent can work:

### A. AI provider configuration

> Configuration flow: Enable AI feature → Select provider → Configure API Key → Test connection → Return to Agent

1. Open the Agent screen. If you see the **"Connect AI"** gate, tap the button below it (Use my API key) to go to the AI Settings screen.

* **Example (Tap Use my API + Enable AI features)**

  <img src="images/01-connect-ai-gate.png" alt="Connect AI gate" width="180" /><img src="images/02-enable-ai-features.png" alt="Enable AI features" width="180" />

AI features are disabled by default. After enabling, exit the configuration screen.

Start setting up and choose an AI provider (OpenAI, Anthropic, Google, DeepSeek, etc.). Enter the API Key and Endpoint (URL), or add a third-party AI provider.

1. Tap the **dropdown** icon in the upper right of AI Provider Setting to open Select AI Provider.

   > When using for the first time, preset providers have no API Key configured.

* **Example (AI Provider Setting + Select AI Provider)**

  <img src="images/03-ai-provider-setting.png" alt="AI Provider Setting" width="180" /><img src="images/04-select-ai-provider.png" alt="Select AI Provider" width="180" />

> Local providers (Ollama, Local Qwen) are supported — the API key field can be left empty for them.

If you need to add a new provider, follow these steps:

1. Tap the green **Add New Provider** button
2. Confirm in the popup window, tap **Add Provider** again

* **Example (Tap Add New Provider, confirm Add Provider)**

  <img src="images/05-add-new-provider.png" alt="Add new provider" width="180" /><img src="images/06-confirm-add-provider.png" alt="Confirm add" width="180" />

How to configure a new Provider:

1. Fill in three basic options: Provider name (customizable), API Base URL, and Model Name.
2. Choose whether to enable API Key Optional (based on provider requirements).

* **Example (Fill in three basic items + API Key Optional setting)**

  <img src="images/07-provider-basic-info.png" alt="Provider basic info" width="180" /><img src="images/08-api-key-optional.png" alt="API Key Optional setting" width="180" />

After configuration, choose whether to set up an API Key based on the provider's requirements (local providers like Ollama can leave it empty). Here are the steps to set up an API Key:

1. After selecting a provider, scroll down to the API Key Management section, tap the update API Key button to open the configuration.
2. Paste the API Key in the input field, tap **Save key** to save.
3. You can tap Clear Key later to remove the key if needed.

* **Example (Tap update API Key + paste and save API Key)**

  <img src="images/09-update-api-key.png" alt="Update API Key" width="180" /><img src="images/10-save-api-key.png" alt="Save API Key" width="180" />

After everything is configured, you can optionally:

1. Tap the **Test connection** button to test if the AI configuration connects successfully. A green notification will appear on success.
2. Return to the Agent screen, which will automatically switch to conversation mode.

* **Example (Tap Test connection button + success notification)**

  <img src="images/11-test-connection.png" alt="Test connection" width="180" /><img src="images/12-test-success.png" alt="Connection successful" width="180" />
  
  > If the test fails, please check: Is the API Key correct? Is the Endpoint URL complete? Is the network connection normal?

### B. Target Setting configuration

Tap the **target icon**  in the top bar and pick one:

<img src="images/13-target-icon.png" alt="Target icon" width="180" /><img src="images/14-target-selection.png" alt="Target selection" width="180" />

| Pick this | Resulting mode | What it does |
|---|---|---|
| Both SSH Profile and target OS | **Terminal mode** | The Agent generates commands matching the remote system, executes them via SSH, and captures output. |
| Target OS only (macOS / Windows / Linux) | **HID mode** | Types keystrokes into the target's active window via BLE keyboard. Output is **not** captured. |

> Terminal Profile configuration corresponds to terminal SSH connection settings.

### C. Agent Setting configuration

Tap the **settings icon** in the top bar and configure each item:

1. **AI Provider** option configures the AI settings used by the current Agent.
2. **Execution Limits** configures plan generation limits and maximum retry attempts for generation/execution failures.
3. **System Prompts** allows customization of system prompts for Terminal mode and HID mode (advanced users).

<img src="images/17-agent-settings.png" alt="Agent Settings" width="180" /><img src="images/18-settings-detail.png" alt="Settings detail" width="180" />

---

## 2. Your first task in 3 steps

### Step 1 — Describe what you want

Type a request in the input box and tap **Send**.

<img src="images/15-input-request.png" alt="Input request" width="180" />

Example prompts:

- `"Check the current status of the computer"`
- `"Show me the top 10 CPU-consuming processes"`
- `"Open a browser and go to example.com"` (HID mode)
- `"Save the current file and switch to the next tab"` (HID mode)

While the Agent is thinking, you'll see "Thinking..." — it may take a few seconds.

### Step 2 — Review the plan

When the plan is ready, the Agent shows a card listing every step. You have three options:

- **Approve & Run** — run the plan as-is.
- **Edit** — modify, add, or remove steps before running.
- **Cancel** — discard the plan.

> <img src="images/19-plan-hid-card.png" alt="Plan card" width="180" /><img src="images/20-plan-hid-mark.png" alt="HID mark" width="180" />
> **Nothing runs until you approve.** The Agent will never execute a command on its own.
>
> When generating HID commands, the plan card will have an HID marker.

### Step 3 — Watch it execute

Each step appears as a card with live output (in Terminal mode) or a status line (in HID mode). When everything finishes, the Agent posts a short summary. After completion, you can type another request right away.

* Execution process + Agent summary

  <img src="images/21-execution-process.png" alt="Execution process" width="180" /><img src="images/22-agent-summary.png" alt="Agent summary" width="180" />

  > The live-running terminal will show the current command execution status. Tap the hourglass icon to abort the current Agent execution.

---

## 3. Example prompts

### Terminal mode (SSH)

| You type | What the Agent does |
|---|---|
| `"Find the largest files in my home directory"` | Runs `find` + `du`, sorts, and summarizes |
| `"Restart the nginx service"` | Runs `sudo systemctl restart nginx` (if allowed) |
| `"Show me what's listening on port 80"` | Runs `ss -tulpn \| grep :80` or similar |
| `"Clean up old log files older than 7 days"` | Plans `find /var/log -mtime +7 -delete` (review carefully!) |

### HID mode (BLE keyboard)

| You type | What the Agent does |
|---|---|
| `"Open the terminal"` | Sends the OS-specific shortcut (Cmd+Space on macOS, Ctrl+Alt+T on Linux, Win+R `cmd` on Windows) |
| `"Save the file and close the tab"` | Sends Cmd+S / Ctrl+S, then Cmd+W / Ctrl+W |
| `"Switch to the second desktop"` | Sends the OS-specific virtual-desktop shortcut |

---

## 4. Safety

The Agent is designed to be safe by default:

- **Approval gate.** Every plan is shown to you before any command runs.

- **Dangerous commands are blocked.** Commands like `rm -rf /`, `mkfs`, fork bombs, and other known-destructive patterns are refused outright — even if the backend AI suggests them.

- **OS mismatch is caught.** If the Agent generates a Linux command for a Windows target, it is flagged and blocked before execution.

- **SSH credentials are masked.** Your username, host, and port are never sent to third-party AI providers.

- **You can cancel anytime.** Tap the hourglass button in the input bar to stop a running plan.

  > Commands marked as *dangerous* (e.g. `rm`, `shutdown`) are not auto-blocked, but you will see them clearly in the plan card before approving.

---

## 5. Common questions

**Q: The Agent says "No target configured".**
A: Tap the target icon 🎯 in the top bar and select either an SSH profile or a target OS.

**Q: The plan keeps generating wrong commands for my OS.**
A: Open the target settings and explicitly pick your OS. The Agent will prefer this over auto-detection.

**Q: The Agent returned an error with a "Retry" button.**
A: Tap **Retry** to regenerate the plan. If it fails repeatedly, check your AI provider's API key and quota.

**Q: A step shows "Retried" in grey.**
A: The step originally failed, and the Agent already regenerated an alternative command that succeeded. No action needed.

**Q: In HID mode, output isn't captured.**
A: That's by design — HID mode types keystrokes into the active window. Use Terminal mode (SSH) when you need to read command output.

**Q: Can I use a local model instead of a cloud API?**
A: Yes. Add an Ollama or Local Qwen provider in Settings — the API key field can be empty.

---

## 6. What's next

Once you're comfortable with the basics:

- **Customize the Agent.** Tap the settings icon to adjust the max number of steps, max retries, or even edit the system prompt.
- **Edit plans before running.** Tap **Edit plan** on a plan card to tweak individual steps.
- **Switch targets on the fly.** The **target icon** lets you change OS or SSH profile between requests.

