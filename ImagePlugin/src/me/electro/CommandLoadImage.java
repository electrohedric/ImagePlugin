package me.electro;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.material.Mushroom;
import org.bukkit.material.types.MushroomBlockTexture;
import org.imgscalr.Scalr; // extremely simple imaging library. does antialiased resizing and rotating, which is all we need.

public class CommandLoadImage implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender commandSender, Command command, String c, String[] args) { //FIXME flat chunk issue, fix lighting maybe maybe, maybe do this asynchonously?
		// first, find the player and make sure it's not sent from console
		Player sender = null;
		for(Player p : Bukkit.getOnlinePlayers()) {
			if(p.getName().equals(commandSender.getName())) {
				sender = p;
			}
		}
		if(sender == null) {
			commandSender.sendMessage(ChatColor.RED + "Bro, need to send from a player.");
			return false;
			
		}

		
		// second, make sure the command is syntactically and semantically valid
		if(args.length < 3) // need at least a url, an orientation, a direction
			return false;
		String url = args[0];
		String orientation = args[1];
		String direction = args[2];
		boolean max = false;
		boolean vertical = false;
		boolean dither = false;
		boolean replace = false;
		boolean barriers = false;
		if(orientation.equalsIgnoreCase("stand")) {
			if(!direction.equalsIgnoreCase("left") && !direction.equalsIgnoreCase("right")) {
				commandSender.sendMessage(ChatColor.RED + "Orientation '" + orientation + "' must be one of 'left', 'right' (since orientation is 'stand'.)");
				return false;
			}
			vertical = true;
		} else if(orientation.equalsIgnoreCase("flat")) {
			if(!direction.equalsIgnoreCase("bottom") && !direction.equalsIgnoreCase("top")) {
				commandSender.sendMessage(ChatColor.RED + "Diretion '" + orientation + "' must be one of 'bottom', 'top' (since orientation is 'flat'.)");
				return false;
			} //vertical = false
		} else {
			commandSender.sendMessage(ChatColor.RED + "Direction '" + orientation + "' must be one of 'stand', 'flat'.");
			return false;
		}
		
		
		int widthPreferred = -1, heightPreferred = -1; // all of these are optional parameters, width and height will be maxed by default, rotation default is 0
		Scalr.Rotation rotPreferred = null;
		for(int i = 3; i < args.length; i++) { // get the rest of the args. max possibility of 3 other args
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
			} else if(keywordPair.equalsIgnoreCase("dither")) {
				dither = true;
			} else if(keywordPair.equalsIgnoreCase("replace")) {
				replace = true;
			} else if(keywordPair.equalsIgnoreCase("barriers")) {
				barriers = true;
			} else if(keywordPair.equalsIgnoreCase("max")) {
				max = true;
			} else {
				commandSender.sendMessage(ChatColor.RED + "Unknown option '" + keywordPair + "'. Should be one of 'dither', 'replace', 'barriers'. Parameters require an '=' otherwise.");
				return false;
			}
		}
		
		
		BufferedImage image = null;
		try {
			image = loadImageFromURL(url);
		} catch (MalformedURLException e) {
			commandSender.sendMessage(ChatColor.RED + "Malformed URL: '" + url + "'. Resource probably not valid.");
			return true; // not a usage error
		} catch (IOException e) {
			commandSender.sendMessage(ChatColor.RED + "IO Error: Couldn't read image. Sorry 'bout that.");
			return true; // not a usage error
		}
		
		
		if(image == null) {
			String[] validExtensions = ImageIO.getWriterFileSuffixes();
			commandSender.sendMessage(ChatColor.RED + "URL '" + url + "' does not have supported image extension. Must be one of " 
					+ Arrays.toString(validExtensions).replaceAll("\\[|\\]|\"", "") + "."); // remove all these: [ ] "
			return true; // not a usage error
		}
		
		// rotating first means that the width provided in the parameters is the fixed width in the world that the image will take up,
		// not the width of the image before it is rotated (although I'm not expecting images to be rotated often)
		if(rotPreferred != null) {
			image = Scalr.rotate(image, rotPreferred, Scalr.OP_ANTIALIAS);
		}
		int imgWidth = image.getWidth();
		int imgHeight = image.getHeight();
		
		Location playerLoc = Bukkit.getPlayer(commandSender.getName()).getLocation();
		if(max) {
			int maxHeight = vertical ? Math.min(256 - playerLoc.getBlockY(), imgHeight) : 400;
			float heightRatio = (float) imgHeight / maxHeight;
			float widthRatio = imgWidth / 400f;
			if(widthRatio > heightRatio) {
				widthPreferred = 400;
				heightPreferred = (int) (imgHeight / widthRatio + 0.5f);
			} else {
				heightPreferred = maxHeight;
				widthPreferred = (int) (imgWidth / heightRatio + 0.5f);
			}
		}
		
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
		if(widthPreferred > 400 || heightPreferred > 400) {
			commandSender.sendMessage(ChatColor.RED + "Image size too large. Resize to smaller than 400x400.");
			return true; // not a usage error
		}
		
		
		// set up common variables for location and direction
		
		float yaw = (playerLoc.getYaw() + 360) % 360; // normalize between 0-360, since rotation is modulo'd to either -360-0 or 0-360
		Location startingLoc = playerLoc.clone();
		Direction widthDir; // original direction of the iterator
		Direction heightDir; // secondary direction of the iterator
		boolean flip = false;
		
		
		// get the actual direction the player is facing which determines the direction of the iterator
		if(vertical) {
			// each of these get 80 degrees of leeway per direction. the last 10 degrees are considered in-between
			if(yaw > 320  || yaw < 40) { // +z faces 0/360
				startingLoc = startingLoc.add(0, 0, 1);
				widthDir = Direction.POS_Z;
			} else if(yaw > 50 && yaw < 130) { // -x faces 90
				startingLoc = startingLoc.add(-1, 0, 0);
				widthDir = Direction.NEG_X;
			} else if(yaw > 140 && yaw < 220) { // -z facing 180
				startingLoc = startingLoc.add(0, 0, -1);
				widthDir = Direction.NEG_Z;
			} else if(yaw > 230 && yaw < 310) { // +x faces 270
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
			if(yaw > 280 && yaw < 350) { // +x faces 270 +z faces 0/360
				startingLoc = startingLoc.add(1, 0, 1);
				widthDir = Direction.POS_Z;
				heightDir = Direction.NEG_X;
			} else if(yaw > 10 && yaw < 80) { // -x faces 90 +z faces 0/360
				startingLoc = startingLoc.add(-1, 0, 1);
				widthDir = Direction.NEG_X;
				heightDir = Direction.NEG_Z;
			} else if(yaw > 190 && yaw < 260) { // +x faces 270 -z facing 180
				startingLoc = startingLoc.add(1, 0, -1);
				widthDir = Direction.POS_X;
				heightDir = Direction.POS_Z;
			} else if(yaw > 100 && yaw < 170) { // -x faces 90 -z facing 180
				startingLoc = startingLoc.add(-1, 0, -1);
				widthDir = Direction.NEG_Z;
				heightDir = Direction.POS_X;
			} else {
				commandSender.sendMessage(ChatColor.RED + "Non-obvious facing direction. Face more toward the corner direction you want.");
				return true; // not a usage error
			}
			if(direction.equalsIgnoreCase("top")) { // going with bottom is standard, thus top is flipped
				flip = true;
			}
		}
		
		
		// test if the image is not high enough or too high up
		startingLoc = heightDir.move(startingLoc, 1 - heightPreferred); // move to the top if it is vertical or move out to the height if horizontal
		if(startingLoc.getBlockY() > 255) {
			int maxHeight = heightPreferred - startingLoc.getBlockY() + 255; // calculate maximum height to reach world limit
			int maxWidth = (int) ((float) imgWidth / imgHeight * maxHeight + 0.5f); // calculate ratio and compute new width. round to the nearest int
			commandSender.sendMessage(ChatColor.RED + String.format("That would hit the skybox. Max size at this level is %dx%d.", maxWidth, maxHeight));
			return true; // not a usage error
		} else if(playerLoc.getBlockY() <= 1) {
			commandSender.sendMessage(ChatColor.RED + String.format("That would fall into the void. Image must start at minimum Y-level 2."));
			return true; // not a usage error
		}
		
		
		// see if there is enough room in the world (i.e. the image won't destroy any blocks)
		// now that we're at the top of the image (startingLoc), we can just loop through all the image block locations
		Location loc = startingLoc.clone();
		if(!replace) { // if we are replacing then we don't care if there's blocks in the way
			int badBlocks = 0;
			for(int dH = 0; dH < heightPreferred; dH++) {
				for(int dW = 0; dW < widthPreferred; dW++) { // after each width loop
					Block b = loc.getBlock();
					if(!blockOk(b)) { // avoid solid blocks. blocks such as snow and grass should be okay to destroy
						badBlocks++;
					}
					
					loc = widthDir.move(loc, 1); // move 1 time in the width direction * widthPreferred times
				}
				loc = widthDir.move(loc, -widthPreferred); // move widthPreferred times in the negative direction * 1 time
				loc = heightDir.move(loc, 1); // move 1 time in the height direction * heightPreferred times
			}
			if(badBlocks > 0) {
				commandSender.sendMessage(ChatColor.RED + "There are " + badBlocks + " blocks in the construction path of the image. Please remove them or find another location.");
				return true; // not a usage error
			}
		}
		
		if(needsResizing) {
			image = Scalr.resize(image, Scalr.Method.QUALITY, widthPreferred, heightPreferred, Scalr.OP_ANTIALIAS); // resize to the preferred and/or calculated height
		}
		if(flip) { // flipped so it, when drawn normally, faces the direction they intend which is opposite of 'standard'
			image = Scalr.rotate(image, Scalr.Rotation.FLIP_HORZ, Scalr.OP_ANTIALIAS);
		}
		
		
		// place barrier blocks underneath the image where needed to prevent falling sand ... well ... falling (this is only needed if they will update the blocks)
		if(barriers) {
			if(vertical) { // place blocks only on the bottom ridge
				loc = playerLoc.clone();
				loc = widthDir.move(loc, 1); // move one accross to be at the width level the image actually starts
				loc = loc.add(0, -1, 0); // move one down in the Y to place the barriers below the image
				for(int dW = 0; dW < widthPreferred; dW++) { // after each width loop
					Block block = loc.getBlock();
					if(blockOk(block)) {
						block.setType(Material.BARRIER);
					}
					
					loc = widthDir.move(loc, 1); // move 1 time in the width direction * widthPreferred times
				}
			} else { // place blocks all under the entire image
				loc = startingLoc.clone();
				loc = loc.add(0, -1, 0); // move one down in the Y to place the barriers below the image
				for(int dH = 0; dH < heightPreferred; dH++) {
					for(int dW = 0; dW < widthPreferred; dW++) { // after each width loop
						Block block = loc.getBlock();
						if(blockOk(block)) {
							block.setType(Material.BARRIER);
						}
						
						loc = widthDir.move(loc, 1); // move 1 time in the width direction * widthPreferred times
					}
					loc = widthDir.move(loc, -widthPreferred); // move widthPreferred times in the negative direction * 1 time
					loc = heightDir.move(loc, 1); // move 1 time in the height direction * heightPreferred times
				}
			}
		}
		
		
		// image is ready to be dithered using a floyd-steinburg implementation and placed into the world at the same time while we gather the correct materials to use
		loc = startingLoc.clone();
		int chunksNeededLoading = 0;
		for(int dH = 0; dH < heightPreferred; dH++) {
			for(int dW = 0; dW < widthPreferred; dW++) { // after each width loop
				int rgb = image.getRGB(dW, dH);
				Color originalPixel = new Color(rgb);
				Color quantizedPixel = originalPixel.quantize();
				Material bestBlockMatch = Main.colorMap.get(quantizedPixel);
				Block block = loc.getBlock();
				Chunk chunk = block.getChunk();
				if(!chunk.isLoaded()) {
					chunk.load(true); // load the chunk if needed, generating as necessary
					chunksNeededLoading++;
				}
				loc.getBlock().setType(bestBlockMatch, false); // set the material in the world, no gravity for falling blocks
				if(bestBlockMatch == Material.BROWN_MUSHROOM_BLOCK) {
					// need to set some metadata for the mushroom
					BlockState state = loc.getBlock().getState();
					Mushroom mushroom = (Mushroom) state.getData();
					mushroom.setBlockTexture(MushroomBlockTexture.ALL_PORES); // set the mushroom to show all pores, which is the lighter texture
					state.setData(mushroom);
					state.update(false, false);
				}
				if(dither) { // if we're dithering, then just propagate the error which literally sets the pixels of the stored buffered image
					Color error = quantizedPixel.errorFrom(originalPixel);
					error.propagateError(image, dW + 1, dH, 0.4375f); // right
					error.propagateError(image, dW + 1, dH + 1, 0.0625f); // bottom-right corner
					error.propagateError(image, dW - 1, dH + 1, 0.1875f); // bottom-left corner
					error.propagateError(image, dW, dH + 1, 0.3125f); // bottom
				}
				
				loc = widthDir.move(loc, 1); // move 1 time in the width direction * widthPreferred times
			}
			loc = widthDir.move(loc, -widthPreferred); // move widthPreferred times in the negative direction * 1 time
			loc = heightDir.move(loc, 1); // move 1 time in the height direction * heightPreferred times
		}
		if(chunksNeededLoading > 0) {
			commandSender.sendMessage(ChatColor.GREEN + "Generated image successfully. Loaded " + chunksNeededLoading + " in the process.");
		} else {
			commandSender.sendMessage(ChatColor.GREEN + "Generated image successfully.");
		}
		return true; // success!
	}
	
	private boolean blockOk(Block b) {
		Material m = b.getType();
		switch(m) { // all these materials have been hand-picked because they can die
		case AIR:
		case CAVE_AIR:
		case GRASS:
		case TALL_GRASS:
		case WATER:
		case KELP:
		case KELP_PLANT:
		case LAVA:
		case SNOW:
		case SUNFLOWER:
		case ROSE_BUSH:
		case POPPY:
		case DANDELION:
		case BLUE_ORCHID:
		case ALLIUM:
		case AZURE_BLUET:
		case ORANGE_TULIP:
		case PINK_TULIP:
		case RED_TULIP:
		case WHITE_TULIP:
		case OXEYE_DAISY:
		case PEONY:
		case LILAC:
		case FERN:
		case LARGE_FERN:
		case SEAGRASS:
		case TALL_SEAGRASS:
		case DEAD_BUSH:
		case BROWN_MUSHROOM:
		case RED_MUSHROOM:
		case TORCH:
		case REDSTONE_TORCH:
		case LILY_PAD:
		case VINE:
			return true;
		default:
			return false;
		}
	}
	
	private BufferedImage loadImageFromURL(String url) throws IOException, MalformedURLException {
		// connect to the URL and see if the image can be read into ImageIO
		BufferedImage image = null;
		URL webServer = new URL(url); // connect to the URL
		URLConnection conn = webServer.openConnection();
		conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2"); //spoofy boi
		InputStream inStream = conn.getInputStream(); // open the stream
		image = ImageIO.read(inStream); // reroute it to the ImageIO library
		inStream.close();
		return image;
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
