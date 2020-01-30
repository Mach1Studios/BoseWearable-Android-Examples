package com.mach1.ar.heading_example

//
//  MainActivity.kt
//  BoseWearable
//
//  Created by Tambet Ingo on 02/19/2019.
//  Copyright © 2019 Bose Corporation. All rights reserved.
//

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bose.ar.heading_example.R
import com.bose.wearable.BoseWearable

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check that the minimum required API level is available
        if (Build.VERSION.SDK_INT < BoseWearable.MINIMUM_SUPPORTED_OS_VERSION) {
            Toast.makeText(this, getString(R.string.insufficient_api_level, BoseWearable.MINIMUM_SUPPORTED_OS_VERSION),
                    Toast.LENGTH_LONG)
                    .show()
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.content, HomeFragment())
                    .commit()
        }
    }
}