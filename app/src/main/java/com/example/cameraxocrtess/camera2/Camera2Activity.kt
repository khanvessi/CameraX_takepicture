package com.example.cameraxocrtess.camera2

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.cameraxocrtess.R

class Camera2Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera2)

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_id, PreviewFragment())
        transaction.commit()
    }
}