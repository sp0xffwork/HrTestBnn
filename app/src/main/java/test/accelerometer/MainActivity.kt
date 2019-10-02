package test.accelerometer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var sensorService: SensorService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val serviceClass = SensorService::class.java
        val serviceIntent = Intent(this, serviceClass)

        // подключаемся к сервису
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // кнопка запуска шагомера
        buttonStart.setOnClickListener {
            val startServiceIntent = Intent(this, serviceClass)
            startServiceIntent.action = SensorService.SERVICE_ACTION_START
            ContextCompat.startForegroundService(this, startServiceIntent)
        }

        // кнопка останова шагомера
        buttonStop.setOnClickListener {
            val stopServiceIntent = Intent(this, serviceClass)
            stopServiceIntent.action = SensorService.SERVICE_ACTION_STOP
            ContextCompat.startForegroundService(this, stopServiceIntent)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
    }

    override fun onResume() {
        super.onResume()

        // запускаем прослушивание сообщений от сервиса
        registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(SensorService.BROADCAST_ACTION_STEPS)
            addAction(SensorService.BROADCAST_ACTION_STATE)
        })

        // перерисовка экрана после (например) разблокировки
        redraw()
    }

    override fun onPause() {
        super.onPause()
        // останавливаем прослушивание в момент неактивности
        unregisterReceiver(broadcastReceiver)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {

            // получаем доступ к сервису, чтобы запрашивать данные напрямую
            val binder = service as SensorService.SensorServiceBinder
            sensorService = binder.getService()

            // перерисовка экрана после пересоздания активити.
            redraw()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            sensorService = null
        }
    }

    private fun redraw() {
        sensorService?.run {
            // получаем состояние сенсора
            val isSensorAvailable = getSensorState()
            if (isSensorAvailable) {
                // акселерометр присутствует
                // делаем видимыми количество шагов
                layoutValue.visibility = View.VISIBLE
                textNotAvailable.visibility = View.GONE
                // получаем из сервиса шаги
                val steps = getSteps()
                drawSteps(steps)
                // получаем из сервиса состояние
                val listeningState = getListeningState()
                setButtonsState(true, listeningState)
            } else {
                // акселерометр отсутствует
                // скрываем надпись с количеством шагов
                // делаем видимой надпись о недоступности
                layoutValue.visibility = View.GONE
                textNotAvailable.visibility = View.VISIBLE
                // скрываем кнопки
                setButtonsState(availableState = false, workState = true)
            }
        }
    }

    private fun setButtonsState(availableState: Boolean, workState: Boolean) {
        if (!availableState) {
            buttonStart.visibility = View.GONE
            buttonStop.visibility = View.GONE
        } else if (workState) {
            buttonStart.visibility = View.GONE
            buttonStop.visibility = View.VISIBLE
        } else {
            buttonStart.visibility = View.VISIBLE
            buttonStop.visibility = View.GONE
        }
    }

    private fun drawSteps(steps: Int) {
        textSteps.text = steps.toString()
    }

    // слушатель сообщений от сервиса
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            when (intent?.action) {
                // информация о шагах
                SensorService.BROADCAST_ACTION_STEPS -> {
                    val steps = intent.getIntExtra(SensorService.BROADCAST_DATA_STEPS, 0)
                    drawSteps(steps)
                }
                // информация о состоянии (запущен/остановлен)
                SensorService.BROADCAST_ACTION_STATE -> {
                    val listeningState = intent.getBooleanExtra(SensorService.BROADCAST_WORK_STATE, false)
                    setButtonsState(true, listeningState)
                }
            }
        }
    }
}
