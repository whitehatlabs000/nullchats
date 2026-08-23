package com.voidchats.admin;

import java.util.concurrent.atomic.AtomicBoolean;

public class MaintenanceManager {
    // Inicia apagado (false)
    private static final AtomicBoolean maintenanceMode = new AtomicBoolean(false);

    public static boolean isMaintenanceModeEnabled() {
        return maintenanceMode.get();
    }

    public static void setMaintenanceMode(boolean enabled) {
        maintenanceMode.set(enabled);
    }
}