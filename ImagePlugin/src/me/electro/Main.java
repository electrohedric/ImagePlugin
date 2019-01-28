package me.electro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
	
	protected static Map<Color, Material> colorMap = new HashMap<>();

	@Override
	public void onEnable() {
		loadCompressedBlocks();
		getCommand("loadimage").setExecutor(new CommandLoadImage());
		getLogger().info("ImageLoader successfully enabled.");
	}
	
	@Override
	public void onDisable() {
		getLogger().info("ImageLoader disabled.");
	}
	
	private boolean loadBlockData(String[] data) {
		float r = Float.parseFloat(data[1]); // parse the RGB values from the CSV file
		float g = Float.parseFloat(data[2]);
		float b = Float.parseFloat(data[3]);
		Color c = new Color(r, g, b);
		Material m = Material.matchMaterial(data[0]); // match the string name of the block to a Material for placing
		if(m != null) {
			colorMap.put(c, m); // put it in the hashmap if it found it
			return true;
		} else {
			System.out.println("Material unmapped! : " + data[0]);
			return false;
		}
	}
	
	private void loadCompressedBlocks() {
		InputStream is = Main.class.getResourceAsStream("/res/block_map.csv"); // open block -> color file
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);
		String line;
		int mapped = 0;
		int unmapped = 0;
		try {
			while((line = br.readLine()) != null) { // read each line
				String[] data = line.split(",");
				if(data.length == 4) {
					if(loadBlockData(data)) // load each line as a block
						mapped++;
					else
						unmapped++;
				}
			}
			is.close(); // close all streams
			isr.close();
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
		getLogger().info(mapped + " mapped textures.");
		getLogger().info(unmapped + " unmapped textures.");
	}
}
