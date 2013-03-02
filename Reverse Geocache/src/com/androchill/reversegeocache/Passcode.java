package com.androchill.reversegeocache;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

public class Passcode extends DialogFragment implements OnClickListener {
	
	Button[] btns = new Button[10];
	ImageButton backBtn;
	EditText[] digits = new EditText[4];
	String text = new String("");
	int code;
	
	DialogListener mListener;
	
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
	
	public static Passcode newInstance(int num) {
        Passcode f = new Passcode();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt("code", num);
        f.setArguments(args);

        return f;
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	        Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
	    // Inflate the layout to use as dialog or embedded fragment
		View v = inflater.inflate(R.layout.passcode_dialog, container, false);
		code = getArguments().getInt("code", 0);
		btns[0] = (Button) v.findViewById(R.id.button_pass_zero);
		btns[1] = (Button) v.findViewById(R.id.button_pass_one);
		btns[2] = (Button) v.findViewById(R.id.button_pass_two);
		btns[3] = (Button) v.findViewById(R.id.button_pass_three);
		btns[4] = (Button) v.findViewById(R.id.button_pass_four);
		btns[5] = (Button) v.findViewById(R.id.button_pass_five);
		btns[6] = (Button) v.findViewById(R.id.button_pass_six);
		btns[7] = (Button) v.findViewById(R.id.button_pass_seven);
		btns[8] = (Button) v.findViewById(R.id.button_pass_eight);
		btns[9] = (Button) v.findViewById(R.id.button_pass_nine);
		backBtn = (ImageButton) v.findViewById(R.id.button_pass_back);
		digits[0] = (EditText) v.findViewById(R.id.passcode_digit_box_1);
		digits[1] = (EditText) v.findViewById(R.id.passcode_digit_box_2);
		digits[2] = (EditText) v.findViewById(R.id.passcode_digit_box_3);
		digits[3] = (EditText) v.findViewById(R.id.passcode_digit_box_4);
		for(Button b : btns)
			b.setOnClickListener(this);
		backBtn.setOnClickListener(this);
		return v;
	}
	
	/** The system calls this only when creating the layout in a dialog. */
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
		switch(v.getId()) {
		case R.id.button_pass_zero:
			text = text.concat("0");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_one:
			text = text.concat("1");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_two:
			text = text.concat("2");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_three:
			text = text.concat("3");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_four:
			text = text.concat("4");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_five:
			text = text.concat("5");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_six:
			text = text.concat("6");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_seven:
			text = text.concat("7");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_eight:
			text = text.concat("8");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_nine:
			text = text.concat("9");
			digits[text.length() - 1].setText("*");
			break;
		case R.id.button_pass_back:
			if(text.length() > 0) {
				text = text.substring(0, text.length() - 1);
				digits[text.length()].setText("");
			} else {
				this.getDialog().cancel();
			}
			break;
		}
		if(text.length() == 4) {
			if(Integer.parseInt(text.toString()) != code && code != -1) {
				text = "";
				Vibrator vib = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
				vib.vibrate(250);
				Toast.makeText(getActivity(), R.string.incorrect_passcode, Toast.LENGTH_SHORT).show();
				for(EditText d : digits)
					d.setText("");
			} else {
				Bundle b = new Bundle();
				b.putInt("code", Integer.parseInt(text.toString()));
				mListener.onDialogPositiveClick(this, b);
				this.dismiss();
			}
		}
	}
}
