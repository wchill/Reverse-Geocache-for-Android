package com.androchill.reversegeocache;


public class IOIOCommand {
	private Object arg;
	private int command;
	
	/**
	 * Constructor to use when wrapping an IOIO command with an argument.
	 *
	 * @param  cmd  an integer representing a command to be sent to IOIO
	 * @param  arg0 an Object used as an argument for the command
	 */
	
	public IOIOCommand(int cmd, Object arg0) {
		arg = arg0;
		command = cmd;
	}
	
	/**
	 * Convenience constructor for no-argument IOIO commands
	 *
	 * @param  cmd  an integer representing a command to be sent to IOIO
	 */
	
	public IOIOCommand(int cmd) {
		this(cmd, null);
	}
	
	/**
	 * Constructor to use when wrapping an IOIO command with an integer argument.
	 *
	 * @param  cmd  an integer representing a command to be sent to IOIO
	 * @param  num  an integer used as an argument for the command
	 */
	
	public IOIOCommand(int cmd, int num) {
		this(cmd, Integer.valueOf(num));
	}
	
	/**
	 * Constructor to use when wrapping an IOIO command with a double argument.
	 *
	 * @param  cmd  an integer representing a command to be sent to IOIO
	 * @param  num  a double used as an argument for the command
	 */
	
	public IOIOCommand(int cmd, double num) {
		this(cmd, Double.valueOf(num));
	}
	
	/**
	 * Constructor to use when wrapping an IOIO command with a boolean argument.
	 *
	 * @param  cmd  an integer representing a command to be sent to IOIO
	 * @param  b    a boolean used as an argument for the command
	 */
	
	public IOIOCommand(int cmd, boolean b) {
		this(cmd, Boolean.valueOf(b));
	}
	
	/**
	 * Constructor to use when wrapping an IOIO command with a long argument.
	 *
	 * @param  cmd  an integer representing a command to be sent to IOIO
	 * @param  num  a long used as an argument for the command
	 */
	
	public IOIOCommand(int cmd, long num) {
		this(cmd, Long.valueOf(num));
	}
	
	/**
	 * Gets the integer representing this instance's IOIO command.
	 *
	 * @return the integer representing the IOIO command for this instance
	 */
	
	public int getCommand() {
		return command;
	}
	
	/**
	 * Gets the boolean argument for this IOIOCommand.
	 *
	 * @return the boolean argument for this instance
	 * @throws ClassCastException if argument is not a boolean
	 */
	
	public boolean getBoolean() throws ClassCastException {
		if(arg instanceof Boolean) return ((Boolean)arg).booleanValue();
		else throw new ClassCastException();
	}
	
	/**
	 * Gets the integer argument for this IOIOCommand.
	 *
	 * @return the integer argument for this instance
	 * @throws ClassCastException if argument is not an integer
	 */
	
	public int getInt() throws ClassCastException {
		if(arg instanceof Integer) return ((Integer)arg).intValue();
		else throw new ClassCastException();
	}
	
	/**
	 * Gets the double argument for this IOIOCommand.
	 *
	 * @return the double argument for this instance
	 * @throws ClassCastException if argument is not a double
	 */
	
	public double getDouble() throws ClassCastException {
		if(arg instanceof Double) return ((Double)arg).doubleValue();
		else throw new ClassCastException();
	}
	
	/**
	 * Gets the long argument for this IOIOCommand.
	 *
	 * @return the long argument for this instance
	 * @throws ClassCastException if argument is not a long
	 */
	
	public long getLong() throws ClassCastException {
		if(arg instanceof Long) return ((Long)arg).longValue();
		else throw new ClassCastException();
	}
	
	/**
	 * Gets the String argument for this IOIOCommand.
	 *
	 * @return the String argument for this instance
	 * @throws ClassCastException if argument is not a String
	 */
	
	public String getString() throws ClassCastException {
		if(arg instanceof String) return (String)arg;
		else throw new ClassCastException();
	}
	
	/**
	 * Gets the object argument for this IOIOCommand.
	 *
	 * @return the object argument for this instance
	 */
	
	public Object getObject() {
		return arg;
	}
}
