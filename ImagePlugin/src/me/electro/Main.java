package me.electro;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

	@Override
	public void onEnable() {
		getLogger().info("ImageLoader enabled.");
		getCommand("loadimage").setExecutor(new CommandLoadImage());
	}
	
	@Override
	public void onDisable() {
		getLogger().info("ImageLoader disabled.");
	}
}
