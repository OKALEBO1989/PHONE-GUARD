package com.okaleb.phoneguard

import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.net.ssl.SSLSocketFactory

object Mailer {
    private fun b64(s: String): String =
        Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)

    fun send(user: String, pass: String, to: List<String>,
             subject: String, body: String): Boolean {
        return try {
            val socket = SSLSocketFactory.getDefault().createSocket("smtp.gmail.com", 465)
            socket.soTimeout = 20000
            val r = BufferedReader(InputStreamReader(socket.getInputStream()))
            val w = OutputStreamWriter(socket.getOutputStream())

            fun reply(): String {
                var line = r.readLine() ?: return ""
                while (line.length > 3 && line[3] == '-') {
                    line = r.readLine() ?: break
                }
                return line
            }
            fun cmd(c: String): String {
                w.write(c + "\r\n")
                w.flush()
                return reply()
            }

            reply()
            cmd("EHLO phoneguard")
            cmd("AUTH LOGIN")
            cmd(b64(user))
            val auth = cmd(b64(pass))
            if (!auth.startsWith("235")) {
                socket.close()
                return false
            }
            cmd("MAIL FROM:<" + user + ">")
            for (t in to) cmd("RCPT TO:<" + t + ">")
            cmd("DATA")
            w.write("From: " + user + "\r\n")
            w.write("To: " + to.joinToString(", ") + "\r\n")
            w.write("Subject: " + subject + "\r\n")
            w.write("Content-Type: text/plain; charset=utf-8\r\n\r\n")
            w.write(body + "\r\n.\r\n")
            w.flush()
            val done = reply()
            cmd("QUIT")
            socket.close()
            done.startsWith("250")
        } catch (e: Exception) {
            false
        }
    }
}
