package com.androchill.reversegeocache;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.DigitalOutput.Spec.Mode;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.TwiMaster;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.app.DialogFragment;
import android.content.ClipboardManager;
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

public class ReverseGeocache extends IOIOActivity implements
		OnClickListener, DialogListener {

	// instance variables
	private double batteryVoltage;
	private double[] pastBatteryVoltage = new double[10];
	private int numVoltageMeasurements = 0;
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

	// Puzzle variables
	private long boxSerial;
	private int[] attempts = new int[2];
	private boolean solved;
	private boolean unlocked;
	private Location gpsLocation;
	private int resetPin;

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
	static final long UNIVERSAL_UPDATE = Long.MAX_VALUE;

	// Enable request code constants
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_ENABLE_GPS = 2;

	@Override
	protected IOIOLooper createIOIOLooper() {
		return new HardwareLooper();
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
				if (enable) {
					batteryBar.setVisibility(View.VISIBLE);
					btImage.setImageResource(R.drawable.ic_action_bluetooth_connected);
					btConnected = true;
					btStatus.setText(R.string.connected);
					changeConnectionStatus(1);
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
					if (currentLocation != null && ioioConnected)
						unlockButton.setEnabled(true);
				} else {
					batteryPercentage.setText(R.string.unavailable);
					attemptsStatus.setText(R.string.unavailable);
					lockStatus.setText(R.string.unavailable);
					btStatus.setText(R.string.disconnected);
					changeConnectionStatus(0);
					unlockButton.setEnabled(false);
					if(boxSerial > 0) {
						serialText.setText(String.valueOf(boxSerial));
					}
					batteryBar.setVisibility(View.INVISIBLE);
					btImage.setImageResource(R.drawable.ic_action_bluetooth);
					lockImage.setImageResource(R.drawable.ic_action_lock);
				}
			}
		});
	}

	private void changeConnectionStatus(final int state) {
		switch(state) {
		case 0:
			connectionStatus.setText(R.string.unavailable);
			break;
		case 1:
			connectionStatus.setText(R.string.ok);
			break;
		case 2:
			connectionStatus.setText(R.string.busy);
			break;
		}
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

	@Override
	public void onClick(View v) {

		// if puzzle is unsolved and attempts remain
		if (attempts[0] < attempts[1] && !solved) {
			attempts[0]++;
			attemptsStatus.setText((attempts[1] - attempts[0]) + " remaining");
			try {
				ioioCommands.put(new IOIOCommand(IOIOCommand.WRITE_ATTEMPTS, attempts[0]));
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

			// check distance between current and target location
			// if less than desired radius, unlock box and mark solved
			float dist = gpsLocation.distanceTo(currentLocation);
			if (dist <= gpsLocation.getAccuracy()) {
				solved = true;
				try {
					ioioCommands
							.put(new IOIOCommand(IOIOCommand.WRITE_SOLVED, true));
					ioioCommands.put(new IOIOCommand(IOIOCommand.WRITE_ATTEMPTS,
							attempts[0]));
					ioioCommands.put(new IOIOCommand(IOIOCommand.WRITE_UNLOCKED,
							true));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				lockStatus.setText(R.string.unlocked);
			} else {
				if(dist > 10000)
					Toast.makeText(this, "Distance: " + dist/1000 + "km",
						Toast.LENGTH_SHORT).show();
				else
					Toast.makeText(this, "Distance: " + dist + "m",
							Toast.LENGTH_SHORT).show();
			}
		} else if (solved) {
			// somehow the box was locked but the puzzle
			// was already solved, so unlock box again
			try {
				ioioCommands.put(new IOIOCommand(IOIOCommand.WRITE_UNLOCKED, true));
				Toast.makeText(this, "Open!", Toast.LENGTH_SHORT).show();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else if (attempts[0] >= attempts[1]) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this,
					AlertDialog.THEME_HOLO_DARK);
			builder.setTitle(R.string.out_of_attempts)
					.setMessage(R.string.locked_forever)
					.setNeutralButton(R.string.ok, null);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);			       
		
		// Set the layout and theme of activity
		setContentView(R.layout.activity_reverse_geocache);
		setTheme(android.R.style.Theme_Holo);

		// Initialize timers
		batteryTimer = new BatteryTimer(10000, 2500);
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

		if (!Settings.Secure.getString(getContentResolver(),
			       Settings.Secure.ALLOW_MOCK_LOCATION).equals("0")) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this,
					AlertDialog.THEME_HOLO_DARK);
			builder.setTitle(R.string.mock_locations_disallowed)
					.setMessage(R.string.prompt_disable_mock_locations)
					.setPositiveButton(R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									ReverseGeocache.this.finish();
								}
							});
			AlertDialog dialog = builder.create();
			dialog.show();
		}
		
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

			// unused
			public void onProviderDisabled(String provider) {}
			public void onProviderEnabled(String provider) {}
			public void onStatusChanged(String provider, int status,
					Bundle extras) {}
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
		super.onDestroy();
	}

	@Override
	public void onDialogNegativeClick(DialogFragment dialog) {
	}

	@Override
	public void onDialogPositiveClick(DialogFragment dialog, Bundle b) {

		if (dialog.getTag().equals("passcode")) {
			// if we got past the first passcode screen show the programming screen
			showProgrammer();
		} else if (dialog.getTag().equals("passcodePrompt")) {
			// find the existing programming menu and set the new passcode
			((Programmer) getFragmentManager().findFragmentByTag(
					"programmer")).setPasscode(b.getInt("code", 0));
		} else if (dialog.getTag().equals("programmer")) {
			// programming screen has given us data, let's process it
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
				String data = ByteConversion.byteArrayToHexString(eeprom);
				
				// calculate checksum and concatenate
				int checkSum = 0;
				for(byte eepromByte : eeprom)
					checkSum += eepromByte;
				data += ":" + ByteConversion.intToHexString(checkSum);
				
				// copy to clipboard
				ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("update data", "UPDATE" + data);
				clipboard.setPrimaryClip(clip);
				Toast.makeText(ReverseGeocache.this,
						R.string.copied_to_clipboard, Toast.LENGTH_SHORT)
						.show();
			} else {
				// writing data to EEPROM
				ProgressDialog pd = ProgressDialog.show(this,
						"Writing data to EEPROM", "Please wait...", true);

				ioioCommands.add(new IOIOCommand(IOIOCommand.WRITE_UNLOCKED, b
						.getBoolean("unlocked", false)));

				ioioCommands.add(new IOIOCommand(IOIOCommand.WRITE_SOLVED, b
						.getBoolean("solved", false)));

				int tmp = b.getInt("attempts", -1);
				if (tmp != -1)
					ioioCommands.add(new IOIOCommand(IOIOCommand.WRITE_ATTEMPTS,
							tmp));

				tmp = b.getInt("maxattempts", -1);
				if (tmp != -1)
					ioioCommands.add(new IOIOCommand(
							IOIOCommand.WRITE_MAX_ATTEMPTS, tmp));

				tmp = b.getInt("radius", -1);
				if (tmp != -1)
					ioioCommands
							.add(new IOIOCommand(IOIOCommand.WRITE_RADIUS, tmp));

				tmp = b.getInt("resetpin", -1);
				if (tmp != -1)
					ioioCommands.add(new IOIOCommand(IOIOCommand.WRITE_RESET_PIN,
							tmp));

				double d = b.getDouble("latitude", Double.NaN);
				if (d != Double.NaN)
					ioioCommands
							.add(new IOIOCommand(IOIOCommand.WRITE_LATITUDE, d));

				d = b.getDouble("longitude", Double.NaN);
				if (d != Double.NaN)
					ioioCommands
							.add(new IOIOCommand(IOIOCommand.WRITE_LONGITUDE, d));

				try {
					ioioCommands.add(new IOIOCommand(IOIOCommand.WRITE_VERSION,
							getPackageManager().getPackageInfo(
									getPackageName(), 0).versionCode));
				} catch (NameNotFoundException e) {
					e.printStackTrace();
				}
				ioioCommands
						.add(new IOIOCommand(IOIOCommand.UI_DISMISS_DIALOG, pd));
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
		this.finish();
		super.onPause();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.menu_update).setVisible(ioioConnected);
		menu.findItem(R.id.menu_program).setVisible(ioioConnected);
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

	private void popupHelp() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
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

	public void promptGpsEnable() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
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

	public void showNotFound() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
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
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		Fragment prev = getFragmentManager().findFragmentByTag(
				"passcode");
		if (prev != null)
			ft.remove(prev);
		ft.addToBackStack(null);

		// Create and show the dialog.
		DialogFragment newFragment = Passcode.newInstance(resetPin);
		newFragment.show(ft, "passcode");
	}

	/**
	 * Pops up a {@link DialogFragment} to allow the user to reprogram the box.
	 */
	
	private void showProgrammer() {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
	    Fragment prev = getFragmentManager().findFragmentByTag("programmer");
	    if (prev != null)
	        ft.remove(prev);
	    ft.addToBackStack(null);
	    DialogFragment newFragment = Programmer.newInstance(boxSerial);
	    newFragment.show(ft, "programmer");
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
								updateData(et.getText().toString(), true);
							}
						}).setNegativeButton(R.string.cancel, null);
		builder.show();
	}

	private boolean updateData(String data, boolean toast) {
		try {
			data = data.substring(6);
			String[] parts = data.split(":");
			// get checksum from the update data
			int checksum = ByteConversion.hexStringToInt(parts[1]);
			// get eeprom image from data
			byte[] eeprom = ByteConversion.hexStringToByteArray(parts[0]);
			// get target serial from data
			byte[] serialBytes = new byte[HardwareLooper.LONG_SIZE];
			System.arraycopy(eeprom, HardwareLooper.SERIAL_ADDRESS, serialBytes, 0, HardwareLooper.LONG_SIZE);
			long targetserial = ByteConversion.byteArrayToLong(serialBytes);
			// verify that eeprom image is good - exception thrown on failure
			int check = 0;
			for(byte eepromByte : eeprom)
				check += eepromByte;
			if(checksum != check) throw new Exception("Checksum failed: " + checksum + " does not match "+ check);
			// verify that this update data is for the right box - exception thrown on failure
			if(targetserial != UNIVERSAL_UPDATE && targetserial != boxSerial && boxSerial != -1) throw new Exception("Target serial " + targetserial + " does not match serial "+ boxSerial);
			// all checks passed, 
			ioioCommands.add(new IOIOCommand(IOIOCommand.FLASH_DATA, eeprom));
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			if(toast)
			Toast.makeText(this, "Bad update data, try again",
					Toast.LENGTH_SHORT).show();
		}
		return false;
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
		if (loc != null && ioioConnected)
			unlockButton.setEnabled(true);
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
			// Average the last several battery measurements to smooth out battery readings due to fluctuations in voltage
			if(numVoltageMeasurements < pastBatteryVoltage.length) {
				pastBatteryVoltage[numVoltageMeasurements] = batteryVoltage;
				numVoltageMeasurements++;
			} else {
				for(int i = 1; i < pastBatteryVoltage.length; i++)
					pastBatteryVoltage[i-1] = pastBatteryVoltage[i];
				pastBatteryVoltage[pastBatteryVoltage.length-1] = batteryVoltage;
			}
			double tmp = 0;
			for(int i = 0; i < pastBatteryVoltage.length; i++)
				tmp += pastBatteryVoltage[i];
			final double total = tmp;
			runOnUiThread(new Runnable() {
				public void run() {
					batteryBar.setProgress(Math.max(0, Math.min((int) (167 * ((total/numVoltageMeasurements)-1.5)), 100)));
					batteryPercentage.setText(Math.max(0, Math.min((int) (167 * ((total/numVoltageMeasurements)-1.5)), 100))
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

	class HardwareLooper extends BaseIOIOLooper {

		// I/O
		private AnalogInput battery;
		private PwmOutput servoPwmOutput;
		private TwiMaster eeprom;

		// pin assignments
		private static final int SERVO_PIN = 6;
		private static final int BATTERY_PIN = 41;

		// variable size constants
		static final int BOOLEAN_SIZE = 1;
		static final int BYTE_SIZE = 1;
		static final int INT_SIZE = 4;
		static final int DOUBLE_SIZE = 8;
		static final int LONG_SIZE = 8;

		// EEPROM constants
		// using a 24LC1025 but 64 put here for backwards compatibility
		// we're using less than 64 bytes of EEPROM anyway
		static final int EEPROM_SIZE = 64;
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
		static final int PHONE_NUMBER_ADDRESS = 35;

		// Servo/PWM constants
		private static final int SERVO_CLOSED = 1400;
		private static final int SERVO_OPEN = 2500;
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
				eeprom = ioio_.openTwiMaster(0, TwiMaster.Rate.RATE_100KHz,
						false);
				connectTimer.cancel();
				ioioConnected = true;
				gpsLocation = readCoords();
				System.out.println(gpsLocation.getLatitude());
				attempts = readAttempts();
				solved = readState();
				resetPin = readResetPin();
				boxSerial = readSerial();
				unlocked = readLock();
				batteryVoltage = battery.getVoltage();
				batteryTimer.start();
				enableUi(true);
			} catch (ConnectionLostException e) {
				enableUi(false);
				e.printStackTrace();
				throw e;
			}  catch (InterruptedException e) {
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
					
					while (ioioCommands.size() > 0) {
						
						IOIOCommand cmd = ioioCommands.poll();
						int cmdNum = cmd.getCommand();
						switch (cmdNum) {
						case IOIOCommand.WRITE_SERIAL:
							writeSerial(cmd.getLong());
							break;
						case IOIOCommand.WRITE_SOLVED:
							writeSolved(cmd.getBoolean());
							break;
						case IOIOCommand.WRITE_UNLOCKED:
							unlock(cmd.getBoolean());
							break;
						case IOIOCommand.WRITE_ATTEMPTS:
							writeAttempts(cmd.getInt());
							break;
						case IOIOCommand.WRITE_MAX_ATTEMPTS:
							writeMaxAttempts(cmd.getInt());
							break;
						case IOIOCommand.WRITE_LATITUDE:
							writeLatitude(cmd.getDouble());
							break;
						case IOIOCommand.WRITE_LONGITUDE:
							writeLongitude(cmd.getDouble());
							break;
						case IOIOCommand.WRITE_RADIUS:
							writeRadius(cmd.getInt());
							break;
						case IOIOCommand.WRITE_RESET_PIN:
							writeResetPin(cmd.getInt());
							break;
						case IOIOCommand.WRITE_VERSION:
							writeVersion(cmd.getInt());
							break;
						case IOIOCommand.RESET:
							reset();
							break;
						case IOIOCommand.UI_DISMISS_DIALOG:
							Dialog d = (Dialog) cmd.getObject();
							d.dismiss();
							break;
						case IOIOCommand.FLASH_DATA:
							// Flashes all data in byte array to EEPROM
							byte[] b = (byte[])cmd.getObject();
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
			byte[] response = new byte[numBytes];
			
			// Note that IOIO only supports up to 127 byte reads in one I2C transaction, so we need to break up any reads longer than that
			for(int i = 0; i <= numBytes/127; i++) {
				int currAddr = address + i * 127;
				byte[] request = { (byte) (currAddr >> 8), (byte) (currAddr & 0xFF) };
				// If we're on the final read, then read numBytes mod 127 else read 127 bytes
				byte[] respBuf = new byte[(i == numBytes/127)?numBytes%127:127];
				eeprom.writeRead(EEPROM_I2C_ADDRESS, false, request,
						request.length, respBuf, respBuf.length);
				System.arraycopy(respBuf, 0, response, i*127, respBuf.length);
			}
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
			return ByteConversion.byteArrayToInt(b);
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
		 * @throws InterruptedException 
		 */

		public void reset() throws ConnectionLostException, InterruptedException {
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
		 * @throws InterruptedException 
		 */

		public void unlock(boolean unlocked) throws ConnectionLostException, InterruptedException {
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
		 * @throws InterruptedException 
		 */

		public void writeAttempts(int attempts) throws ConnectionLostException, InterruptedException {
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
		 * @throws InterruptedException 
		 */

		public void writeEEPROM(int address, byte b)
				throws ConnectionLostException, InterruptedException {
			byte[] request = new byte[] { (byte) (address >> 8),
					(byte) (address & 0xFF), b };
			eeprom.writeRead(EEPROM_I2C_ADDRESS, false, request,
					request.length, null, 0);
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
				byte[] request = new byte[2 + b.length];
				request[0] = (byte) (address >> 8);
				request[1] = (byte) (address & 0xFF);
				System.arraycopy(b, 0, request, 2, b.length);
				eeprom.writeRead(EEPROM_I2C_ADDRESS, false, request, request.length, null, 0);
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

		public void writeEEPROMByteArrayDialog(int address, final byte[] b)
				throws ConnectionLostException {
			writeEEPROMByteArray(address, b);
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(ReverseGeocache.this, "Update successful!", Toast.LENGTH_SHORT).show();
				}
			});
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
		 * @throws InterruptedException 
		 */

		public void writeMaxAttempts(int attempts)
				throws ConnectionLostException, InterruptedException {
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
		 * @throws InterruptedException 
		 */

		public void writeSolved(boolean solved) throws ConnectionLostException, InterruptedException {
			writeEEPROM(SOLVE_STATE_ADDRESS, (byte) (solved ? 1 : 0));
		}

		/**
		 * Writes a new version code to EEPROM.
		 * 
		 * @param ver
		 *            the new version code
		 * @throws ConnectionLostException
		 *             if connection is lost during write
		 * @throws InterruptedException 
		 */

		public void writeVersion(int ver) throws ConnectionLostException, InterruptedException {
			writeEEPROM(VERSION_ADDRESS, (byte) ver);
		}
	}
}
