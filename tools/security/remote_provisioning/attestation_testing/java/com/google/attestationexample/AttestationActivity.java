package com.google.attestationexample;

import android.app.Activity;
import android.os.Bundle;

public class AttestationActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runTests();
    }

    private void runTests() {
        try {
            new AttestationTest().execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
