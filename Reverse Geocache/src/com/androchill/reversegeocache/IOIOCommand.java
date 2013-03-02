package com.androchill.reversegeocache;

public class IOIOCommand {
	private Object arg;
	private int command;
	
	public IOIOCommand(int cmd, Object arg0) {
		arg = arg0;
		command = cmd;
	}
	
	public IOIOCommand(int cmd) {
		this(cmd, null);
	}
	
	public IOIOCommand(int cmd, int num) {
		this(cmd, Integer.valueOf(num));
	}
	
	public IOIOCommand(int cmd, double num) {
		this(cmd, Double.valueOf(num));
	}
	
	public IOIOCommand(int cmd, boolean b) {
		this(cmd, Boolean.valueOf(b));
	}
	
	public IOIOCommand(int cmd, long num) {
		this(cmd, Long.valueOf(num));
	}
	
	public int getCommand() {
		return command;
	}
	
	public boolean getBoolean() throws ClassCastException {
		if(arg instanceof Boolean) return ((Boolean)arg).booleanValue();
		else throw new ClassCastException();
	}
	
	public int getInt() throws ClassCastException {
		if(arg instanceof Integer) return ((Integer)arg).intValue();
		else throw new ClassCastException();
	}
	
	public double getDouble() throws ClassCastException {
		if(arg instanceof Double) return ((Double)arg).doubleValue();
		else throw new ClassCastException();
	}
	
	public long getLong() throws ClassCastException {
		if(arg instanceof Long) return ((Long)arg).longValue();
		else throw new ClassCastException();
	}
	
	public String getString() throws ClassCastException {
		if(arg instanceof String) return (String)arg;
		else throw new ClassCastException();
	}
	
	public Object getObject() {
		return arg;
	}
}
