package test.accelerometer

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import kotlin.math.sqrt

/**
 * SensorService - класс службы, которая в фоне получчает показания акселерометра
 * и передает их широковещательным сообщением
 */
class SensorService : Service(), SensorEventListener {

    private val binder = SensorServiceBinder()
    private lateinit var sensorManager: SensorManager
    private var sensorAcceleration: Sensor? = null
    private var isListeningStarted = false
    private var stepsCount = 0
    private var lastUpdate = 0L

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "test.accelerometer.service"
        const val NOTIFICATION_ID = 100
        const val BROADCAST_ACTION_STEPS = "test.accelerometer.broadcast.steps"
        const val BROADCAST_ACTION_STATE = "test.accelerometer.broadcast.state"
        const val BROADCAST_DATA_STEPS = "steps"
        const val BROADCAST_WORK_STATE = "work_state"
        const val SERVICE_ACTION_START = "action_start"
        const val SERVICE_ACTION_STOP = "action_stop"
    }

    override fun onCreate() {
        super.onCreate()
        // получаем системный менеджер сенсоров
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // получаем сенсор вкселерометра с учетом гравитации
        sensorAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SERVICE_ACTION_START -> {
                if (sensorAcceleration != null) {
                    // регистрируем слушателя изменения состояний сенсора
                    sensorManager.registerListener(this, sensorAcceleration, SensorManager.SENSOR_DELAY_NORMAL)
                    isListeningStarted = true
                    sendBroadcastStateInfo()
                    sendBroadcastStepsInfo()
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
            SERVICE_ACTION_STOP -> {
                // удаляем слушателя
                sensorManager.unregisterListener(this)
                isListeningStarted = false
                // очищаем счетчик
                stepsCount = 0
                sendBroadcastStateInfo()
                stopForeground(true)
            }
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var channel = service.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
        if (channel == null) {
            channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_NONE
            )
            channel.lightColor = Color.BLUE
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            service.createNotificationChannel(channel)
        }

        return channelId
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(applicationContext, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0,
            notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(NOTIFICATION_CHANNEL_ID, "Sensor Service")
        } else {
            ""
        }

        val text = "${getString(R.string.steps)}: $stepsCount"
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentText(text)
            .setContentTitle(getString(R.string.pedometer))
            .setPriority(Notification.PRIORITY_HIGH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setTicker(text)
            .setWhen(System.currentTimeMillis())

        return builder.build()
    }

    fun getSensorState(): Boolean {
        return sensorAcceleration != null
    }

    fun getListeningState(): Boolean {
        return isListeningStarted
    }

    fun getSteps(): Int {
        return stepsCount
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            // векторы ускорения по осям
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            // длина вектора ускорения
            val acceleration = sqrt(ax * ax + ay * ay + az * az)

            // считаем за шаги при значении ускорения больше, чем 3
            if (acceleration > 3) {
                val currentTime = System.currentTimeMillis()
                if ((currentTime - lastUpdate) > 300) {
                    // счиитаем только если между событиями прошло больше 300 мс
                    // для предотвращения ложных срабатываний
                    lastUpdate = currentTime
                    stepsCount++
                    // отправляем данные для отображения
                    sendBroadcastStepsInfo()
                    // изменяем данные в нотификации
                    val notification = buildNotification()
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun sendBroadcastStepsInfo() {
        sendBroadcastInfo(BROADCAST_ACTION_STEPS, bundleOf(BROADCAST_DATA_STEPS to stepsCount))
    }

    private fun sendBroadcastStateInfo() {
        sendBroadcastInfo(BROADCAST_ACTION_STATE, bundleOf(BROADCAST_WORK_STATE to isListeningStarted))
    }

    private fun sendBroadcastInfo(action: String, data: Bundle) {
        val intent = Intent()
        intent.action = action
        intent.putExtras(data)
        sendBroadcast(intent)
    }

    inner class SensorServiceBinder : Binder() {
        fun getService(): SensorService {
            return this@SensorService
        }
    }

}
