package com.example.camera2_demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.camera2_demo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var layoutBinding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        layoutBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(layoutBinding.root)
        loadFragment()
    }

    fun loadFragment(){
        supportFragmentManager.beginTransaction().replace(layoutBinding.container.id, CameraFragment()).commit()
    }
}