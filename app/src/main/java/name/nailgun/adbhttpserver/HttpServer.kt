package name.nailgun.adbhttpserver

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.concurrent.Executors

class HttpServer(private val port: Int, private val onLogMessage: (String) -> Unit) {
    
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
