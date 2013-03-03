package com.androchill.reversegeocache;

import android.os.Bundle;
import android.app.DialogFragment;

public interface DialogListener {
	
	/**
	 * Called when a positive result occurs (not necessarily a click)
	 *
	 * @param dialog the {@link DialogFragment} that is reporting the result
	 * @param bundle a {@link Bundle} containing arguments for the DialogListener
	 */
	
	public void onDialogPositiveClick(DialogFragment dialog, Bundle bundle);
	
	/**
	 * Called when a negative result occurs (not necessarily a click)
	 *
	 * @param dialog the {@link DialogFragment} that is reporting the result
	 */
	
    public void onDialogNegativeClick(DialogFragment dialog);
}
