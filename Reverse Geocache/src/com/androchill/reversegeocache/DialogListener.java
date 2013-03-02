package com.androchill.reversegeocache;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public interface DialogListener {
	public void onDialogPositiveClick(DialogFragment dialog, Bundle bundle);
    public void onDialogNegativeClick(DialogFragment dialog);
}
