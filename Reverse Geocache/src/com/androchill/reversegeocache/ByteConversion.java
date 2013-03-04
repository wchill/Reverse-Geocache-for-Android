package com.androchill.reversegeocache;

import java.nio.ByteBuffer;

/**
 * A helper class for easily converting between byte arrays
 * and different variable types.
 */

public class ByteConversion {
	
	/**
	 * Converts a double to a byte array.
	 *
	 * @param value the number to convert
	 * @return a byte array converted from the given number
	 */
	
	public static byte[] doubleToByteArray(double value) {
		byte[] bytes = new byte[8];
		ByteBuffer.wrap(bytes).putDouble(value);
		return bytes;
	}

	/**
	 * Converts a byte array to a double.
	 *
	 * @param bytes the byte array to convert
	 * @return a double converted from the byte array
	 */
	
	public static double byteArrayToDouble(byte[] bytes) {
		return ByteBuffer.wrap(bytes).getDouble();
	}

	/**
	 * Converts an int to a byte array.
	 *
	 * @param value the number to convert
	 * @return a byte array converted from the given number
	 */
	
	public static byte[] intToByteArray(int value) {
		byte[] bytes = new byte[4];
		ByteBuffer.wrap(bytes).putInt(value);
		return bytes;
	}

	/**
	 * Converts a byte array to an int.
	 *
	 * @param bytes the byte array to convert
	 * @return a int converted from the byte array
	 */
	
	public static int byteArrayToInt(byte[] bytes) {
		return ByteBuffer.wrap(bytes).getInt();
	}
	
	/**
	 * Converts a long to a byte array.
	 *
	 * @param value the number to convert
	 * @return a byte array converted from the given number
	 */
	
	public static byte[] longToByteArray(long value) {
		byte[] bytes = new byte[8];
		ByteBuffer.wrap(bytes).putLong(value);
		return bytes;
	}

	/**
	 * Converts a byte array to a long.
	 *
	 * @param bytes the byte array to convert
	 * @return a long converted from the byte array
	 */
	
	public static long byteArrayToLong(byte[] bytes) {
		return ByteBuffer.wrap(bytes).getLong();
	}
	
	/**
	 * Converts a String into a byte array.
	 *
	 * @param str the String to convert
	 * @return a byte[] containing the converted String
	 */
	
	public static byte[] stringToByteArray(String str) {
		byte[] b = new byte[str.length() << 1];
		for (int i = 0; i < str.length(); i++) {
			char strChar = str.charAt(i);
			int bpos = i << 1;
			b[bpos] = (byte) ((strChar & 0xFF00) >> 8);
			b[bpos + 1] = (byte) (strChar & 0x00FF);
		}
		return b;
	}

	/**
	 * Converts a byte array to a String representation.
	 *
	 * @param bytes the byte array to convert
	 * @return a String containing the converted array
	 */
	
	public static String byteArrayToString(byte[] bytes) {
		char[] buffer = new char[bytes.length >> 1];
		for (int i = 0; i < buffer.length; i++) {
			int bpos = i << 1;
			char c = (char) (((bytes[bpos] & 0x00FF) << 8) + (bytes[bpos + 1] & 0x00FF));
			buffer[i] = c;
		}
		return new String(buffer);
	}

	/**
	 * Converts a hexadecimal String to a byte array.
	 *
	 * @param str the String to convert
	 * @return a byte[] containing the converted String
	 */
	
	public static byte[] hexStringToByteArray(String str) {
		    int len = str.length();
		    byte[] data = new byte[len / 2];
		    for (int i = 0; i < len; i += 2) {
		        data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
		                             + Character.digit(str.charAt(i+1), 16));
		    }
		    return data;
	}
	
	/**
	 * Converts a byte array to a String representation in hexadecimal.
	 *
	 * @param bytes the byte array to convert
	 * @return a String containing a hexadecimal representation of the array
	 */
	
	public static String byteArrayToHexString(byte[] bytes) {
	    final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	    char[] hexChars = new char[bytes.length * 2];
	    int v;
	    for ( int j = 0; j < bytes.length; j++ ) {
	        v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
}
