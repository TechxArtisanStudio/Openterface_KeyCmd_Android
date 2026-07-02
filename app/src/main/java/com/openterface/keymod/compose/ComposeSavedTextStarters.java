package com.openterface.keymod.compose;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * First-run starter snippets for KM Pro Compose Saved texts — homelab/sysadmin examples that
 * demonstrate pin, load, and send workflows for new users.
 */
public final class ComposeSavedTextStarters {

    private static final String PROD_DEPLOY =
            "cd /opt/app && git pull origin main && \\\n"
                    + "npm ci --omit=dev && \\\n"
                    + "npm run build && \\\n"
                    + "pm2 reload ecosystem.config.js --env production";

    private ComposeSavedTextStarters() {}

    @NonNull
    public static List<SavedTextItem> buildStarterItems(long baseTimeMs) {
        List<SavedTextItem> out = new ArrayList<>(7);
        out.add(item(1L, "prod-deploy", PROD_DEPLOY, true, baseTimeMs + 2, baseTimeMs));
        out.add(
                item(
                        2L,
                        "rotate-root-pass",
                        "Open1face-Demo-2026-Rotate-Me-Not-Real",
                        true,
                        baseTimeMs + 1,
                        baseTimeMs));
        out.add(
                item(
                        3L,
                        "docker-prune-safe",
                        "docker system prune -af --filter \"until=168h\" && docker volume ls",
                        false,
                        0L,
                        baseTimeMs));
        out.add(
                item(
                        4L,
                        "nas-mount-nfs",
                        "sudo mount -t nfs 192.168.11.20:/volume1/homelab /mnt/nas && df -h /mnt/nas",
                        false,
                        0L,
                        baseTimeMs));
        out.add(
                item(
                        5L,
                        "k8s-rollout-status",
                        "kubectl -n homelab rollout status deployment/grafana --timeout=120s",
                        false,
                        0L,
                        baseTimeMs));
        out.add(
                item(
                        6L,
                        "ufw-allow-ssh",
                        "sudo ufw allow OpenSSH && sudo ufw status",
                        false,
                        0L,
                        baseTimeMs));
        out.add(
                item(
                        7L,
                        "journalctl-boot-err",
                        "journalctl -b -p err --no-pager | tail -n 50",
                        false,
                        0L,
                        baseTimeMs));
        return out;
    }

    @NonNull
    private static SavedTextItem item(
            long id,
            @NonNull String title,
            @NonNull String content,
            boolean pinned,
            long pinnedAt,
            long timestamp) {
        SavedTextItem it = new SavedTextItem();
        it.id = id;
        it.title = title;
        it.content = content;
        it.pinned = pinned;
        it.pinnedAt = pinned ? pinnedAt : 0L;
        it.createdAt = timestamp;
        it.updatedAt = timestamp;
        return it;
    }
}
