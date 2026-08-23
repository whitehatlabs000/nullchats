package com.voidchats;

public class AppPrivateConfig {
    // Variables estáticas y privadas
    private static String messagingMasterKey;

    // Setters (Solo se usan al iniciar la app desde el Listener)
    public static void setMessagingMasterKey(String key) { messagingMasterKey = key; }

    // Getters (Para que tus Servlets los usen)
    public static String getMessagingMasterKey() { return messagingMasterKey; }
}