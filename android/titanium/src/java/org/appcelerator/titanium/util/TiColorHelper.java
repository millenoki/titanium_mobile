/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.util;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.appcelerator.titanium.TiApplication;

import android.graphics.Color;

/**
 * This class contain utility methods that converts a String color, like "red", into its corresponding RGB/RGBA representation.
 */
public class TiColorHelper
{
	static Pattern shortHexPattern = Pattern.compile("#([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f]?)");
	static Pattern rgbPattern = Pattern.compile("(rgb|rgba|argb)\\((\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*(\\d+(?:\\.\\d+)?))?\\)");

	static Boolean hexColorUsesRGBA = null;
	
	static boolean hexColorUsesRGBA() {
	    if (hexColorUsesRGBA == null) {
	        hexColorUsesRGBA = TiApplication.getInstance().getAppProperties().getString("ti.hexColorFormat", "argb").equals("rgba");
	    }
	    return hexColorUsesRGBA;
	}

	private static final String TAG = "TiColorHelper";
	private static HashMap<String, Integer> colorTable;
	
    public static int parseHexColor(String lowval) {
        final int length = lowval.length();
        long alpha = 255;
        long value = new BigInteger(lowval, 16).longValue();
        long red = 0;
        long green = 0;
        long blue = 0;

        if (length < 6)
        {
            value = ((value & 0xF000) << 16) |
            ((value & 0xFF00) << 12) |
            ((value & 0xFF0) << 8) |
            ((value & 0xFF) << 4) |
            (value & 0xF);
        }
        
        if (hexColorUsesRGBA()) {
            if((length % 4)==0)
            {
                red = (value >> 24) & 0xFF;
                green = (value >> 16) & 0xFF;
                blue = (value >> 8) & 0xFF;
                alpha = (value & 0xFF);
            } else {
                red = (value >> 16) & 0xFF;
                green = (value >> 8) & 0xFF;
                blue = value & 0xFF;
            }
        } else {
            if((length % 4)==0)
            {
                alpha = ((value >> 24) & 0xFF);
            }
            
            red = (value >> 16) & 0xFF;
            green = (value >> 8) & 0xFF;
            blue = value & 0xFF;
        }
        return Color.argb((int) alpha, (int) red, (int) green, (int) blue);
    }
	/**
	 * Convert string representations of colors, like "red" into the corresponding RGB/RGBA representation.
	 * @param value the color value to convert. For example, "red".
	 * @return the RGB/RGBA representation (int) of the color.
	 */
	public static int parseColor(String value) {
		int color = Color.TRANSPARENT;
		if (value != null) {
			String lowval = value.trim().toLowerCase();
			Matcher m = null;
			if (lowval.startsWith("#")) {
			   color = parseHexColor(lowval.substring(1));
			} else if ((m = rgbPattern.matcher(lowval)).matches()) {
			    String first = m.group(1);
                boolean argb = first.equalsIgnoreCase("argb");
                boolean rgba = first.equalsIgnoreCase("rgba");
			    if (argb) {
				color = Color.argb(
                            (int) (Float.valueOf(m.group(2))*255.0f),
                            Integer.valueOf(m.group(3)),
						Integer.valueOf(m.group(4)),
                            Integer.valueOf(m.group(5))
						);
                } else if (rgba) {
			    color = Color.argb(
                            (int) (Float.valueOf(m.group(5))*255.0f),
			            Integer.valueOf(m.group(2)),
                            Integer.valueOf(m.group(3)),
                            Integer.valueOf(m.group(4))
			            );
                } else {
                    color = Color.rgb(
                            Integer.valueOf(m.group(2)),
                            Integer.valueOf(m.group(3)),
                            Integer.valueOf(m.group(4))
			            );
                }
				
			} else {
				try {
					if (colorTable == null) {
						buildColorTable();
					}
					if (colorTable.containsKey(lowval)) {
                        return colorTable.get(lowval);
					}
		            color = parseHexColor(lowval);
			    } catch (IllegalArgumentException e) {
				}
			}
		    
		}
		return color;
	}

	private static void buildColorTable() {
		synchronized(TiColorHelper.class) {
			colorTable = new HashMap<String, Integer>(20);

			colorTable.put("black", Color.BLACK);
			colorTable.put("red", Color.RED);
			colorTable.put("purple", Color.rgb(0x80, 0, 0x80));
			colorTable.put("orange", Color.rgb(0xff, 0x80, 0));
			colorTable.put("gray", Color.GRAY);
			colorTable.put("darkgray", Color.DKGRAY);
			colorTable.put("lightgray", Color.LTGRAY);
			colorTable.put("cyan", Color.CYAN);
			colorTable.put("magenta",Color.MAGENTA);
			colorTable.put("transparent", Color.TRANSPARENT);
			colorTable.put("aqua", Color.rgb(0, 0xff, 0xff));
			colorTable.put("fuchsia", Color.rgb(0xff, 0, 0xff));
			colorTable.put("lime", Color.rgb(0, 0xff, 0));
			colorTable.put("maroon", Color.rgb(0x88,0 ,0x88));
			colorTable.put("pink", Color.rgb(0xff,0xc0, 0xcb));
			colorTable.put("navy", Color.rgb(0, 0, 0x80));
			colorTable.put("silver", Color.rgb(0xc0, 0xc0, 0xc0));
			colorTable.put("olive", Color.rgb(0x80, 0x80, 0));
			colorTable.put("teal", Color.rgb(0x0, 0x80, 0x80));
			colorTable.put("brown", Color.rgb(0x99, 0x66, 0x33));
            colorTable.put("white", Color.WHITE);
            colorTable.put("green", Color.GREEN);
            colorTable.put("blue", Color.BLUE);
            colorTable.put("yellow", Color.YELLOW);
		}
	}
	
	public static String toHexString(final int color) {
	    int alpha = Color.alpha(color);
	    int red = Color.red(color);
	    int green = Color.green(color);
	    int blue = Color.blue(color);
	    if (hexColorUsesRGBA()) {
	        return String.format("#%02x%02x%02x%02x",red, green, blue, alpha);
	    } else {
	        return String.format("#%02x%02x%02x%02x", alpha,red, green, blue);
}
	}
}
