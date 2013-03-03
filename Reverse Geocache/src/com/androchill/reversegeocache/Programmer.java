package com.androchill.reversegeocache;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class Programmer extends DialogFragment implements OnClickListener {
	
	private CheckBox[] cbs = new CheckBox[2];
	private Button[] btns = new Button[3];
	private EditText[] txts = new EditText[7];
	private BoundsWatcher[] bws = new BoundsWatcher[7];
	private DialogListener mListener;
	private long boxSerial;
	
	public static DialogFragment newInstance(long boxSerial) {
		Programmer f = new Programmer();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putLong("serial", boxSerial);
        f.setArguments(args);

        return f;
	}
	
	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the DialogListener so we can send events to the host
            mListener = (DialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement DialogListener");
        }
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	        Bundle savedInstanceState) {
		boxSerial = getArguments().getLong("serial", Long.MAX_VALUE);
	    // Inflate the layout to use as dialog or embedded fragment
		View v = inflater.inflate(R.layout.program_layout, container, false);
		cbs[0] = (CheckBox) v.findViewById(R.id.checkBox_solved);
		cbs[1] = (CheckBox) v.findViewById(R.id.checkBox_unlocked);
		txts[0] = (EditText) v.findViewById(R.id.field_attempts);
		txts[1] = (EditText) v.findViewById(R.id.field_max_attempts);
		txts[2] = (EditText) v.findViewById(R.id.field_latitude);
		txts[3] = (EditText) v.findViewById(R.id.field_longitude);
		txts[4] = (EditText) v.findViewById(R.id.field_radius);
		txts[5] = (EditText) v.findViewById(R.id.field_reset_code);
		txts[6] = (EditText) v.findViewById(R.id.field_serial);
		btns[0] = (Button) v.findViewById(R.id.button_reset);
		btns[1] = (Button) v.findViewById(R.id.button_flash);
		btns[2] = (Button) v.findViewById(R.id.button_save_data);
		
		String[] m = new String[7];
		m[0] = "Attempts must be between 0 and 250";
		m[1] = "Attempts cannot exceed max attempts";
		m[2] = "Latitude must be between -90 and 90";
		m[3] = "Longitude must be between -180 and 180";
		m[4] = "Radius must be between 10 and 100000";
		m[5] = "Reset code must be between 0000 and 9999";
		m[6] = "Must be a valid 64-bit number";
		
		bws[0] = new BoundsWatcher(0, 250, txts[0], txts[1], true, m[0], m[1]);
		bws[1] = new BoundsWatcher(1, 250, txts[1], txts[0], false, m[0], m[1]);
		bws[2] = new BoundsWatcher(-90.0, 90.0, txts[2], m[2]);
		bws[3] = new BoundsWatcher(-180.0, 180.0, txts[3], m[3]);
		bws[4] = new BoundsWatcher(10, 100000, txts[4], m[4]);
		bws[5] = new BoundsWatcher(0, 9999, txts[5], m[5]);
		bws[6] = new BoundsWatcher(0, Long.MAX_VALUE, txts[6], m[6]);
		
		txts[6].setText(String.valueOf(boxSerial));
		
		txts[5].setOnClickListener(this);
		for(Button b : btns)
			b.setOnClickListener(this);
		return v;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
	    // The only reason you might override this method when using onCreateView() is
	    // to modify any dialog characteristics. For example, the dialog includes a
	    // title by default, but your custom layout might not need it. So here you can
	    // remove the dialog title, but you must call the superclass to get the Dialog.
	    Dialog dialog = super.onCreateDialog(savedInstanceState);
	    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
	    return dialog;
	}

	@Override
	public void onClick(View v) {
		boolean ok = true;
		switch(v.getId()) {
		case R.id.button_reset:
			break;
		case R.id.button_flash:
		case R.id.button_save_data:
			for(BoundsWatcher b : bws) {
				if(!b.check()) {
					ok = false;
					break;
				}
			}
			if(txts[6].length() == 0) {
				ok = false;
				Toast.makeText(getActivity(), "Must provide a serial number", Toast.LENGTH_SHORT).show();
			}
			if(ok) {
				Bundle b = new Bundle();
				b.putBoolean("save", v.getId() == R.id.button_save_data);
				b.putBoolean("solved", cbs[0].isChecked());
				b.putBoolean("unlocked", cbs[1].isChecked());
				if(txts[0].length() > 0)
					b.putInt("attempts", Integer.parseInt(txts[0].getText().toString()));
				if(txts[1].length() > 0)
					b.putInt("maxattempts", Integer.parseInt(txts[1].getText().toString()));
				if(txts[2].length() > 0)
					b.putDouble("latitude", Double.parseDouble(txts[2].getText().toString()));
				if(txts[3].length() > 0)
					b.putDouble("longitude", Double.parseDouble(txts[3].getText().toString()));
				if(txts[4].length() > 0)
					b.putInt("radius", Integer.parseInt(txts[4].getText().toString()));
				if(txts[5].length() > 0)
					b.putInt("resetpin", Integer.parseInt(txts[5].getText().toString()));
				if(txts[6].length() > 0)
					b.putLong("serial", Long.parseLong(txts[6].getText().toString()));
				mListener.onDialogPositiveClick(this, b);
			}
			break;
		case R.id.field_reset_code:
			promptPasscode();
			break;
		}
	}
	
	/**
	 * Called by the activity when a passcode has been set using the passcode dialog.
	 *
	 * @param code the passcode to save
	 */
	
	public void setPasscode(int code) {
		// pad to 4 digits if there are leading zeros
		int length = String.valueOf(code).length();
		txts[5].setText("000".substring(0, 4-length) + code);
	}
	
	/**
	 * Creates a dialog box prompting the user to enter a passcode.
	 */
	
	private void promptPasscode() {
		FragmentManager fm = getActivity().getFragmentManager();
	    FragmentTransaction ft = fm.beginTransaction();
	    Fragment prev = getActivity().getFragmentManager().findFragmentByTag("passcodePrompt");
	    if (prev != null)
	        ft.remove(prev);
	    ft.addToBackStack(null);
	    DialogFragment newFragment = Passcode.newInstance(-1);
	    newFragment.show(ft, "passcodePrompt");
	}
	
	/**
	 * Creates and displays a Toast to alert the user of an error.
	 *
	 * @param s the message to say
	 */
	
	private void sayErrMsg(String s) {
		Toast.makeText(getActivity(), s, Toast.LENGTH_SHORT).show();
	}
	
	/**
	 * Helper class to ensure that {@link EditText} fields are within
	 * desired limits.
	 */
	
	class BoundsWatcher {
		
		int lowerBound;
		int upperBound;
		long lowerBoundLong;
		long upperBoundLong;
		double lowerBoundDouble;
		double upperBoundDouble;
		boolean isDouble = false;
		boolean isLong = false;
		EditText ed = null;
		EditText compare = null;
		boolean higher = false;
		String errMsg = "";
		String compareErrMsg = "";
		
		/**
		 * Constructor to set limits on an {@link EditText} and compare it
		 * to another EditText.
		 *
		 * @param lower 		the lower limit
		 * @param upper 		the upper limit
		 * @param ed    		the EditText to be checked
		 * @param c				the EditText to be compared against
		 * @param h				whether ed must be less than c
		 * @param msg			the message to be displayed if EditText is not within given limits
		 * @param compareMsg	the message to be displayed if the EditText comparison check fails
		 */
		
		public BoundsWatcher(int lower, int upper, EditText ed, EditText c, boolean h, String msg, String compareMsg) {
			this(lower, upper, ed, msg);
			compare = c;
			higher = h;
			compareErrMsg = compareMsg;
		}
		
		/**
		 * Constructor to set limits on an {@link EditText} and compare it
		 * to another EditText.
		 *
		 * @param lower 		the lower limit
		 * @param upper 		the upper limit
		 * @param ed    		the EditText to be checked
		 * @param c				the EditText to be compared against
		 * @param h				whether ed must be less than c
		 * @param msg			the message to be displayed if EditText is not within given limits
		 * @param compareMsg	the message to be displayed if the EditText comparison check fails
		 */
		
		public BoundsWatcher(double lower, double upper, EditText ed, EditText c, boolean h, String msg, String compareMsg) {
			this(lower, upper, ed, msg);
			compare = c;
			higher = h;
			compareErrMsg = compareMsg;
		}
		
		/**
		 * Constructor to set limits on an {@link EditText} and compare it
		 * to another EditText.
		 *
		 * @param lower 		the lower limit
		 * @param upper 		the upper limit
		 * @param ed    		the EditText to be checked
		 * @param c				the EditText to be compared against
		 * @param h				whether ed must be less than c
		 * @param msg			the message to be displayed if EditText is not within given limits
		 * @param compareMsg	the message to be displayed if the EditText comparison check fails
		 */
		
		public BoundsWatcher(long lower, long upper, EditText ed, EditText c, boolean h, String msg, String compareMsg) {
			this(lower, upper, ed, msg);
			compare = c;
			higher = h;
			compareErrMsg = compareMsg;
		}
		
		/**
		 * Constructor to set limits on an {@link EditText}.
		 *
		 * @param lower 		the lower limit
		 * @param upper 		the upper limit
		 * @param ed    		the EditText to be checked
		 * @param msg			the message to be displayed if EditText is not within given limits
		 */
		
		public BoundsWatcher(long lower, long upper, EditText et, String msg) {
			lowerBoundLong = lower;
			upperBoundLong = upper;
			isLong = true;
			errMsg = msg;
			ed = et;
		}
		
		/**
		 * Constructor to set limits on an {@link EditText}.
		 *
		 * @param lower 		the lower limit
		 * @param upper 		the upper limit
		 * @param ed    		the EditText to be checked
		 * @param msg			the message to be displayed if EditText is not within given limits
		 */
		
		public BoundsWatcher(int lower, int upper, EditText et, String msg) {
			lowerBound = lower;
			upperBound = upper;
			errMsg = msg;
			ed = et;
		}
		
		/**
		 * Constructor to set limits on an {@link EditText}.
		 *
		 * @param lower 		the lower limit
		 * @param upper 		the upper limit
		 * @param ed    		the EditText to be checked
		 * @param msg			the message to be displayed if EditText is not within given limits
		 */
		
		public BoundsWatcher(double lower, double upper, EditText et, String msg) {
			lowerBoundDouble = lower;
			upperBoundDouble = upper;
			isDouble = true;
			errMsg = msg;
			ed = et;
		}
		
		/**
		 * Checks the given {@link EditText} to determine whether input is valid.
		 *
		 * @return true if the input is within given limits or blank, false otherwise
		 */
		
		public boolean check() {
			try {
				if(ed.length() == 0) return true;
				if(isDouble) {
					double d = Double.parseDouble(ed.getText().toString());
					if(lowerBoundDouble > d || upperBoundDouble < d) {
						sayErrMsg(errMsg);
						return false;
					}
					if(compare != null && compare.length() > 0) {
						double d1 = Double.parseDouble(compare.getText().toString());
						if(higher && d1 < d || !higher && d1 > d) {
							sayErrMsg(compareErrMsg);
							return false;
						}
					}
				} else if(isLong) {
					long k = Long.parseLong(ed.getText().toString());
					if(lowerBoundLong > k || upperBoundLong < k) {
						sayErrMsg(errMsg);
						return false;
					}
					if(compare != null && compare.length() > 0) {
						long k1 = Long.parseLong(compare.getText().toString());
						if(higher && k1 < k || !higher && k1 > k) {
							sayErrMsg(compareErrMsg);
							return false;
						}
					}
				} else {
					int k = Integer.parseInt(ed.getText().toString());
					if(lowerBound > k || upperBound < k) {
						sayErrMsg(errMsg);
						return false;
					}
					if(compare != null && compare.length() > 0) {
						int k1 = Integer.parseInt(compare.getText().toString());
						if(higher && k1 < k || !higher && k1 > k) {
							sayErrMsg(compareErrMsg);
							return false;
						}
					}
				}
			} catch (NumberFormatException e) {
				sayErrMsg("Invalid number");
				e.printStackTrace();
				return false;
			}
			return true;
		}
	}
}
