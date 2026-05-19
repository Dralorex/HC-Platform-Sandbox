package com.hollowcrown.hub.integration;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class HubIronLegacySupport {

    private HubIronLegacySupport() {
    }

    public static Optional<IronLegacyTemplateAdmin> ilAdmin() {
        RegisteredServiceProvider<IronLegacyTemplateAdmin> reg =
                Bukkit.getServicesManager().getRegistration(IronLegacyTemplateAdmin.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}
