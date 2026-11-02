package com.talotv.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Talo TV Shield VPN Service
 *
 * Provides a lightweight local DNS proxy that intercepts ad and tracker requests
 * without routing or slowing down actual video/media streams.
 *
 * CRITICAL CAST COMPATIBILITY:
 * - Google Cast receiver (com.google.android.apps.mediashell) and Play Services (com.google.android.gms)
 *   are strictly excluded from the VPN tunnel via addDisallowedApplication().
 * - allowBypass() is enabled so local network connections, mDNS, and SSDP bypass the VPN entirely.
 * - Local subnets are never routed through the tunnel, guaranteeing 100% flawless phone-to-TV casting.
 */
class TaloVpnService : VpnService() {

    companion object {
        const val TAG = "TaloVpnService"
        const val ACTION_START = "com.talotv.launcher.VPN_START"
        const val ACTION_STOP = "com.talotv.launcher.VPN_STOP"
        const val ACTION_RELOAD = "com.talotv.launcher.VPN_RELOAD"
        const val CHANNEL_ID = "talo_shield_vpn"
        const val NOTIFICATION_ID = 42001

        const val PRIMARY_DNS = "94.140.14.14" // AdGuard Default DNS (Blocks Ads & Trackers)
        const val SECONDARY_DNS = "94.140.15.15" // AdGuard Default Secondary

        val isRunning = AtomicBoolean(false)

        fun start(context: Context) {
            val intent = Intent(context, TaloVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TaloVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun reload(context: Context) {
            if (isRunning.get()) {
                val intent = Intent(context, TaloVpnService::class.java).apply {
                    action = ACTION_RELOAD
                }
                context.startService(intent)
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerThread: Thread? = null
    private val shouldRun = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
            ACTION_RELOAD -> restartVpn()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bouclier Talo TV",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Protection anti-publicités et anti-traqueurs active"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Bouclier Talo TV")
            .setContentText("Protection anti-pubs et anti-traqueurs active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @Synchronized
    private fun startVpn() {
        if (isRunning.get()) return

        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error", e)
        }

        val builder = Builder()
            .setSession("Talo Shield")
            .addAddress("10.240.0.1", 32)
            .addDnsServer(PRIMARY_DNS)
            .addDnsServer(SECONDARY_DNS)
            .addRoute(PRIMARY_DNS, 32)
            .addRoute(SECONDARY_DNS, 32)

        // Allow apps to bypass VPN for local subnets and direct connections
        builder.allowBypass()

        // 1. ALWAYS exclude Google Cast and core Android TV receiver packages
        // This guarantees that phone-to-TV casting, YouTube cast, and remote control NEVER break!
        val criticalCastPackages = listOf(
            "com.google.android.apps.mediashell",
            "com.google.android.gms",
            "com.google.android.apps.chromecast.app",
            "com.google.android.backdrop",
            "com.google.android.tv.remote.service",
            packageName,
            "com.talotv.launcher",
            "com.talotv.launcher.debug"
        )
        for (pkg in criticalCastPackages) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {}
        }

        // 2. Read user preferences for disabled apps in Shield
        try {
            val keptStr = getSharedPreferences("page", MODE_PRIVATE).getString("tvhome.settings", null)
            if (keptStr != null) {
                val obj = JSONObject(keptStr)
                val s = obj.optJSONObject("s") ?: obj
                val dis = s.optJSONArray("shield_disabled")
                if (dis != null) {
                    for (i in 0 until dis.length()) {
                        val disPkg = dis.getString(i)
                        if (disPkg == "system") {
                            // User disabled protection for system telemetry
                            val systemPkgs = listOf(
                                "com.google.android.tvrecommendations",
                                "com.google.android.tvlauncher",
                                "com.google.android.apps.tv.launcherx",
                                "com.google.android.feedback",
                                "com.google.android.partnersetup"
                            )
                            for (sp in systemPkgs) {
                                try { builder.addDisallowedApplication(sp) } catch (_: Exception) {}
                            }
                        } else {
                            try {
                                builder.addDisallowedApplication(disPkg)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying shield exclusions", e)
        }

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "builder.establish() returned null")
                stopSelf()
                return
            }

            isRunning.set(true)
            shouldRun.set(true)
            startDnsWorker(vpnInterface!!)
            Log.i(TAG, "Talo Shield VPN successfully started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            stopSelf()
        }
    }

    private fun startDnsWorker(pfd: ParcelFileDescriptor) {
        workerThread = Thread({
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val upstreamAddress = InetAddress.getByName(PRIMARY_DNS)
            val secondaryAddress = InetAddress.getByName(SECONDARY_DNS)

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 2500
                // Protect the socket so its packets bypass the VPN
                protect(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create protected UDP socket", e)
                return@Thread
            }

            val inBuf = ByteArray(32768)
            val outBuf = ByteArray(4096)
            val outPacket = DatagramPacket(outBuf, outBuf.size)
            val replyBuf = ByteArray(4096)

            while (shouldRun.get()) {
                try {
                    val length = inputStream.read(inBuf)
                    if (length <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    // Check for IPv4 UDP packet
                    val versionAndIhl = inBuf[0].toInt() and 0xFF
                    val version = versionAndIhl shr 4
                    if (version != 4) continue // Only IPv4 handled

                    val ihl = (versionAndIhl and 0x0F) * 4
                    if (length < ihl + 8) continue

                    val protocol = inBuf[9].toInt() and 0xFF
                    if (protocol != 17) continue // Protocol 17 = UDP

                    val destPort = ((inBuf[ihl + 2].toInt() and 0xFF) shl 8) or (inBuf[ihl + 3].toInt() and 0xFF)
                    if (destPort != 53) continue // Only DNS port 53

                    val srcPort = ((inBuf[ihl].toInt() and 0xFF) shl 8) or (inBuf[ihl + 1].toInt() and 0xFF)
                    val udpLen = ((inBuf[ihl + 4].toInt() and 0xFF) shl 8) or (inBuf[ihl + 5].toInt() and 0xFF)
                    val dnsPayloadLen = udpLen - 8
                    if (dnsPayloadLen <= 0 || ihl + 8 + dnsPayloadLen > length) continue

                    // Zero-copy DNS query packet forwarding
                    val queryPacket = DatagramPacket(inBuf, ihl + 8, dnsPayloadLen, upstreamAddress, 53)
                    try {
                        socket.send(queryPacket)
                        outPacket.length = outBuf.size
                        socket.receive(outPacket)
                    } catch (_: java.net.SocketTimeoutException) {
                        // Fallback to secondary DNS on primary timeout
                        queryPacket.address = secondaryAddress
                        socket.send(queryPacket)
                        outPacket.length = outBuf.size
                        socket.receive(outPacket)
                    }

                    val respLen = outPacket.length
                    if (respLen <= 0) continue

                    val totalReplyLen = 20 + 8 + respLen
                    if (totalReplyLen > replyBuf.size) continue

                    // IPv4 Header
                    replyBuf[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
                    replyBuf[1] = 0x00.toByte()
                    replyBuf[2] = ((totalReplyLen shr 8) and 0xFF).toByte()
                    replyBuf[3] = (totalReplyLen and 0xFF).toByte()
                    replyBuf[4] = 0x00.toByte()
                    replyBuf[5] = 0x00.toByte()
                    replyBuf[6] = 0x40.toByte() // Flags: Don't Fragment
                    replyBuf[7] = 0x00.toByte()
                    replyBuf[8] = 64.toByte() // TTL 64
                    replyBuf[9] = 17.toByte() // Protocol UDP

                    // Swap IPs: reply source is packet's destination, reply destination is packet's source
                    System.arraycopy(inBuf, 16, replyBuf, 12, 4) // Src IP
                    System.arraycopy(inBuf, 12, replyBuf, 16, 4) // Dst IP

                    // IPv4 Header Checksum
                    replyBuf[10] = 0
                    replyBuf[11] = 0
                    val ipCheck = computeIpChecksum(replyBuf, 0, 20)
                    replyBuf[10] = ((ipCheck.toInt() shr 8) and 0xFF).toByte()
                    replyBuf[11] = (ipCheck.toInt() and 0xFF).toByte()

                    // UDP Header (starts at byte 20)
                    val replyUdpLen = 8 + respLen
                    replyBuf[20] = ((destPort shr 8) and 0xFF).toByte() // UDP Src Port (53)
                    replyBuf[21] = (destPort and 0xFF).toByte()
                    replyBuf[22] = ((srcPort shr 8) and 0xFF).toByte()  // UDP Dst Port (client port)
                    replyBuf[23] = (srcPort and 0xFF).toByte()
                    replyBuf[24] = ((replyUdpLen shr 8) and 0xFF).toByte()
                    replyBuf[25] = (replyUdpLen and 0xFF).toByte()
                    replyBuf[26] = 0x00.toByte() // Checksum 0 is valid for IPv4 UDP
                    replyBuf[27] = 0x00.toByte()

                    // Payload (starts at byte 28)
                    System.arraycopy(outPacket.data, 0, replyBuf, 28, respLen)

                    // Write response back into the TUN
                    outputStream.write(replyBuf, 0, totalReplyLen)
                    outputStream.flush()
                } catch (_: java.net.SocketTimeoutException) {
                    // Upstream timeout, continue
                } catch (e: Exception) {
                    if (!shouldRun.get()) break
                }
            }

            try { socket.close() } catch (_: Exception) {}
            try { inputStream.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }, "TaloShieldDnsWorker")
        workerThread?.isDaemon = true
        workerThread?.start()
    }

    private fun computeIpChecksum(header: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while ((sum shr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }

    @Synchronized
    private fun restartVpn() {
        stopVpn()
        startVpn()
    }

    @Synchronized
    private fun stopVpn() {
        shouldRun.set(false)
        isRunning.set(false)

        try {
            workerThread?.interrupt()
            workerThread = null
        } catch (_: Exception) {}

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}

        stopSelf()
        Log.i(TAG, "Talo Shield VPN stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
