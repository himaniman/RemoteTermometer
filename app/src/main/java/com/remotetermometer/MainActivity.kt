package com.remotetermometer

import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.*
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

var devices = ArrayList<BluetoothDevice>()
var devicesMap = HashMap<String, BluetoothDevice>()
var mArrayAdapter: ArrayAdapter<String>? = null
val uuid: UUID = UUID.fromString("8989063a-c9af-463a-b3f1-f21d9b2b827b")
var message = ""

class MainActivity : AppCompatActivity() {
    lateinit var bAdapter:BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mArrayAdapter = ArrayAdapter(this, R.layout.dialog_select_device)
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(mReceiver, filter) // Don't forget to unregister during onDestroy
//        val textv: TextView = findViewById(R.id.textView)
        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {view ->
            if (BluetoothAdapter.getDefaultAdapter() == null) {
                Snackbar.make(view, "Bluetooth is disabled", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()

            } else {
                devicesMap = HashMap()
                devices = ArrayList()
                mArrayAdapter!!.clear()

//                val editText = findViewById<EditText>(R.id.editText)
                message = "test text" //editText.text.toString()
//                editText.text.clear()
                for (device in BluetoothAdapter.getDefaultAdapter().bondedDevices) {
                    devicesMap.put(device.address, device)
                    devices.add(device)
                    // Add the name and address to an array adapter to show in a ListView
                    if (device.name == "HC-05") mArrayAdapter!!.add((if (device.name != null) device.name else "Unknown") + "\n" + device.address + "\nPared")
                }

                // Start discovery process
                if (BluetoothAdapter.getDefaultAdapter().startDiscovery()) {
                    val dialog = SelectDeviceDialog()
                    dialog.show(supportFragmentManager, "select_device")
                }
            }
        }

        BluetoothServerController(this).start()
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND == action) {
                // Get the BluetoothDevice object from the Intent
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val pairedDevice = devicesMap[device!!.address]
                if (pairedDevice == null) {
                    var index = -1
                    for (i in devices.indices) {
                        val tmp = devices[i]
                        if (tmp.address == device.address) {
                            index = i
                            break
                        }
                    }

                    if (index > -1) {
                        if (device!!.name != null) {
                            mArrayAdapter?.insert(
                                (if (device.name != null) device.name else "Unknown") + "\n" + device.address,
                                index
                            )
                        }
                    } else {
                        devices.add(device)
                        // 	Add the name and address to an array adapter to show in a ListView
                        mArrayAdapter?.add((if (device.name != null) device.name else "Unknown") + "\n" + device.address)
                    }
                }
            }
        }
    }

    fun onClick(v: View?) {
//        Toast.makeText(this, "Button2 Clicked!", Toast.LENGTH_SHORT).show()
        val tv1: TextView = findViewById(R.id.textView)
        tv1.text = "Hello"

        bAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish(); //automatic close app if Bluetooth service is not available!
        }

        if (!bAdapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, 1)
        }

        if (bAdapter.isDiscovering) {
            bAdapter.cancelDiscovery()
        }
        bAdapter.startDiscovery()

//        // Register for broadcasts when a device is discovered
//        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//        registerReceiver(discoveryFinishReceiver, filter)
//
//        // Register for broadcasts when discovery has finished
//        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
//        registerReceiver(discoveryFinishReceiver, filter)

        bAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = bAdapter.bondedDevices
    }
}

class BluetoothServerController(activity: MainActivity) : Thread() {
    private var cancelled: Boolean
    private val serverSocket: BluetoothServerSocket?
    private val activity = activity

    init {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter != null) {
            this.serverSocket = btAdapter.listenUsingRfcommWithServiceRecord("test", uuid)
            this.cancelled = false
        } else {
            this.serverSocket = null
            this.cancelled = true
        }

    }

    override fun run() {
        var socket: BluetoothSocket

        while(true) {
            if (this.cancelled) {
                break
            }

            try {
                socket = serverSocket!!.accept()
            } catch(e: IOException) {
                break
            }

            if (!this.cancelled && socket != null) {
                Log.wtf("server", "Connecting")
                BluetoothServer(this.activity, socket).start()
            }
        }
    }

    fun cancel() {
        this.cancelled = true
        this.serverSocket!!.close()
    }
}

class SelectDeviceDialog: DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(this.activity)
        builder.setTitle("Send message to")
        builder.setAdapter(mArrayAdapter) { _, which: Int ->
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
            BluetoothClient(devices[which]).start()
        }

        return builder.create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
    }
}

class BluetoothServer(private val activity: MainActivity, private val socket: BluetoothSocket): Thread() {
    private val inputStream = this.socket.inputStream
    private val outputStream = this.socket.outputStream

    override fun run() {
        try {
            val available = inputStream.available()
            val bytes = ByteArray(available)
            Log.i("server", "Reading")
            inputStream.read(bytes, 0, available)
            val text = String(bytes)
            Log.i("server", "Message received")
            Log.i("server", text)
//            activity.appendText(text)
        } catch (e: Exception) {
            Log.e("client", "Cannot read data", e)
        } finally {
            inputStream.close()
            outputStream.close()
            socket.close()
        }
    }
}

class BluetoothClient(device: BluetoothDevice): Thread() {
    private val socket = device.createRfcommSocketToServiceRecord(uuid)

    override fun run() {
        Log.i("client", "Connecting")
        try {
            this.socket.connect()
        } catch (e: java.lang.Exception)  {
            e.printStackTrace()
        }

        Log.i("client", "Sending")
        val outputStream = this.socket.outputStream
        val inputStream = this.socket.inputStream
        try {
            outputStream.write(message.toByteArray())
            outputStream.flush()
            Log.i("client", "Sent")
        } catch (e: Exception) {
            Log.e("client", "Cannot send", e)
        } finally {
            outputStream.close()
            inputStream.close()
            this.socket.close()
        }
    }
}