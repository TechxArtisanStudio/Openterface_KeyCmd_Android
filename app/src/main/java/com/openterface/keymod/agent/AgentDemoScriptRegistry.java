package com.openterface.keymod.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of curated Agent marketing demo scripts. */
public final class AgentDemoScriptRegistry {

    private static final Map<String, AgentDemoScript> SCRIPTS = new LinkedHashMap<>();

    static {
        SCRIPTS.put(AgentDemoScript.ID_HERO_MIXED, heroMixed());
        SCRIPTS.put(AgentDemoScript.ID_CLI_ONLY, cliOnly());
        SCRIPTS.put(AgentDemoScript.ID_MACRO_ONLY, macroOnly());
    }

    private AgentDemoScriptRegistry() {
    }

    @NonNull
    public static List<AgentDemoScript> all() {
        return Collections.unmodifiableList(Arrays.asList(
                SCRIPTS.get(AgentDemoScript.ID_HERO_MIXED),
                SCRIPTS.get(AgentDemoScript.ID_CLI_ONLY),
                SCRIPTS.get(AgentDemoScript.ID_MACRO_ONLY)));
    }

    @Nullable
    public static AgentDemoScript get(@Nullable String id) {
        if (id == null) {
            return null;
        }
        return SCRIPTS.get(id);
    }

    @NonNull
    public static AgentDemoScript defaultScript() {
        return SCRIPTS.get(AgentDemoScript.ID_HERO_MIXED);
    }

    @NonNull
    private static AgentDemoScript heroMixed() {
        List<AgentPlanStep> plan = Arrays.asList(
                new AgentPlanStep(1, "Connect SSH to target Mac", "~2s", AgentPlanStep.Kind.TERMINAL),
                new AgentPlanStep(2, "Run df -h /", "Check disk usage", AgentPlanStep.Kind.TERMINAL),
                new AgentPlanStep(3, "Play macro \"Morning Setup\"", "12 HID steps · ~8s", AgentPlanStep.Kind.MACRO));
        List<String> terminal = AgentDemoScript.lines(
                "$ ssh mac-host",
                "Connecting to mac-host...",
                "Connected.",
                "$ df -h /",
                "Filesystem      Size   Used  Avail Capacity  Mounted on",
                "/dev/disk1s1   460Gi  193Gi  267Gi    42%    /",
                "Macintosh HD   42% used");
        List<String> macroSteps = AgentDemoScript.lines(
                "Focus host window",
                "Open Finder",
                "Launch Safari",
                "Open calendar",
                "Dismiss notifications");
        List<String> macroChips = AgentDemoScript.lines(
                "Sending <CMD>+<TAB>…",
                "Sending <CMD>+<SPACE>…",
                "Click Dock · Safari",
                "Sending <CMD>+<SPACE>…",
                "Sending <ESC>…");
        return new AgentDemoScript(
                AgentDemoScript.ID_HERO_MIXED,
                "Full workflow",
                "CLI + Macro in one command",
                "Check disk space on the Mac, then run my Morning Setup macro.",
                "I'll connect to your Mac via Terminal, check disk usage, then play your Morning Setup macro on the host.",
                plan,
                "Done. Macintosh HD is 42% full. Morning Setup completed in 8s.",
                terminal,
                macroSteps,
                macroChips,
                true,
                true);
    }

    @NonNull
    private static AgentDemoScript cliOnly() {
        List<AgentPlanStep> plan = Arrays.asList(
                new AgentPlanStep(1, "SSH to 192.168.50.10", "Linux host", AgentPlanStep.Kind.TERMINAL),
                new AgentPlanStep(2, "Run top -bn1 | head -20", "Sort by CPU", AgentPlanStep.Kind.TERMINAL));
        List<String> terminal = AgentDemoScript.lines(
                "$ ssh user@192.168.50.10",
                "Connected to 192.168.50.10.",
                "$ top -bn1 | head -20",
                "  PID USER      PR  NI    VIRT    RES  %CPU  COMMAND",
                "  842 root      20   0  169032   4120  12.4  nginx",
                "  615 root      20   0   89312   3104   4.1  systemd",
                " 1204 node      20   0  512000  88400   3.2  node");
        return new AgentDemoScript(
                AgentDemoScript.ID_CLI_ONLY,
                "Terminal only",
                "Remote hands over SSH",
                "What's using the most CPU on the Linux box?",
                "I'll SSH in and run top -bn1 sorted by CPU.",
                plan,
                "Top consumer: nginx at 12.4% CPU. Want me to restart the service?",
                terminal,
                Collections.emptyList(),
                Collections.emptyList(),
                true,
                false);
    }

    @NonNull
    private static AgentDemoScript macroOnly() {
        List<AgentPlanStep> plan = Arrays.asList(
                new AgentPlanStep(1, "Focus host window", "Bring target to front", AgentPlanStep.Kind.HID),
                new AgentPlanStep(2, "Play macro \"Stream Setup\"", "8 HID steps · ~6s", AgentPlanStep.Kind.MACRO));
        List<String> macroSteps = AgentDemoScript.lines(
                "Focus host window",
                "Open Spotlight",
                "Launch OBS",
                "Select scene Main",
                "Start virtual camera");
        List<String> macroChips = AgentDemoScript.lines(
                "Sending <CMD>+<TAB>…",
                "Sending <CMD>+<SPACE>…",
                "Typing OBS…",
                "Click scene Main",
                "Sending <CMD>+<SHIFT>+V…");
        return new AgentDemoScript(
                AgentDemoScript.ID_MACRO_ONLY,
                "Macro only",
                "One phrase, full desktop workflow",
                "Start my stream setup.",
                "I'll run your Stream Setup macro — 8 HID steps on the host.",
                plan,
                "Stream Setup finished. OBS is on scene \"Main\".",
                Collections.emptyList(),
                macroSteps,
                macroChips,
                false,
                true);
    }
}
