package com.androchill.reversegeocache;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.DigitalOutput.Spec.Mode;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.TwiMaster;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOFragmentActivity;

import java.io.UnsupportedEncodingException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class ReverseGeocache extends IOIOFragmentActivity implements
		OnClickListener, DialogListener {

	// 64 bit key used for encrypting/decrypting update data
	// should remain constant across all app revisions, as
	// security isn't a huge issue for this application
	private static final long ENC_KEY = 0x635BFCB399D050EAL;

	// instance variables
	private double batteryVoltage;
	private boolean ioioConnected = false;

	// UI elements
	private ProgressBar batteryBar;
	private TextView batteryPercentage;
	private TextView btStatus;
	private ImageView btImage;
	private TextView gpsStatus;
	private ImageView gpsImage;
	private TextView connectionStatus;
	private TextView attemptsStatus;
	private TextView lockStatus;
	private ImageView lockImage;
	private Button unlockButton;
	private TextView serialText;
	private boolean btConnected = false;
	private boolean btIconState = false;
	private boolean btDisabled = true;
	private boolean gpsConnected = false;
	private boolean gpsIconState = false;
	private boolean gpsDisabled = true;
	private boolean enableButton = false;

	// Puzzle variables
	private long boxSerial = Long.MIN_VALUE;
	private int[] attempts = new int[] { 0, 1 };
	private boolean solved = false;
	private boolean unlocked = false;
	private Location gpsLocation = new Location("empty"); // (0,0)
	private int resetPin = 0; // default pin 0000

	// Timers
	private IconFlashTimer iconFlashTimer;
	private BatteryTimer batteryTimer;
	private ConnectTimer connectTimer;

	// Location variables
	private Location currentLocation;
	private LocationManager locationManager;
	private LocationListener locationListener;

	// IOIO command queue
	ArrayBlockingQueue<IOIOCommand> ioioCommands = new ArrayBlockingQueue<IOIOCommand>(
			100, true);

	// constants
	// IOIO commands
	private static final int COMMAND_WRITE_SOLVED = 0;
	private static final int COMMAND_WRITE_UNLOCKED = 1;
	private static final int COMMAND_WRITE_ATTEMPTS = 2;
	private static final int COMMAND_WRITE_MAX_ATTEMPTS = 3;
	private static final int COMMAND_WRITE_LATITUDE = 4;
	private static final int COMMAND_WRITE_LONGITUDE = 5;
	private static final int COMMAND_WRITE_RADIUS = 6;
	private static final int COMMAND_WRITE_RESET_PIN = 7;
	private static final int COMMAND_WRITE_VERSION = 8;
	private static final int COMMAND_RESET = 9;
	private static final int COMMAND_POWER_OFF = 10;
	private static final int COMMAND_UI_DISMISS_DIALOG = 11;
	private static final int COMMAND_WRITE_SERIAL = 12;
	private static final int COMMAND_FLASH_DATA = 13;

	// Enable request code constants
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_ENABLE_GPS = 2;

	@Override
	protected IOIOLooper createIOIOLooper() {
		return new HardwareLooper();
	}

	/**
	 * Decrypts a String given a {@link SecretKey}. This method is blocking and
	 * should not be called on the UI thread.
	 * 
	 * @param secret
	 *            the SecretKey to use to decrypt
	 * @param ciphertext
	 *            the array containing the ciphertext
	 * @param iv
	 *            the array containing the initialization vectors
	 * @return the decrypted String
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeyException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws UnsupportedEncodingException
	 * @throws InvalidAlgorithmParameterException
	 */

	private String decryptString(SecretKey secret, byte[] ciphertext, byte[] iv)
			throws UnsupportedEncodingException, IllegalBlockSizeException,
			BadPaddingException, InvalidKeyException,
			InvalidAlgorithmParameterException, NoSuchAlgorithmException,
			NoSuchPaddingException {
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
		String plaintext = new String(cipher.doFinal(ciphertext), "UTF-8");
		return plaintext;
	}

	/**
	 * Creates a {@link SecretKey} given a password and a salt. This method is
	 * blocking and should not be called on the UI thread.
	 * 
	 * @param password
	 *            a char array containing a password
	 * @param salt
	 *            a byte array containing a salt
	 * @return a SecretKey object containing the secret key
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 */

	private SecretKey deriveKey(char[] password, byte[] salt)
			throws NoSuchAlgorithmException, InvalidKeySpecException {
		SecretKeyFactory factory = SecretKeyFactory
				.getInstance("PBKDF2WithHmacSHA1");
		KeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
		SecretKey tmp = factory.generateSecret(spec);
		SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
		return secret;
	}

	/**
	 * Enables or disables UI elements. Runs on UI thread.
	 * 
	 * @param enable
	 *            whether to enable UI elements or not.
	 */

	private void enableUi(final boolean enable) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// TODO: double check viability
				if (enable) {
					batteryBar.setVisibility(View.VISIBLE);
					btImage.setImageResource(R.drawable.ic_action_bluetooth_connected);
					btConnected = true;
					btStatus.setText(R.string.connected);
					connectionStatus.setText(R.string.ok);
					if (!solved)
						attemptsStatus.setText((attempts[1] - attempts[0])
								+ " remaining");
					else
						attemptsStatus.setText(R.string.solved);
					lockStatus.setText((unlocked ? R.string.unlocked
							: R.string.locked));
					lockImage
							.setImageResource((unlocked ? R.drawable.ic_action_unlock
									: R.drawable.ic_action_lock));
					serialText.setText(String.valueOf(boxSerial));
					if (!enableButton)
						enableButton = true;
					else
						unlockButton.setEnabled(true);
				} else {
					connectionStatus.setText(R.string.unavailable);
					batteryPercentage.setText(R.string.unavailable);
					attemptsStatus.setText(R.string.unavailable);
					lockStatus.setText(R.string.unavailable);
					btStatus.setText(R.string.disconnected);
					unlockButton.setEnabled(false);
					batteryBar.setVisibility(View.INVISIBLE);
					btImage.setImageResource(R.drawable.ic_action_bluetooth);
					lockImage.setImageResource(R.drawable.ic_action_lock);
				}
			}
		});
	}

	/**
	 * Encrypts a String given a {@link SecretKey}. This method is blocking and
	 * should not be called on the UI thread.
	 * 
	 * @param secret
	 *            the SecretKey to use to encrypt
	 * @param plaintext
	 *            the String to encrypt
	 * @return an array containing two byte arrays containing the ciphertext and
	 *         initialization vectors
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeyException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws UnsupportedEncodingException
	 * @throws InvalidParameterSpecException
	 */

	private byte[][] encryptString(SecretKey secret, String plaintext)
			throws NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, UnsupportedEncodingException,
			InvalidParameterSpecException {
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, secret);
		AlgorithmParameters params = cipher.getParameters();
		byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
		byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));
		return new byte[][] { ciphertext, iv };
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode != RESULT_CANCELED) {
				btImage.setImageResource(R.drawable.ic_action_bluetooth_searching);
				btStatus.setText(R.string.searching);
				btDisabled = false;
			}
			break;
		case REQUEST_ENABLE_GPS:
			LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			if (!locationManager
					.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				promptGpsEnable();
			} else {
				gpsImage.setImageResource(R.drawable.ic_action_gps_searching);
				gpsStatus.setText(R.string.searching);
				gpsDisabled = false;
			}
			break;
		}
	}

	@SuppressLint("NewApi")
	@Override
	public void onClick(View v) {

		// if puzzle is unsolved and attempts remain
		if (attempts[0] < attempts[1] && !solved) {
			attempts[0]++;
			attemptsStatus.setText((attempts[1] - attempts[0]) + " remaining");

			// check distance between current and target location
			// if less than desired radius, unlock box and mark solved
			float dist = gpsLocation.distanceTo(currentLocation);
			if (dist <= gpsLocation.getAccuracy()) {
				solved = true;
				try {
					ioioCommands
							.put(new IOIOCommand(COMMAND_WRITE_SOLVED, true));
					ioioCommands.put(new IOIOCommand(COMMAND_WRITE_ATTEMPTS,
							attempts[0]));
					ioioCommands.put(new IOIOCommand(COMMAND_WRITE_UNLOCKED,
							true));
					ioioCommands.put(new IOIOCommand(COMMAND_POWER_OFF));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				Toast.makeText(this, "Distance: " + dist + "km",
						Toast.LENGTH_SHORT).show();
			}
		} else if (solved) {
			// somehow the box was locked but the puzzle
			// was already solved, so unlock box again
			try {
				ioioCommands.put(new IOIOCommand(COMMAND_WRITE_UNLOCKED, true));
				ioioCommands.put(new IOIOCommand(COMMAND_POWER_OFF));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else if (attempts[0] >= attempts[1]) {
			AlertDialog.Builder builder = (android.os.Build.VERSION.SDK_INT < 11) ? new AlertDialog.Builder(
					this) : new AlertDialog.Builder(this,
					AlertDialog.THEME_HOLO_DARK);
			builder.setTitle(R.string.out_of_attempts)
					.setMessage(R.string.locked_forever)
					.setNeutralButton(R.string.ok, null);
		}
	}

	@SuppressLint("InlinedApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set the layout and theme of activity
		setContentView(R.layout.activity_reverse_geocache);
		setTheme((android.os.Build.VERSION.SDK_INT < 11) ? android.R.style.Theme_Light
				: android.R.style.Theme_Holo_Light);

		// Initialize timers
		batteryTimer = new BatteryTimer(120000, 5000);
		connectTimer = new ConnectTimer(20000, 5000);

		// Save handles to UI elements for later use
		batteryBar = (ProgressBar) findViewById(R.id.battery_bar);
		batteryPercentage = (TextView) findViewById(R.id.battery_status_text);

		btStatus = (TextView) findViewById(R.id.bluetooth_status_text);
		gpsStatus = (TextView) findViewById(R.id.gps_status_text);
		connectionStatus = (TextView) findViewById(R.id.connection_status_text);
		attemptsStatus = (TextView) findViewById(R.id.attempts_status_text);
		lockStatus = (TextView) findViewById(R.id.lock_status_text);
		serialText = (TextView) findViewById(R.id.serial_number);

		unlockButton = (Button) findViewById(R.id.attempt_unlock_button);
		unlockButton.setOnClickListener(this);

		btImage = (ImageView) findViewById(R.id.bluetooth_status_icon);
		gpsImage = (ImageView) findViewById(R.id.gps_status_icon);
		lockImage = (ImageView) findViewById(R.id.lock_status_icon);

		// Enable Bluetooth
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else {
			btImage.setImageResource(R.drawable.ic_action_bluetooth_searching);
			btStatus.setText(R.string.searching);
			btDisabled = false;
		}

		// Check for GPS
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			promptGpsEnable();
		} else {
			gpsImage.setImageResource(R.drawable.ic_action_gps_searching);
			gpsStatus.setText(R.string.searching);
			gpsDisabled = false;
		}

		// Define a listener that responds to location updates
		locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				updateLocation(location);
			}

			public void onProviderDisabled(String provider) {
			}

			public void onProviderEnabled(String provider) {
			}

			// unused
			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}
		};

		// Register the listener with the Location Manager to receive location
		// updates
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				10000, 0, locationListener);
		if (!ioioConnected)
			connectTimer.start();
		iconFlashTimer = new IconFlashTimer(500, 500);
		iconFlashTimer.start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_reverse_geocache, menu);
		return true;
	}

	@Override
	public void onDestroy() {
		connectTimer.cancel();
		batteryTimer.cancel();
		iconFlashTimer.cancel();
		try {
			ioioCommands.put(new IOIOCommand(COMMAND_POWER_OFF));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		super.onDestroy();
	}

	@Override
	public void onDialogNegativeClick(DialogFragment dialog) {
	}

	@Override
	public void onDialogPositiveClick(DialogFragment dialog, Bundle b) {

		if (dialog.getTag().equals("passcode")) {
			new Programmer().show(getSupportFragmentManager(), "programmer");
		} else if (dialog.getTag().equals("passcodePrompt")) {
			// find the existing programming menu and set the new passcode
			((Programmer) getSupportFragmentManager().findFragmentByTag(
					"programmer")).setPasscode(b.getInt("code", 0));
		} else if (dialog.getTag().equals("programmer")) {
			boolean save = b.getBoolean("save");
			if (save) {
				// create a new array the size of EEPROM so we can copy it
				// directly
				byte[] eeprom = new byte[HardwareLooper.EEPROM_SIZE];
				Arrays.fill(eeprom, (byte) 0);

				eeprom[HardwareLooper.LOCK_STATE_ADDRESS] = (byte) (b
						.getBoolean("unlocked", false) ? 1 : 0);

				eeprom[HardwareLooper.SOLVE_STATE_ADDRESS] = (byte) (b
						.getBoolean("solved", false) ? 1 : 0);

				int tmp = b.getInt("attempts", -1);
				eeprom[HardwareLooper.ATTEMPTS_ADDRESS] = (byte) ((tmp == -1) ? attempts[0]
						: tmp);

				tmp = b.getInt("maxattempts", -1);
				eeprom[HardwareLooper.MAX_ATTEMPTS_ADDRESS] = (byte) ((tmp == -1) ? attempts[1]
						: tmp);

				tmp = b.getInt("radius", -1);
				byte[] buf = ByteConversion
						.intToByteArray((tmp == -1) ? (int) gpsLocation
								.getAccuracy() : tmp);
				System.arraycopy(buf, 0, eeprom, HardwareLooper.RADIUS_ADDRESS,
						buf.length);

				tmp = b.getInt("resetpin", -1);
				buf = ByteConversion.intToByteArray((tmp == -1) ? resetPin
						: tmp);
				System.arraycopy(buf, 0, eeprom,
						HardwareLooper.RESET_PIN_ADDRESS, buf.length);

				double d = b.getDouble("latitude", Double.NaN);
				buf = ByteConversion
						.doubleToByteArray((d == Double.NaN) ? gpsLocation
								.getLatitude() : d);
				System.arraycopy(buf, 0, eeprom,
						HardwareLooper.LATITUDE_ADDRESS, buf.length);

				d = b.getDouble("longitude", Double.NaN);
				buf = ByteConversion
						.doubleToByteArray((d == Double.NaN) ? gpsLocation
								.getLongitude() : d);
				System.arraycopy(buf, 0, eeprom,
						HardwareLooper.LONGITUDE_ADDRESS, buf.length);

				try {
					tmp = getPackageManager().getPackageInfo(getPackageName(),
							0).versionCode;
					eeprom[HardwareLooper.VERSION_ADDRESS] = (byte) tmp;
				} catch (NameNotFoundException e) {
					e.printStackTrace();
					eeprom[HardwareLooper.VERSION_ADDRESS] = (byte) 0;
				}
				long l = b.getLong("serial", -1);
				buf = ByteConversion.longToByteArray(l);
				System.arraycopy(buf, 0, eeprom, HardwareLooper.SERIAL_ADDRESS,
						buf.length);
				new EncryptStringTask().execute(ByteConversion
						.byteArrayToString(eeprom));
			} else {
				ProgressDialog pd = ProgressDialog.show(this,
						"Writing data to EEPROM", "Please wait...", true);

				ioioCommands.add(new IOIOCommand(COMMAND_WRITE_UNLOCKED, b
						.getBoolean("unlocked", false)));

				ioioCommands.add(new IOIOCommand(COMMAND_WRITE_SOLVED, b
						.getBoolean("solved", false)));

				int tmp = b.getInt("attempts", -1);
				if (tmp != -1)
					ioioCommands.add(new IOIOCommand(COMMAND_WRITE_ATTEMPTS,
							tmp));

				tmp = b.getInt("maxattempts", -1);
				if (tmp != -1)
					ioioCommands.add(new IOIOCommand(
							COMMAND_WRITE_MAX_ATTEMPTS, tmp));

				tmp = b.getInt("radius", -1);
				if (tmp != -1)
					ioioCommands
							.add(new IOIOCommand(COMMAND_WRITE_RADIUS, tmp));

				tmp = b.getInt("resetpin", -1);
				if (tmp != -1)
					ioioCommands.add(new IOIOCommand(COMMAND_WRITE_RESET_PIN,
							tmp));

				double d = b.getDouble("latitude", Double.NaN);
				if (d != Double.NaN)
					ioioCommands
							.add(new IOIOCommand(COMMAND_WRITE_LATITUDE, d));

				d = b.getDouble("longitude", Double.NaN);
				if (d != Double.NaN)
					ioioCommands
							.add(new IOIOCommand(COMMAND_WRITE_LONGITUDE, d));

				try {
					ioioCommands.add(new IOIOCommand(COMMAND_WRITE_VERSION,
							getPackageManager().getPackageInfo(
									getPackageName(), 0).versionCode));
				} catch (NameNotFoundException e) {
					e.printStackTrace();
				}
				ioioCommands
						.add(new IOIOCommand(COMMAND_UI_DISMISS_DIALOG, pd));
			}
			dialog.dismiss();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_help:
			popupHelp();
			return true;
		case R.id.menu_update:
			showUpdateDialog();
			return true;
		case R.id.menu_program:
			showPasscodeDialog();
			return true;
		case R.id.menu_contact:
			sendEmail();
			return true;
		case R.id.menu_exit:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onPause() {
		if (locationManager != null && locationListener != null)
			locationManager.removeUpdates(locationListener);
		super.onPause();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// TODO: disable upon connect
		// menu.findItem(R.id.menu_update).setVisible(ioioConnected);
		// menu.findItem(R.id.menu_program).setVisible(ioioConnected);
		return true;
	}

	@Override
	public void onResume() {
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				10000, 0, locationListener);
		super.onResume();
	}

	/**
	 * Pops up an {@link AlertDialog} with help information loaded from
	 * strings.xml.
	 */

	@SuppressLint("NewApi")
	private void popupHelp() {
		AlertDialog.Builder builder = (android.os.Build.VERSION.SDK_INT < 11) ? new AlertDialog.Builder(
				this) : new AlertDialog.Builder(this,
				AlertDialog.THEME_HOLO_DARK);
		builder.setMessage(R.string.dialog_help)
				.setNeutralButton(R.string.ok, null).setTitle("Help");
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	/**
	 * Pops up an {@link AlertDialog} prompting the user to enable GPS and
	 * exiting if GPS remains disabled.
	 */

	@SuppressLint("NewApi")
	public void promptGpsEnable() {
		AlertDialog.Builder builder = (android.os.Build.VERSION.SDK_INT < 11) ? new AlertDialog.Builder(
				this) : new AlertDialog.Builder(this,
				AlertDialog.THEME_HOLO_DARK);
		builder.setTitle(R.string.gps_disabled)
				.setMessage(R.string.gps_enable_prompt)
				.setPositiveButton(R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								// User clicked OK button, send them to GPS
								// settings to enable
								Intent I = new Intent(
										android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
								startActivityForResult(I, REQUEST_ENABLE_GPS);
							}
						})
				.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								// User cancelled the dialog, exit app
								finish();
							}
						});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	/**
	 * Fires an Intent to send an email to the developer.
	 */

	private void sendEmail() {

		// This is the email intent
		Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);

		// Add the address/subject to the intent
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
				new String[] { "wchill1337@gmail.com" });
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
				"Mystery Box");

		// Specify that we want to send an email
		emailIntent.setType("message/rfc822");

		startActivity(emailIntent);
	}

	/**
	 * Pops up an {@link AlertDialog} notifying the user that an IOIO could not
	 * be found.
	 */

	@SuppressLint("NewApi")
	public void showNotFound() {
		AlertDialog.Builder builder = (android.os.Build.VERSION.SDK_INT < 11) ? new AlertDialog.Builder(
				this) : new AlertDialog.Builder(this,
				AlertDialog.THEME_HOLO_DARK);
		builder.setTitle(R.string.ioio_not_found_title)
				.setMessage(R.string.ioio_not_found)
				.setNeutralButton(R.string.ok, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	/**
	 * Pops up a {@link DialogFragment} prompting the user for a passcode.
	 */

	private void showPasscodeDialog() {
		// DialogFragment.show() will take care of adding the fragment
		// in a transaction. We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag(
				"passcode");
		if (prev != null)
			ft.remove(prev);
		ft.addToBackStack(null);

		// Create and show the dialog.
		DialogFragment newFragment = Passcode.newInstance(resetPin);
		newFragment.show(ft, "passcode");
	}

	/**
	 * Pops up an {@link AlertDialog} prompting the user for update data.
	 */

	private void showUpdateDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = getLayoutInflater();
		final View v = inflater.inflate(R.layout.update_dialog, null);
		final EditText et = (EditText) v.findViewById(R.id.field_update_data);
		builder.setTitle(R.string.menu_update)
				.setView(v)
				.setPositiveButton(R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
								imm.hideSoftInputFromWindow(
										et.getWindowToken(), 0);
								updateData(et.getText().toString());
							}
						}).setNegativeButton(R.string.cancel, null);
		builder.show();
	}

	// TODO: implement updating via string
	private void updateData(String data) {
		try {
			new DecryptStringTask().execute(data);
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "Bad update data, try again",
					Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Called whenever the device's current GPS location has been updated. Marks
	 * GPS as connected, saves new location and enables unlock attempts.
	 * 
	 * @param loc
	 *            the new Location object
	 */

	private void updateLocation(Location loc) {
		gpsConnected = true;
		gpsImage.setImageResource(R.drawable.ic_action_gps_locked);
		gpsStatus.setText(R.string.locked);
		if (loc == null) {
			if (!enableButton) {
				// locationManager.removeUpdates(locationListener);
				enableButton = true;
			} else
				unlockButton.setEnabled(true);
		}
		currentLocation = loc;
	}

	/**
	 * Helper class used to automatically update the remaining capacity of the
	 * battery connected to the IOIO.
	 */

	public class BatteryTimer extends CountDownTimer {

		public BatteryTimer(long startTime, long interval) {
			super(startTime, interval);
		}

		@Override
		public void onFinish() {
			batteryTimer.start(); // restart it
		}

		@Override
		public void onTick(long millisUntilFinished) {
			runOnUiThread(new Runnable() {
				public void run() {
					// TODO: check for accurate values

					batteryBar.setProgress((int) (210 / batteryVoltage));
					batteryPercentage.setText((int) (210 / batteryVoltage)
							+ "%");
				}
			});
		}
	}

	/**
	 * Helper class used to notify the user if an IOIO cannot be found.
	 */

	public class ConnectTimer extends CountDownTimer {

		public ConnectTimer(long startTime, long interval) {
			super(startTime, interval);
		}

		@Override
		public void onFinish() {
			if (!ioioConnected) {
				showNotFound();
			}

		}

		@Override
		public void onTick(long millisUntilFinished) {
			// not used
		}
	}

	/**
	 * Helper class used to flash GPS/Bluetooth icons if not connected.
	 */

	public class IconFlashTimer extends CountDownTimer {

		public IconFlashTimer(long startTime, long interval) {
			super(startTime, interval);
		}

		@Override
		public void onFinish() {
			if (!gpsConnected && !gpsDisabled) {
				gpsImage.setImageResource((gpsIconState) ? R.drawable.ic_action_gps_searching
						: R.drawable.ic_action_gps_locked);
				gpsIconState = !gpsIconState;
			}
			if (!btConnected && !btDisabled) {
				btImage.setImageResource((btIconState) ? R.drawable.ic_action_bluetooth
						: R.drawable.ic_action_bluetooth_searching);
				btIconState = !btIconState;
			}
			if (!gpsConnected || !btConnected)
				this.start();
		}

		@Override
		public void onTick(long millisUntilFinished) {
			// not used
		}
	}

	/**
	 * Helper class to asynchronously decrypt a String.
	 */

	class DecryptStringTask extends AsyncTask<String, Void, String> {
		Dialog d;

		@Override
		protected String doInBackground(String... data) {
			try {
				SecretKey secret = deriveKey(Long.valueOf(ENC_KEY).toString()
						.toCharArray(),
						ByteConversion.longToByteArray(boxSerial));
				String[] enc = data[0].split(":");
				String plaintext = decryptString(secret,
						ByteConversion.hexStringToByteArray(enc[0]),
						ByteConversion.hexStringToByteArray(enc[1]));
				return plaintext;
			} catch (Exception e) {
				e.printStackTrace();
				try {
					SecretKey secret = deriveKey(Long.valueOf(ENC_KEY)
							.toString().toCharArray(),
							ByteConversion.longToByteArray(-1));
					String[] enc = data[0].split(" ");
					String plaintext = decryptString(secret,
							ByteConversion.hexStringToByteArray(enc[0]),
							ByteConversion.hexStringToByteArray(enc[1]));
					return plaintext;
				} catch (Exception e1) {
					e.printStackTrace();
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(String data) {
			d.dismiss();
			if (data != null)
				ioioCommands.add(new IOIOCommand(COMMAND_FLASH_DATA, data));
			else
				Toast.makeText(ReverseGeocache.this, R.string.bad_update_data,
						Toast.LENGTH_SHORT).show();
		}

		@Override
		protected void onPreExecute() {
			AlertDialog.Builder builder = new AlertDialog.Builder(
					ReverseGeocache.this);
			LayoutInflater inflater = ReverseGeocache.this.getLayoutInflater();
			builder.setView(
					inflater.inflate(R.layout.indeterminate_progress_bar, null))
					.setTitle(R.string.converting_data).setCancelable(false);
			d = builder.show();
		}

	}

	/**
	 * Helper class used to asynchronously encrypt a String.
	 */

	class EncryptStringTask extends AsyncTask<String, Void, String> {
		Dialog d;

		@Override
		protected String doInBackground(String... data) {
			try {
				SecretKey secret = deriveKey(Long.valueOf(ENC_KEY).toString()
						.toCharArray(),
						ByteConversion.longToByteArray(boxSerial));
				byte[][] senc = encryptString(secret, data[0]);
				String encrypted = ByteConversion.byteArrayToHexString(senc[0])
						+ ":" + ByteConversion.byteArrayToHexString(senc[1]);
				return encrypted;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(String data) {
			d.dismiss();
			if (data != null) {
				ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				clipboard.setText(data);
				Toast.makeText(ReverseGeocache.this,
						R.string.copied_to_clipboard, Toast.LENGTH_SHORT)
						.show();
			} else {
				Toast.makeText(ReverseGeocache.this, R.string.encrypt_failure,
						Toast.LENGTH_SHORT).show();
			}
		}

		@Override
		protected void onPreExecute() {
			AlertDialog.Builder builder = new AlertDialog.Builder(
					ReverseGeocache.this);
			LayoutInflater inflater = ReverseGeocache.this.getLayoutInflater();
			builder.setView(
					inflater.inflate(R.layout.indeterminate_progress_bar, null))
					.setTitle(R.string.converting_data).setCancelable(false);
			d = builder.show();
		}

	}

	class HardwareLooper extends BaseIOIOLooper {

		// I/O
		private AnalogInput battery;
		private DigitalOutput powerOffOutput;
		private PwmOutput servoPwmOutput;
		private TwiMaster eeprom;

		// pin assignments
		private static final int POWER_OFF_PIN = 4;
		private static final int SERVO_PIN = 3;
		private static final int BATTERY_PIN = 41;

		// variable size constants
		static final int BOOLEAN_SIZE = 1;
		static final int BYTE_SIZE = 1;
		static final int INT_SIZE = 4;
		static final int DOUBLE_SIZE = 8;
		static final int LONG_SIZE = 8;

		// EEPROM constants
		// using a 24LC1025 but 256 put here for backwards compatibility
		static final int EEPROM_SIZE = 256;
		static final int EEPROM_I2C_ADDRESS = 0x50;

		// addresses for data in EEPROM
		static final int START_ADDRESS = 0;
		static final int VERSION_ADDRESS = 0;
		static final int SERIAL_ADDRESS = 1;
		static final int SOLVE_STATE_ADDRESS = 9;
		static final int LOCK_STATE_ADDRESS = 10;
		static final int ATTEMPTS_ADDRESS = 11;
		static final int MAX_ATTEMPTS_ADDRESS = 12;
		static final int LATITUDE_ADDRESS = 13;
		static final int LONGITUDE_ADDRESS = 21;
		static final int RADIUS_ADDRESS = 29;
		static final int RESET_PIN_ADDRESS = 33;

		// Servo/PWM constants
		// TODO: calibrate values
		private static final int SERVO_CLOSED = 1000;
		private static final int SERVO_OPEN = 2000;
		private static final int PWM_FREQ = 100;

		/**
		 * Called once after the HardwareLooper has been created. Sets up all
		 * outputs/inputs, prepares I2C communication with EEPROM and reads data
		 * from EEPROM. UI elements are then enabled and battery state updating
		 * begins.
		 * 
		 * If an exception is encountered, UI elements will be disabled.
		 * 
		 * @throws ConnectionLostException
		 *             if connection with IOIO is lost during setup
		 */

		public void setup() throws ConnectionLostException {
			try {
				servoPwmOutput = ioio_.openPwmOutput(new DigitalOutput.Spec(
						SERVO_PIN, Mode.OPEN_DRAIN), PWM_FREQ);
				battery = ioio_.openAnalogInput(BATTERY_PIN);
				eeprom = ioio_.openTwiMaster(1, TwiMaster.Rate.RATE_100KHz,
						false);
				powerOffOutput = ioio_.openDigitalOutput(POWER_OFF_PIN);

				gpsLocation = readCoords();
				attempts = readAttempts();
				solved = readState();
				resetPin = readResetPin();
				boxSerial = readSerial();
				unlocked = readLock();

				ioioConnected = true;

				connectTimer.cancel();
				enableUi(true);

				batteryTimer.start();

			} catch (ConnectionLostException e) {
				enableUi(false);
				throw e;
			} catch (InterruptedException e) {
				enableUi(false);
				e.printStackTrace();
			}
		}
		
		/**
		 * Automatically called 100 times per second.
		 * 
		 * Processes any queued commands requested by the application and
		 * batches them together for execution.
		 * 
		 * Battery state is updated every run.
		 * 
		 * If the method encounters an {@link InterruptedException}, IOIO will
		 * be disconnected and UI elements will be disabled.
		 * 
		 * If the method encounters an {@link ConnectionLostException}, UI
		 * elements will be disabled.
		 * 
		 * @throws ConnectionLostExceptionif
		 *             connection with IOIO is lost
		 */

		public void loop() throws ConnectionLostException {
			try {
				Thread.sleep(10);

				// if the command queue is not empty, start batching commands
				if (ioioCommands.size() > 0) {
					ioio_.beginBatch();
					try {
						while (ioioCommands.size() > 0) {
							IOIOCommand cmd = ioioCommands.poll();
							int cmdNum = cmd.getCommand();
							switch (cmdNum) {
							case COMMAND_WRITE_SERIAL:
								writeSerial(cmd.getLong());
								break;
							case COMMAND_WRITE_SOLVED:
								writeSolved(cmd.getBoolean());
								break;
							case COMMAND_WRITE_UNLOCKED:
								unlock(cmd.getBoolean());
								break;
							case COMMAND_WRITE_ATTEMPTS:
								writeAttempts(cmd.getInt());
								break;
							case COMMAND_WRITE_MAX_ATTEMPTS:
								writeAttempts(cmd.getInt());
								break;
							case COMMAND_WRITE_LATITUDE:
								writeLatitude(cmd.getDouble());
								break;
							case COMMAND_WRITE_LONGITUDE:
								writeLongitude(cmd.getDouble());
								break;
							case COMMAND_WRITE_RADIUS:
								writeRadius(cmd.getInt());
								break;
							case COMMAND_WRITE_RESET_PIN:
								writeResetPin(cmd.getInt());
								break;
							case COMMAND_WRITE_VERSION:
								writeVersion(cmd.getInt());
							case COMMAND_RESET:
								reset();
								break;
							case COMMAND_POWER_OFF:
								powerOff();
								break;
							case COMMAND_UI_DISMISS_DIALOG:
								Dialog d = (Dialog) cmd.getObject();
								d.dismiss();
								break;
							case COMMAND_FLASH_DATA:
								// Flashes all data in byte array to EEPROM
								byte[] b = ByteConversion
										.hexStringToByteArray(cmd.getString());
								byte[] tmp = new byte[8];
								System.arraycopy(b, SERIAL_ADDRESS, tmp, 0,
										LONG_SIZE);
								if (ByteConversion.byteArrayToLong(tmp) == -1) {
									tmp = ByteConversion
											.longToByteArray(readSerial());
									System.arraycopy(tmp, 0, b, SERIAL_ADDRESS,
											LONG_SIZE);
								}
								writeEEPROMByteArrayDialog(START_ADDRESS, b);
								break;
							}
						}
					} finally {
						ioio_.endBatch();
					}
				}
				try {
					batteryVoltage = battery.getVoltage();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (InterruptedException e) {
				ioio_.disconnect();
				enableUi(false);
				e.printStackTrace();
			} catch (ConnectionLostException e) {
				enableUi(false);
				throw e;
			}
		}

		/**
		 * Powers off the IOIO using the Pololu switch.
		 */

		public void powerOff() {
			try {
				powerOffOutput.write(true);
			} catch (ConnectionLostException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Reads the current number of attempts from EEPROM.
		 * 
		 * @returns an int array of size 2 containing the number of used
		 *          attempts and the number of max attempts
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public int[] readAttempts() throws ConnectionLostException,
				InterruptedException {
			int used = readEEPROM(ATTEMPTS_ADDRESS, BYTE_SIZE)[0];
			int allowed = readEEPROM(MAX_ATTEMPTS_ADDRESS, BYTE_SIZE)[0];

			return new int[] { used, allowed };
		}

		/**
		 * Reads the target GPS coordinates from EEPROM.
		 * 
		 * @returns a Location object representing the target location.
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public Location readCoords() throws ConnectionLostException,
				InterruptedException {
			double lat = 0;
			double lng = 0;
			int rad = 1000; // within rad meters of (lat,lng) box will open,
							// default is 1km

			// float = 4 bytes, double = 8 bytes
			byte[] b = readEEPROM(LATITUDE_ADDRESS, DOUBLE_SIZE);
			lat = ByteConversion.byteArrayToDouble(b);
			b = readEEPROM(LONGITUDE_ADDRESS, DOUBLE_SIZE);
			lng = ByteConversion.byteArrayToDouble(b);
			b = readEEPROM(RADIUS_ADDRESS, INT_SIZE);
			rad = ByteConversion.byteArrayToInt(b);

			Location loc = new Location("EEPROM");
			loc.setAccuracy(rad);
			loc.setLatitude(lat);
			loc.setLongitude(lng);
			return loc;
		}

		/**
		 * Reads EEPROM_SIZE bytes of EEPROM starting from START_ADDRESS
		 * (constants).
		 * 
		 * @returns a byte array containing the complete contents of EEPROM
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public byte[] readData() throws ConnectionLostException,
				InterruptedException {
			return readEEPROM(START_ADDRESS, EEPROM_SIZE);
		}

		/**
		 * Reads bytes from EEPROM starting at a given address.
		 * 
		 * @param address
		 *            the address of EEPROM to start reading at
		 * @param numBytes
		 *            the number of bytes to be read
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public byte[] readEEPROM(int address, int numBytes)
				throws ConnectionLostException, InterruptedException {
			byte[] request = { (byte) (address >> 8), (byte) (address & 0xFF) };
			byte[] response = new byte[numBytes];
			eeprom.writeRead(EEPROM_I2C_ADDRESS, false, request,
					request.length, response, response.length);
			return response;
		}

		/**
		 * Reads the current lock state from EEPROM.
		 * 
		 * @returns true if the box is unlocked, false otherwise
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public boolean readLock() throws ConnectionLostException,
				InterruptedException {
			boolean locked = readEEPROM(LOCK_STATE_ADDRESS, BOOLEAN_SIZE)[0] == 1;
			return locked;
		}

		/**
		 * Reads the current reset passcode from EEPROM.
		 * 
		 * @returns an int containing the box's passcode
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public int readResetPin() throws ConnectionLostException,
				InterruptedException {
			byte[] b = readEEPROM(RESET_PIN_ADDRESS, INT_SIZE);
			int pin = b[0] << 8 + b[1];
			return pin;
		}

		/**
		 * Reads the current serial from EEPROM.
		 * 
		 * @returns a long containing the serial number of the box
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public long readSerial() throws ConnectionLostException,
				InterruptedException {
			long serial = ByteConversion.byteArrayToLong(readEEPROM(
					SERIAL_ADDRESS, LONG_SIZE));
			return serial;
		}

		/**
		 * Reads the current solved state from EEPROM.
		 * 
		 * @returns true if the box is marked solved, false otherwise
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public boolean readState() throws ConnectionLostException,
				InterruptedException {
			boolean solved = readEEPROM(SOLVE_STATE_ADDRESS, BOOLEAN_SIZE)[0] == 1;
			return solved;
		}

		/**
		 * Reads the current version code from EEPROM.
		 * 
		 * @returns a byte containing the application version last used to write
		 *          to the box
		 * @throws ConnectionLostException
		 *             if connection is lost during read
		 * @throws InterruptedException
		 *             if read I/O is interrupted
		 */

		public byte readVersion() throws ConnectionLostException,
				InterruptedException {
			byte ver = readEEPROM(VERSION_ADDRESS, BYTE_SIZE)[0];
			return ver;
		}

		/**
		 * Resets the current puzzle by marking it as unsolved, locking the box,
		 * and changing attempts used to 0.
		 * 
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void reset() throws ConnectionLostException {
			writeSolved(false);
			unlock(false);
			writeAttempts(0);
		}


		/**
		 * Writes a new unlock state to EEPROM and locks/unlocks the box
		 * accordingly.
		 * 
		 * @param unlocked
		 *            the new unlock state
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void unlock(boolean unlocked) throws ConnectionLostException {
			writeEEPROM(LOCK_STATE_ADDRESS, (byte) ((unlocked) ? 1 : 0));
			servoPwmOutput
					.setPulseWidth((unlocked) ? SERVO_OPEN : SERVO_CLOSED);
		}

		/**
		 * Writes a new number of attempts to EEPROM.
		 * 
		 * @param attempts
		 *            the new number of attempts
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeAttempts(int attempts) throws ConnectionLostException {
			writeEEPROM(ATTEMPTS_ADDRESS, (byte) attempts);
		}

		/**
		 * Writes one byte to a supported EEPROM at a given address.
		 * 
		 * @param address
		 *            the address of EEPROM to write at
		 * @param b
		 *            the data to be written
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeEEPROM(int address, byte b)
				throws ConnectionLostException {
			byte[] request = new byte[] { (byte) (address >> 8),
					(byte) (address & 0xFF), b };
			byte[] response = new byte[0];
			eeprom.writeReadAsync(EEPROM_I2C_ADDRESS, false, request,
					request.length, response, 0);
		}

		/**
		 * Writes an array of bytes to EEPROM starting at a given address.
		 * 
		 * @param address
		 *            the address of EEPROM to start writing at
		 * @param b
		 *            the data to be written
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeEEPROMByteArray(int address, byte[] b)
				throws ConnectionLostException {
			try {
				for (int i = 0; i < b.length; i++) {
					writeEEPROM(address + i, b[i]);
					Thread.sleep(5);
				}
			} catch (InterruptedException e) {
				ioio_.disconnect();
			}
		}

		/**
		 * Writes an array of bytes to EEPROM starting at a given address and
		 * displays a dialog notifying the user of write progress.
		 * 
		 * @param address
		 *            the address of EEPROM to start writing at
		 * @param b
		 *            the data to be written
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeEEPROMByteArrayDialog(int address, byte[] b)
				throws ConnectionLostException {
			ProgressDialog pd = ProgressDialog.show(ReverseGeocache.this,
					"Writing data to EEPROM", "Please wait...");
			try {
				pd.setMax(b.length);
				for (int i = 0; i < b.length; i++) {
					writeEEPROM(address + i, b[i]);
					pd.setProgress(i);
					Thread.sleep(5);
				}
				pd.dismiss();
			} catch (InterruptedException e) {
				ioio_.disconnect();
			}
		}

		/**
		 * Writes a new latitude coordinate to EEPROM.
		 * 
		 * @param lat
		 *            the new latitude coordinate
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeLatitude(double lat) throws ConnectionLostException {
			writeEEPROMByteArray(LATITUDE_ADDRESS,
					ByteConversion.doubleToByteArray(lat));
		}

		/**
		 * Writes a new longitude coordinate to EEPROM.
		 * 
		 * @param lng
		 *            the new longitude coordinate
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeLongitude(double lng) throws ConnectionLostException {
			writeEEPROMByteArray(LONGITUDE_ADDRESS,
					ByteConversion.doubleToByteArray(lng));
		}

		/**
		 * Writes a new number of max attempts to EEPROM.
		 * 
		 * @param attempts
		 *            the new number of max attempts
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeMaxAttempts(int attempts)
				throws ConnectionLostException {
			writeEEPROM(MAX_ATTEMPTS_ADDRESS, (byte) attempts);
		}

		/**
		 * Writes a new radius size to EEPROM.
		 * 
		 * @param rad
		 *            the new radius size
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeRadius(int rad) throws ConnectionLostException {
			writeEEPROMByteArray(RADIUS_ADDRESS,
					ByteConversion.intToByteArray(rad));
		}

		/**
		 * Writes a new reset passcode to EEPROM.
		 * 
		 * @param pin
		 *            the new passcode
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeResetPin(int pin) throws ConnectionLostException {
			writeEEPROMByteArray(RESET_PIN_ADDRESS,
					ByteConversion.intToByteArray(pin));
		}

		/**
		 * Writes a new serial to EEPROM.
		 * 
		 * @param serial
		 *            the new serial
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeSerial(long serial) throws ConnectionLostException {
			writeEEPROMByteArray(SERIAL_ADDRESS,
					ByteConversion.longToByteArray(serial));
		}

		/**
		 * Writes a new solved state to EEPROM.
		 * 
		 * @param solved
		 *            the new state
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeSolved(boolean solved) throws ConnectionLostException {
			writeEEPROM(SOLVE_STATE_ADDRESS, (byte) (solved ? 1 : 0));
		}

		/**
		 * Writes a new version code to EEPROM.
		 * 
		 * @param ver
		 *            the new version code
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 */

		public void writeVersion(int ver) throws ConnectionLostException {
			writeEEPROM(VERSION_ADDRESS, (byte) ver);
		}
	}
}
