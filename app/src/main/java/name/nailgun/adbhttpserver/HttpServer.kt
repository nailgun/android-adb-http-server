package name.nailgun.adbhttpserver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.concurrent.Executors

class HttpServer(private val port: Int, private val context: Context, private val onLogMessage: (String) -> Unit) {
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val executor = Executors.newFixedThreadPool(10)
    
    fun start() {
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            onLogMessage("HTTP Server started on port $port")
            
            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept()
                    if (clientSocket != null) {
                        executor.submit {
                            handleClient(clientSocket)
                        }
                    }
                } catch (e: SocketException) {
                    if (isRunning) {
                        onLogMessage("Socket error: ${e.message}")
                    }
                }
            }
        } catch (e: IOException) {
            onLogMessage("Failed to start server: ${e.message}")
        }
    }
    
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            onLogMessage("HTTP Server stopped")
        } catch (e: IOException) {
            onLogMessage("Error stopping server: ${e.message}")
        }
    }
    
    private fun handleClient(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)
            
            val requestLine = reader.readLine()
            if (requestLine == null) {
                clientSocket.close()
                return
            }
            
            val parts = requestLine.split(" ")
            if (parts.size < 3) {
                sendErrorResponse(writer, "Bad Request")
                clientSocket.close()
                return
            }
            
            val method = parts[0]
            val path = parts[1]
            
            // Read headers to get content length
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null && line != "") {
                if (line?.startsWith("Content-Length:", ignoreCase = true) == true) {
                    contentLength = line?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                }
            }
            
            when {
                method == "POST" && path == "/exec" -> {
                    handleRunCommand(reader, writer, contentLength)
                }
                method == "POST" && path == "/start" -> {
                    handleStartActivity(reader, writer, contentLength)
                }
                method == "GET" && path == "/status" -> {
                    handleStatusRequest(writer)
                }
                else -> {
                    sendErrorResponse(writer, "Not Found")
                }
            }
            
        } catch (e: Exception) {
            onLogMessage("Error handling client: ${e.message}")
        } finally {
            try {
                clientSocket.close()
            } catch (e: IOException) {
                onLogMessage("Error closing client socket: ${e.message}")
            }
        }
    }
    
    private fun handleRunCommand(reader: BufferedReader, writer: PrintWriter, contentLength: Int) {
        try {
            val body = CharArray(contentLength)
            reader.read(body, 0, contentLength)
            val requestBody = String(body)
            
            // Parse command from request body
            val command = parseCommandFromBody(requestBody)
            if (command.isEmpty()) {
                sendErrorResponse(writer, "Missing command parameter")
                return
            }
            
            onLogMessage("Executing command: $command")
            
            // Execute ADB command
            val result = executeAdbCommand(command)
            
            // Send response
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: application/json")
            writer.println("Content-Length: ${result.length}")
            writer.println("Connection: close")
            writer.println()
            writer.println(result)
            
        } catch (e: Exception) {
            onLogMessage("Error handling run command: ${e.message}")
            sendErrorResponse(writer, "Internal Server Error")
        }
    }
    
    private fun handleStartActivity(reader: BufferedReader, writer: PrintWriter, contentLength: Int) {
        try {
            val body = CharArray(contentLength)
            reader.read(body, 0, contentLength)
            val requestBody = String(body)
            
            // Parse start parameters from request body
            val startParams = parseStartParamsFromBody(requestBody)
            if (startParams.isEmpty()) {
                sendErrorResponse(writer, "Missing start parameters")
                return
            }
            
            onLogMessage("Starting activity with params: $startParams")
            
            // Check if we should force foreground execution
            val forceForeground = startParams["force_foreground"]?.toBoolean() ?: false
            
            // Start activity using Android API
            val result = startActivityWithParams(startParams, forceForeground)
            
            // Send response
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: application/json")
            writer.println("Content-Length: ${result.length}")
            writer.println("Connection: close")
            writer.println()
            writer.println(result)
            
        } catch (e: Exception) {
            onLogMessage("Error handling start activity: ${e.message}")
            sendErrorResponse(writer, "Internal Server Error")
        }
    }
    
    private fun handleStatusRequest(writer: PrintWriter) {
        val status = """{"status": "running", "port": $port}"""
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: application/json")
        writer.println("Content-Length: ${status.length}")
        writer.println("Connection: close")
        writer.println()
        writer.println(status)
    }
    
    private fun parseCommandFromBody(body: String): String {
        return try {
            // Simple parsing - look for command parameter
            val lines = body.split("&")
            for (line in lines) {
                if (line.startsWith("command=")) {
                    return URLDecoder.decode(line.substring(8), "UTF-8")
                }
            }
            ""
        } catch (e: Exception) {
            onLogMessage("Error parsing command: ${e.message}")
            ""
        }
    }
    
    private fun parseStartParamsFromBody(body: String): Map<String, String> {
        return try {
            val params = mutableMapOf<String, String>()
            val lines = body.split("&")
            for (line in lines) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    params[key] = value
                }
            }
            params
        } catch (e: Exception) {
            onLogMessage("Error parsing start params: ${e.message}")
            emptyMap()
        }
    }
    
    private fun executeAdbCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            // Read output
            var line: String?
            while (outputReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            // Read errors
            while (errorReader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            
            val result = StringBuilder()
            result.append("{\n")
            result.append("  \"exitCode\": $exitCode,\n")
            result.append("  \"output\": \"${escapeJson(output.toString())}\",\n")
            result.append("  \"error\": \"${escapeJson(error.toString())}\"\n")
            result.append("}")
            
            onLogMessage("Command executed with exit code: $exitCode")
            result.toString()
            
        } catch (e: Exception) {
            onLogMessage("Error executing command: ${e.message}")
            """{"exitCode": -1, "output": "", "error": "${escapeJson(e.message ?: "Unknown error")}"}"""
        }
    }
    
    private fun startActivityWithParams(params: Map<String, String>, forceForeground: Boolean = false): String {
        return try {
            val intent = Intent()
            
            // Parse action (-a)
            params["action"]?.let { action ->
                intent.action = action
            }
            
            // Parse data (-d)
            params["data"]?.let { data ->
                intent.data = Uri.parse(data)
            }
            
            // Parse categories (-c)
            params["category"]?.let { category ->
                intent.addCategory(category)
            }
            
            // Parse component (-n)
            params["component"]?.let { component ->
                val parts = component.split("/")
                if (parts.size == 2) {
                    intent.setClassName(parts[0], parts[1])
                }
            }
            
            // Parse flags (-f)
            params["flags"]?.let { flags ->
                try {
                    val flagValue = flags.toInt()
                    intent.flags = flagValue
                } catch (e: NumberFormatException) {
                    onLogMessage("Invalid flags value: $flags")
                }
            }
            
            // Parse extras (-e)
            params["extras"]?.let { extras ->
                try {
                    // Simple parsing for extras in format "key:value,key2:value2"
                    val extraPairs = extras.split(",")
                    for (pair in extraPairs) {
                        val keyValue = pair.split(":", limit = 2)
                        if (keyValue.size == 2) {
                            intent.putExtra(keyValue[0], keyValue[1])
                        }
                    }
                } catch (e: Exception) {
                    onLogMessage("Error parsing extras: ${e.message}")
                }
            }
            
            // Start activity with flags for background execution
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            
            // If forceForeground is true, bring app to foreground first
            if (forceForeground) {
                tryBringAppToForeground()
                Thread.sleep(500) // Give some time for the app to come to foreground
            }

            // Try to start activity, handle SecurityException
            try {
                context.startActivity(intent)
            } catch (e: SecurityException) {
                onLogMessage("SecurityException: ${e.message}")
                // Try alternative method using PendingIntent
                tryStartActivityWithPendingIntent(intent)
            }
            
            val result = StringBuilder()
            result.append("{\n")
            result.append("  \"success\": true,\n")
            result.append("  \"message\": \"Activity started successfully\",\n")
            result.append("  \"intent\": {\n")
            result.append("    \"action\": \"${intent.action ?: "null"}\",\n")
            result.append("    \"data\": \"${intent.dataString ?: "null"}\",\n")
            result.append("    \"categories\": \"${intent.categories?.joinToString(",") ?: "null"}\",\n")
            result.append("    \"component\": \"${intent.component?.className ?: "null"}\"\n")
            result.append("  }\n")
            result.append("}")
            
            onLogMessage("Activity started successfully")
            result.toString()
            
        } catch (e: Exception) {
            onLogMessage("Error starting activity: ${e.message}")
            """{"success": false, "message": "${escapeJson(e.message ?: "Unknown error")}"}"""
        }
    }
    
    private fun tryStartActivityWithPendingIntent(intent: Intent) {
        try {
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                pendingIntentFlags
            )
            
            // Try to send the pending intent
            pendingIntent.send()
            onLogMessage("Activity started using PendingIntent")
            
        } catch (e: Exception) {
            onLogMessage("Failed to start activity with PendingIntent: ${e.message}")
            // Last resort: try to bring app to foreground first
            tryBringAppToForeground()
        }
    }
    
    private fun tryBringAppToForeground() {
        try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
                onLogMessage("Brought app to foreground")
            }
        } catch (e: Exception) {
            onLogMessage("Failed to bring app to foreground: ${e.message}")
        }
    }
    
    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    private fun sendErrorResponse(writer: PrintWriter, message: String) {
        val response = """{"error": "$message"}"""
        writer.println("HTTP/1.1 400 Bad Request")
        writer.println("Content-Type: application/json")
        writer.println("Content-Length: ${response.length}")
        writer.println("Connection: close")
        writer.println()
        writer.println(response)
    }
}
