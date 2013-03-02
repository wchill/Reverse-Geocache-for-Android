package com.androchill.reversegeocache;

import java.nio.ByteBuffer;

public class ByteConversion {
	
	public static byte[] doubleToByteArray(double value) {
		byte[] bytes = new byte[8];
		ByteBuffer.wrap(bytes).putDouble(value);
		return bytes;
	}

	public static double byteArrayToDouble(byte[] bytes) {
		return ByteBuffer.wrap(bytes).getDouble();
	}

	public static byte[] intToByteArray(int value) {
		byte[] bytes = new byte[4];
		ByteBuffer.wrap(bytes).putInt(value);
		return bytes;
	}

	public static int byteArrayToInt(byte[] bytes) {
		return ByteBuffer.wrap(bytes).getInt();
	}
	
	public static byte[] longToByteArray(long value) {
		byte[] bytes = new byte[8];
		ByteBuffer.wrap(bytes).putLong(value);
		return bytes;
	}

	public static long byteArrayToLong(byte[] bytes) {
		return ByteBuffer.wrap(bytes).getLong();
	}
	
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

	public static String byteArrayToString(byte[] bytes) {
		char[] buffer = new char[bytes.length >> 1];
		for (int i = 0; i < buffer.length; i++) {
			int bpos = i << 1;
			char c = (char) (((bytes[bpos] & 0x00FF) << 8) + (bytes[bpos + 1] & 0x00FF));
			buffer[i] = c;
		}
		return new String(buffer);
	}

	public static byte[] hexStringToByteArray(String str) {
		    int len = str.length();
		    byte[] data = new byte[len / 2];
		    for (int i = 0; i < len; i += 2) {
		        data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
		                             + Character.digit(str.charAt(i+1), 16));
		    }
		    return data;
	}
	
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
