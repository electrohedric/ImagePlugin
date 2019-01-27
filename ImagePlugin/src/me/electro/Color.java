package me.electro;

import java.awt.image.BufferedImage;

public class Color {

	public float r, g, b;
	
	public Color(int rgb) {
		this.r = (rgb & 0xff0000) >> 16;
		this.g = (rgb & 0x00ff00) >> 8;
		this.b = (rgb & 0x0000ff) >> 0;
	}
	
	public Color(float r, float g, float b) {
		this.r = r;
		this.g = g;
		this.b = b;
	}
	
	public Color quantize() {
		Color recordHolder = null; // this is impossible to stay null, so we don't have to worry about null pointers
		float recordDistance = Float.MAX_VALUE; // obviously everything else has to be smaller
		for(Color guess : Main.colorMap.keySet()) { // loop through all possible block colors to get the closest one to this color
			// distance is calculated with the euclidian distance adjusted for perceived lightness and sensitivity
			float guessDistance = (float) (Math.pow((guess.r - r) * 0.3, 2) + Math.pow((guess.g - g) * 0.59, 2) + Math.pow((guess.b - b) * 0.11, 2));
			if(guessDistance < recordDistance) { // run a minimize function on the color to get the closest distance
				recordDistance = guessDistance;
				recordHolder = guess;
			}
		}
		return recordHolder;
	}
	
	public void putImage(BufferedImage img, int x, int y) {
		img.setRGB(x, y, ((int) r << 16) | ((int) g << 8) | ((int) b)); //convert to a single int and place in the image at x, y
	}
	
	public Color errorFrom(Color compare) {
		return new Color(compare.r - r, compare.g - g, compare.b - b); // this is a special type of color I'm calling a delta color. Should not use this for anything but extracting r, g, b
	}
	
	private void clamp() {
		r = Math.min(Math.max(r, 0), 255);
		g = Math.min(Math.max(g, 0), 255);
		b = Math.min(Math.max(b, 0), 255);
	}
	
	public void propagateError(BufferedImage img, int nx, int ny, float part) { // used in floyd-steinburg algorithm
		if(0 <= nx && nx < img.getWidth() && 0 <= ny && ny < img.getHeight()) {
			Color unfixedPixel = new Color(img.getRGB(nx, ny));
			Color fixedPixel = new Color(unfixedPixel.r + r * part, unfixedPixel.g + g * part, unfixedPixel.b + b * part);
			fixedPixel.clamp(); // fix pixels between 0 and 255
			fixedPixel.putImage(img, nx, ny);
		}
	}
	
	public String toString() {
		return String.format("%d, %d, %d", (int) r, (int) g, (int) b);
	}
}
