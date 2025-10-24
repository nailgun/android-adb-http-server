package name.nailgun.adbhttpserver

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ServerViewModel : ViewModel() {
    
    private val _serverStatus = MutableLiveData<ServerStatus>(ServerStatus.STOPPED)
    val serverStatus: LiveData<ServerStatus> = _serverStatus
    
    private val _logMessages = MutableLiveData<MutableList<String>>(mutableListOf())
    val logMessages: LiveData<MutableList<String>> = _logMessages
    
    private var currentPort: Int = 0
    private var isRunning: Boolean = false
    
    fun updateStatus(status: ServerStatus) {
        _serverStatus.value = status
        isRunning = status == ServerStatus.RUNNING
    }
    
    fun addLogMessage(message: String) {
        val currentLogs = _logMessages.value ?: mutableListOf()
        currentLogs.add("${System.currentTimeMillis()}: $message")
        
        // Keep only last 100 log messages
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }
        
        _logMessages.value = currentLogs
    }
    
    fun setCurrentPort(port: Int) {
        currentPort = port
    }
    
    fun getCurrentPort(): Int = currentPort
    
    fun isServerRunning(): Boolean = isRunning
}

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}
