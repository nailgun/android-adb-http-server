package name.nailgun.adbhttpserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewModel: ServerViewModel
    private lateinit var portEditText: EditText
    private lateinit var startStopButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var logTextView: TextView
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "SERVER_STATUS_CHANGED" -> {
                    val status = intent.getStringExtra("status")
                    val port = intent.getIntExtra("port", 0)
                    when (status) {
                        "RUNNING" -> {
                            viewModel.updateStatus(ServerStatus.RUNNING)
                            viewModel.setCurrentPort(port)
                        }
                        "STOPPED" -> {
                            viewModel.updateStatus(ServerStatus.STOPPED)
                        }
                    }
                }
                "SERVER_LOG_MESSAGE" -> {
                    val message = intent.getStringExtra("message")
                    message?.let { viewModel.addLogMessage(it) }
                }
                "SERVER_ERROR" -> {
                    val error = intent.getStringExtra("error")
                    error?.let { 
                        viewModel.addLogMessage("ERROR: $it")
                        viewModel.updateStatus(ServerStatus.ERROR)
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        viewModel = ViewModelProvider(this)[ServerViewModel::class.java]
        
        initViews()
        setupObservers()
        setupClickListeners()
        registerBroadcastReceiver()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }
    
    private fun initViews() {
        portEditText = findViewById(R.id.portEditText)
        startStopButton = findViewById(R.id.startStopButton)
        statusTextView = findViewById(R.id.statusTextView)
        logTextView = findViewById(R.id.logTextView)
        
        // Set default port
        portEditText.setText("8080")
    }
    
    private fun setupObservers() {
        viewModel.serverStatus.observe(this) { status ->
            updateStatus(status)
        }
        
        viewModel.logMessages.observe(this) { messages ->
            logTextView.text = messages.joinToString("\n")
        }
    }
    
    private fun setupClickListeners() {
        startStopButton.setOnClickListener {
            val portText = portEditText.text.toString()
            if (portText.isEmpty()) {
                Toast.makeText(this, "Please enter a port number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val port = portText.toIntOrNull()
            if (port == null || port < 1024 || port > 65535) {
                Toast.makeText(this, "Please enter a valid port number (1024-65535)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (viewModel.isServerRunning()) {
                stopServer()
            } else {
                startServer(port)
            }
        }
    }
    
    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction("SERVER_STATUS_CHANGED")
            addAction("SERVER_LOG_MESSAGE")
            addAction("SERVER_ERROR")
        }
        registerReceiver(broadcastReceiver, filter)
    }
    
    private fun startServer(port: Int) {
        viewModel.updateStatus(ServerStatus.STARTING)
        val intent = Intent(this, HttpServerService::class.java).apply {
            putExtra("port", port)
            action = "START_SERVER"
        }
        startForegroundService(intent)
    }
    
    private fun stopServer() {
        val intent = Intent(this, HttpServerService::class.java).apply {
            action = "STOP_SERVER"
        }
        startService(intent)
    }
    
    private fun updateStatus(status: ServerStatus) {
        when (status) {
            ServerStatus.STOPPED -> {
                statusTextView.text = "Server Status: Stopped"
                statusTextView.setTextColor(getColor(android.R.color.holo_red_dark))
                startStopButton.text = "Start Server"
                startStopButton.isEnabled = true
            }
            ServerStatus.STARTING -> {
                statusTextView.text = "Server Status: Starting..."
                statusTextView.setTextColor(getColor(android.R.color.holo_orange_dark))
                startStopButton.text = "Starting..."
                startStopButton.isEnabled = false
            }
            ServerStatus.RUNNING -> {
                statusTextView.text = "Server Status: Running on port ${viewModel.getCurrentPort()}"
                statusTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                startStopButton.text = "Stop Server"
                startStopButton.isEnabled = true
            }
            ServerStatus.ERROR -> {
                statusTextView.text = "Server Status: Error"
                statusTextView.setTextColor(getColor(android.R.color.holo_red_dark))
                startStopButton.text = "Start Server"
                startStopButton.isEnabled = true
            }
        }
    }
}
