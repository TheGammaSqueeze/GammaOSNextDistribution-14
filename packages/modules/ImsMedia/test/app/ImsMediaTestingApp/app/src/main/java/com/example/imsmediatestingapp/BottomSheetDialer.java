package com.example.imsmediatestingapp;

import android.content.Context;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.EventListener;

/**
 * The BottomSheerDialer class extends the BottomSheetDialog class. It is used when there is an
 * active call session to send DTMF input.
 */
public class BottomSheetDialer extends BottomSheetDialog implements EventListener {
    private boolean isOpen = false;
    private TextView dtmfInput;

    public BottomSheetDialer(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        super.onStart();
        isOpen = true;
        dtmfInput = findViewById(R.id.dtmfInputTextView);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        isOpen = false;
    }

    /**
     * @return boolean value if the dialer is open
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * @return Textview containing the imputed dialer values.
     */
    public TextView getDtmfInput() {
        return dtmfInput;
    }
}
