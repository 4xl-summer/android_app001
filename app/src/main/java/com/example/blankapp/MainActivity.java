package com.example.blankapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View blank = new View(this);
        blank.setBackgroundColor(Color.WHITE);
        setContentView(blank);
    }
}
