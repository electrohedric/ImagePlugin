package me.electro;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.imgscalr.Scalr; // extremely simple imaging library. does antialiased resizing and rotating, which is all we need.

public class CommandLoadImage implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender commandSender, Command command, String c, String[] args) {
		// first, find the player and make sure it's not sent from console
		Player sender = null;
		for(Player p : Bukkit.getOnlinePlayers()) {
			if(p.getName().equals(commandSender.getName())) {
				sender = p;
			}
		}
		if(sender == null) {
			commandSender.sendMessage(ChatColor.RED + "Bro, need to send from a player.");
			commandSender.sendMessage(ChatColor.GREEN + "... but we letting this go though for sake of testing");
			//return false; // uncomment for production
		}
		
		// second, make sure the command is syntactically and semantically valid
		if(args.length < 4) // need at least a url, an orientation, a direction, and dithering
			return false;
		String url = args[0];
		String orientation = args[1];
		String direction = args[2];
		String dithering = args[3];
		boolean vertical = false;
		if(orientation.equalsIgnoreCase("stand")) {
			if(!direction.equalsIgnoreCase("left") && !direction.equalsIgnoreCase("right")) {
				commandSender.sendMessage(ChatColor.RED + "Orientation '" + orientation + "' must be one of 'left', 'right' since orientation is 'stand'.");
				return false;
			}
			vertical = true;
		} else if(orientation.equalsIgnoreCase("flat")) {
			if(!direction.equalsIgnoreCase("bottom") && !direction.equalsIgnoreCase("top")) {
				commandSender.sendMessage(ChatColor.RED + "Diretion '" + orientation + "' must be one of 'bottom', 'top' since orientation is 'flat'.");
				return false;
			} //vertical = false
		} else {
			commandSender.sendMessage(ChatColor.RED + "Direction '" + orientation + "' must be one of 'stand', 'flat'.");
			return false;
		}
		boolean dither = Boolean.parseBoolean(dithering); // returns false for anything but "true", ignoring case
		
		int widthPreferred = -1, heightPreferred = -1; // all of these are optional parameters, width and height will be maxed by default, rotation default is 0
		Scalr.Rotation rotPreferred = null;
		for(int i = 4; i < args.length; i++) { // get the rest of the args. max possibility of 3 other args
			String keywordPair = args[i];
			if(keywordPair.contains("=")) { // valid keyword pair
				String[] keywordPairArray = keywordPair.split("=");
				if(keywordPairArray.length == 2) {
					String key = keywordPairArray[0].trim();
					String value = keywordPairArray[1].trim();
					if(key.equalsIgnoreCase("w")) {
						try {
							widthPreferred = Integer.parseInt(value);
							if(widthPreferred < 1) {
								commandSender.sendMessage(ChatColor.RED + "Value '" + value + "' must be at least 1.");
								return false;
							}
						} catch(NumberFormatException nfe) {
							commandSender.sendMessage(ChatColor.RED + "Value '" + value + "' not an integer.");
							return false;
						}
					} else if(key.equalsIgnoreCase("h")) {
						try {
							heightPreferred = Integer.parseInt(value);
							if(heightPreferred < 1) {
								commandSender.sendMessage(ChatColor.RED + "Value '" + value + "' must be at least 1.");
								return false;
							}
						} catch(NumberFormatException nfe) {
							commandSender.sendMessage(ChatColor.RED + "Value '" + value + "' not an integer.");
							return false;
						}
					} else if(key.equalsIgnoreCase("r")) {
						if(value.equalsIgnoreCase("cw")) {
							rotPreferred = Scalr.Rotation.CW_90;
						} else if(value.equalsIgnoreCase("ccw")) {
							rotPreferred = Scalr.Rotation.CW_270;
						} else if(value.equalsIgnoreCase("180")) {
							rotPreferred = Scalr.Rotation.CW_180;
						} else {
							commandSender.sendMessage(ChatColor.RED + "Unrecognized value '" + value + "'. Must be one of 'cw', 'ccw', '180'.");
							return false;
						}
					} else {
						commandSender.sendMessage(ChatColor.RED + "Unknown key '" + key + "'. Should be one of 'w', 'h', 'r'.");
						return false;
					}
				} else if(keywordPairArray.length == 1) { // when '<key>=' typed
					commandSender.sendMessage(ChatColor.RED + "Expected value after " + keywordPair + ".");
					return false;
				} else { // when there are too many '='
					commandSender.sendMessage(ChatColor.RED + "Unexpected '=': " + keywordPair + ".");
					return false;
				}
			} else {
				commandSender.sendMessage(ChatColor.RED + "Parameter '" + keywordPair + "' has no key assigned. Should be one of 'w=', 'h=', 'r='.");
				return false;
			}
		}
		
		String[] validExtensions = ImageIO.getWriterFileSuffixes();
		boolean validExtension = false;
		for(String valid : validExtensions) {
			if(url.toLowerCase().endsWith("." + valid.toLowerCase())) {  // compare valid extensions to url, ignoring case
				validExtension = true;
				break;
			}
		}
		if(!validExtension) {
			commandSender.sendMessage(ChatColor.RED + "URL '" + url + "' does not have supported image extension. Must be one of " 
					+ Arrays.toString(validExtensions).replaceAll("\\[|\\]|\"", "") + "."); // remove all these: [ ] "
			return true; // not a usage error
		}
		
		// connect to the URL and see if the image can be read into ImageIO
		BufferedImage image = null;
		try {
			URL webServer = new URL(url); // connect to the URL
			URLConnection conn = webServer.openConnection();
			conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2"); //spoofy boi
			InputStream inStream = conn.getInputStream(); // open the stream
			image = ImageIO.read(inStream); // reroute it to the ImageIO library
			inStream.close();
		} catch (MalformedURLException e) {
			commandSender.sendMessage(ChatColor.RED + "Malformed URL: '" + url + "'. Resource probably not valid.");
			return true; // not a usage error
		} catch (IOException e) {
			commandSender.sendMessage(ChatColor.RED + "IO Error: Couldn't read image. Sorry 'bout that.");
			return true; // not a usage error
		}
		
		if(image == null) {
			commandSender.sendMessage(ChatColor.RED + "Couldn't read image. Sorry 'bout that.");
			return true; // not a usage error
		}
		int imgWidth = image.getWidth();
		int imgHeight = image.getHeight();
		if(widthPreferred == -1 && heightPreferred != -1) { // width case: calculate new width to resize to given height
			widthPreferred = (int) ((float) imgWidth / imgHeight * heightPreferred + 0.5f); // calculate ratio and compute new width. round to the nearest int
		} else if(widthPreferred != -1 && heightPreferred == -1) { // height case: calculate new height to resize to given width
			heightPreferred = (int) ((float) imgHeight / imgWidth * widthPreferred + 0.5f); // calculate ratio and compute new height. round to the nearest int
		}
		// the only cases: both are -1 (no size is preferred) or both are not -1 (a preferred size has been supplied)
		boolean needsResizing = widthPreferred != -1 && heightPreferred != -1;
		if(needsResizing) {
			commandSender.sendMessage(ChatColor.GRAY + String.format("Retrieved image successfully. Resized from %dx%d to %dx%d", imgWidth, imgHeight, widthPreferred, heightPreferred));
		} else { // then no adjusted width or height are supplied, so set them to the actual size of the image
			widthPreferred = imgWidth; // now these 100% refer to the width/height that will be in-game
			heightPreferred = imgHeight;
			commandSender.sendMessage(ChatColor.GRAY + String.format("Retrieved image successfully. Size is %dx%d", widthPreferred, heightPreferred));
		}
		
		
		Location playerLoc = Bukkit.getPlayer(commandSender.getName()).getLocation();
		float yaw = playerLoc.getYaw();
		Location startingLoc = playerLoc.clone();
		Direction widthDir; // original direction of the iterator
		Direction heightDir; // secondary direction of the iterator
		boolean flip = false;
		
		if(vertical) {
			// each of these get 80 degrees of leeway
			if(yaw > -40 && yaw <= 0 || yaw > 0 && yaw < -320) { // +z faces -360/0
				startingLoc = startingLoc.add(0, 0, 1);
				widthDir = Direction.POS_Z;
			} else if(yaw > -310 && yaw < -230) { // -x faces -270
				startingLoc = startingLoc.add(-1, 0, 0);
				widthDir = Direction.NEG_X;
			} else if(yaw > -220 && yaw < -140) { // -z facing -180
				startingLoc = startingLoc.add(0, 0, -1);
				widthDir = Direction.NEG_Z;
			} else if(yaw > -130 && yaw < -50) { // +x faces -90
				startingLoc = startingLoc.add(1, 0, 0);
				widthDir = Direction.POS_X;
			} else {
				commandSender.sendMessage(ChatColor.RED + "Non-obvious facing direction. Face more toward the axis direction you want.");
				return true; // not a usage error
			}
			heightDir = Direction.NEG_Y;
			if(direction.equalsIgnoreCase("left")) { // arbitrarily going with right is standard, thus left is flipped
				flip = true;
			}
		} else { // it must be "flat"
			if(yaw > -80 && yaw < -10) { // +x faces -90 +z faces -360/0
				startingLoc = startingLoc.add(1, 0, 1);
				widthDir = Direction.POS_Z;
				heightDir = Direction.NEG_X;
			} else if(yaw > -350 && yaw < -280) { // -x faces -270 +z faces -360/0
				startingLoc = startingLoc.add(-1, 0, 1);
				widthDir = Direction.NEG_X;
				heightDir = Direction.NEG_Z;
			} else if(yaw > -170 && yaw < -100) { // +x faces -90 -z facing -180
				startingLoc = startingLoc.add(1, 0, -1);
				widthDir = Direction.NEG_Z;
				heightDir = Direction.POS_X;
			} else if(yaw > -260 && yaw < -190) { // -x faces -270 -z facing -180
				startingLoc = startingLoc.add(-1, 0, -1);
				widthDir = Direction.POS_X;
				heightDir = Direction.POS_Z;
			} else {
				commandSender.sendMessage(ChatColor.RED + "Non-obvious facing direction. Face more toward the corner direction you want.");
				return true; // not a usage error
			}
			if(direction.equalsIgnoreCase("top")) { // going with bottom is standard, thus top is flipped
				flip = true;
			}
		}
		startingLoc = heightDir.move(startingLoc, 1 - heightPreferred); // move to the top if it is vertical or move out to the height if horizontal
		commandSender.sendMessage(ChatColor.LIGHT_PURPLE + String.format("Top image loc " + startingLoc));
		if(startingLoc.getBlockY() > 255) {
			int maxHeight = heightPreferred - startingLoc.getBlockY() + 255; // calculate maximum height to reach world limit
			int maxWidth = (int) ((float) imgWidth / imgHeight * maxHeight + 0.5f); // calculate ratio and compute new width. round to the nearest int
			commandSender.sendMessage(ChatColor.RED + String.format("That would hit the skybox. Max size at this level is %dx%d.", maxWidth, maxHeight));
			return true; // not a usage error
		}
		// now that we're at the top of the image (startingLoc), we can just loop through all the image block locations
		Location loc = startingLoc.clone();
		int badBlocks = 0;
		for(int dH = 0; dH < heightPreferred; dH++) {
			for(int dW = 0; dW < widthPreferred; dW++) { // after each width loop
				Block b = loc.getBlock();
				Material mat = b.getType();
				if(mat != Material.AIR && mat != Material.CAVE_AIR) { // because they added cave_air for some reason
					badBlocks++;
				}
				loc = widthDir.move(loc, 1); // move 1 time in the width direction * widthPreferred times
			}
			loc = widthDir.move(loc, -widthPreferred); // move widthPreferred times in the negative direction * 1 time
			loc = heightDir.move(loc, 1); // move 1 time in the height direction * heightPreferred times
		}
		loc = startingLoc.clone();
		// note: barrier blocks will be placed underneath the image where solid blocks are not already present to prevent sand from falling through
		// we don't need to check for this because it will be a respectful replacement only replacing air. I mean, if it's solid, it's solid
		if(badBlocks > 0) {
			commandSender.sendMessage(ChatColor.RED + "There are " + badBlocks + " blocks in the construction path of the image. Please remove them or find another location.");
			return true; // not a usage error
		}
		// but now we know that there is room in the world
		
		// rotating first means that the width provided in the parameters is the fixed width in the world that the image will take up,
		// not the width of the image before it is rotated (although I'm not expecting images to be rotated often)
		if(rotPreferred != null) {
			image = Scalr.rotate(image, rotPreferred, Scalr.OP_ANTIALIAS);
		}
		if(needsResizing) {
			image = Scalr.resize(image, Scalr.Method.QUALITY, widthPreferred, heightPreferred, Scalr.OP_ANTIALIAS); // resize to the preferred and/or calculated height
		}
		if(flip) { // flipped so it, when drawn normally, faces the direction they intend which is opposite of 'standard'
			image = Scalr.rotate(image, Scalr.Rotation.FLIP_HORZ, Scalr.OP_ANTIALIAS);
		}
		if(dither) {
			//TODO: load in the map file for blocks -> color
			InputStream is = getClass().getResourceAsStream("block_map.csv"); // TODO: yo can we load this on startup instead
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			Map<Color, String> map = new HashMap<>(); //FIXME: make this Color -> Material
			String line;
			try {
				while((line = br.readLine()) != null) {
					String[] data = line.split(",");
					if(data.length == 4) {
						int r = Integer.parseInt(data[1]);
						int g = Integer.parseInt(data[2]);
						int b = Integer.parseInt(data[3]);
						map.put(new Color(r, g, b), data[0]); //FIXME: do a reverse lookup from name to material (if not exact, then change csv file to match)
					}
				}
				is.close();
				isr.close();
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
			//TODO: compute new image with floyd-steinburg dithering and minecraft blocks
		}
		//TODO: finally paste the generated image into the world
		//TODO: and do all this from a thread probably otherwise it might lag the server?
		//TODO: place barrier underneath the blocks to prevent falling sand falling
		//TODO: finally refactor this spaghoot into several methods
		return true;
	}
	
	private enum Direction {
		POS_X, POS_Y, POS_Z, NEG_X, NEG_Y, NEG_Z;
		
		Location move(Location loc, int amount) {
			switch(this) {
			case POS_X: return loc.add(amount, 0, 0);
			case POS_Y: return loc.add(0, amount, 0);
			case POS_Z: return loc.add(0, 0, amount);
			case NEG_X: return loc.add(-amount, 0, 0);
			case NEG_Y: return loc.add(0, -amount, 0);
			case NEG_Z: return loc.add(0, 0, -amount);
			default: return null;
			}
		}
	}
}
