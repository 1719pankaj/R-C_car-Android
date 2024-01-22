package com.example.analogplayground

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.analogplayground.databinding.ActivityMainBinding
import java.net.InetSocketAddress
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var socket: Socket
    private val SERVER_PORT = 12345 // Set your ESP32 server port
    private var SERVER_IP = "flag" // Replace with your ESP32 IP address

    var accelStrength: Int = 0
    var steerStrength: Int = 0

    private val PREF_NAME = "YourAppPreferences"
    private val KEY_LAST_KNOWN_IP = "lastKnownIP"
    private lateinit var sharedPreferences: SharedPreferences

    private val timeout = 500

    val defaultMargin = 85
    var currentMargin = defaultMargin


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater) // This is the line that inflates the layout
        val view = binding.root
        setContentView(view)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        SERVER_IP = getLastKnownIP()
        binding.ipTV.setText("IP: $SERVER_IP")

        binding.suuuBT.setOnClickListener {
            if (binding.UACET.text.toString().isBlank()) {
                Toast.makeText(this, "Enter IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SERVER_IP = binding.UACET.text.toString()
            setLastKnownIP(SERVER_IP)
            binding.ipTV.setText("IP: $SERVER_IP")
        }

        binding.ipdResetcardView.setOnClickListener {
            currentMargin = defaultMargin
            setMargins(binding.leftStick, defaultMargin, 0, 0, defaultMargin)
            setMargins(binding.rightStick, 0, 0, defaultMargin, defaultMargin)
        }

        binding.ipdMinuscardView.setOnClickListener {
            currentMargin += 15
            setMargins(binding.leftStick, currentMargin, 0, 0, defaultMargin)
            setMargins(binding.rightStick, 0, 0, currentMargin, defaultMargin)
        }

        binding.ipdPluscardView.setOnClickListener {
            currentMargin -= 15
            setMargins(binding.leftStick, currentMargin, 0, 0, defaultMargin)
            setMargins(binding.rightStick, 0, 0, currentMargin, defaultMargin)
        }

        binding.leftStick.setOnMoveListener { angle, strength ->
            val stren = if(strength < 20) 0 else strength
            accelStrength = if (angle in 0..179) {
                stren
            } else if (angle in 180..359) {
                stren * -1
            } else {
                0
            }
            pushUp(accelStrength + 200)
            binding.leftAngle.setText("Angle: $angle°")
            binding.leftStrength.setText("Strength: $accelStrength%")
        }

        binding.rightStick.setOnMoveListener { angle, strength ->
            val stren = if(strength < 20) 0 else strength
            steerStrength = if (angle in 90..270)
                stren
            else
                stren * -1
            pushUp(steerStrength + 500)
            binding.rightAngle.setText("Angle: $angle°")
            binding.rightStrength.setText("Strength: $steerStrength%")
        }

    }

    private fun setLastKnownIP(serverIp: String) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_LAST_KNOWN_IP, serverIp)
        editor.apply()
    }

    private fun getLastKnownIP(): String {
        return sharedPreferences.getString(KEY_LAST_KNOWN_IP, "") ?: ""
    }


    private fun gasPass(stren: Int) {
        try {
            val outputStream = socket.getOutputStream()
            outputStream.write(ByteBuffer.allocate(4).putInt(stren).array())
            outputStream.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun pushUp(stren: Int) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val future: Future<*> = executor.submit {
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(SERVER_IP, SERVER_PORT), timeout)
                gasPass(stren)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    socket.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        try {
            // Wait for the task to complete or timeout
            future.get(timeout.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            // Task exceeded timeout, cancel it
            future.cancel(true)
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } finally {
            // Shutdown the executor
            executor.shutdownNow()
        }
    }

    private fun setMargins(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        if (view.layoutParams is MarginLayoutParams) {
            val p = view.layoutParams as MarginLayoutParams
            p.setMargins(left, top, right, bottom)
            view.requestLayout()
        }
    }


}


