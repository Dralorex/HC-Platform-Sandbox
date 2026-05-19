package com.hollowcrown.hub.command;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/** Extra messaging when Iron Legacy admin actions fail (CLI + GUIs). */
public final class HubIlAdminHints {

    private HubIlAdminHints() {
    }

    public static void explainPublishFailure(CommandSender sender, IronLegacyTemplateAdmin admin, String keyRaw) {
        String key = keyRaw == null ? "" : keyRaw.trim();
        if (key.isEmpty()) {
            return;
        }
        boolean known = admin.listTemplates().stream().anyMatch(r -> r.key().equalsIgnoreCase(key));
        if (!known) {
            sender.sendMessage(Component.text(
                    "No Iron Legacy row matching \"" + key + "\" (world folder or yaml key). "
                            + "/hch template worlds list · importhub adds Hub maps from templates.yml.",
                    NamedTextColor.GRAY));
            return;
        }
        String worldName = "";
        for (var r : admin.listTemplates()) {
            if (r.key().equalsIgnoreCase(key)) {
                worldName = r.worldName();
                break;
            }
        }
        sender.sendMessage(Component.text(
                "Snapshot step failed: the world folder for \"" + worldName + "\" must exist under the server directory "
                        + "(same as a normal Bukkit world). Load the template world once, then publish again.",
                NamedTextColor.GRAY));
    }
}
