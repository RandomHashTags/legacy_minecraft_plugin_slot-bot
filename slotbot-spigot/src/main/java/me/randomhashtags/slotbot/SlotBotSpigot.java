package me.randomhashtags.slotbot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlotBotSpigot extends JavaPlugin {

    public static SlotBotSpigot getPlugin;
    public boolean placeholderapi;

    @Override
    public void onEnable() {
        getPlugin = this;
        getCommand("slotbot").setExecutor(SlotBotAPI.INSTANCE);
        enable();
    }

    @Override
    public void onDisable() {
        disable();
    }

    public void enable() {
        saveDefaultConfig();
        placeholderapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        CustomItemsAPI.INSTANCE.load();
        SlotBotAPI.INSTANCE.load();
    }
    public void disable() {
        placeholderapi = false;
        CustomItemsAPI.INSTANCE.unload();
        SlotBotAPI.INSTANCE.unload();
    }

    public void reload() {
        disable();
        enable();
    }
}