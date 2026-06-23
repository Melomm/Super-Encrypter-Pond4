package com.superencrypter.data.scan

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.superencrypter.SuperEncrypterApplication
import com.superencrypter.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ManualScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            progressNotification("Mantendo conexao com o backend ate a analise terminar.")
        )
        ManualScanProgressBus.started()

        scope.launch {
            val repository = (application as SuperEncrypterApplication).container.repository
            runCatching {
                repository.scanPickedFile(uri) { status ->
                    ManualScanProgressBus.status(status)
                    updateProgressNotification(status)
                }
            }.onSuccess { result ->
                ManualScanProgressBus.completed(result)
            }.onFailure { error ->
                val message = error.message ?: "Nao foi possivel concluir o scan."
                ManualScanProgressBus.failed(message)
                repository.notifyScanFailed(message)
            }
            ServiceCompat.stopForeground(this@ManualScanService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(status: String) =
        NotificationCompat.Builder(this, AppConstants.SCAN_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("Scan em andamento")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun updateProgressNotification(status: String) {
        startForeground(NOTIFICATION_ID, progressNotification(status))
    }

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val NOTIFICATION_ID = 42_001

        fun intent(context: Context, uri: Uri): Intent =
            Intent(context, ManualScanService::class.java)
                .putExtra(EXTRA_URI, uri.toString())
    }
}
