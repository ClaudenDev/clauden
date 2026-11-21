package space.teleopen.clauden.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import space.teleopen.clauden.ui.ClaudenLogo
import space.teleopen.clauden.ui.ClaudenLogoVariant
import space.teleopen.clauden.ui.ClaudenTheme
import space.teleopen.clauden.ui.LocalClaudenColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val type = intent?.getStringExtra(ClaudenAlarmScheduler.EXTRA_TYPE) ?: "timer"
        val message = intent?.getStringExtra(ClaudenAlarmScheduler.EXTRA_MESSAGE).orEmpty()

        setContent {
            ClaudenTheme {
                AlarmScreen(type = type, message = message, onStop = ::stopAlarm)
            }
        }
    }

    private fun stopAlarm() {
        runCatching {
            startService(
                Intent(this, ClaudenAlarmService::class.java)
                    .setAction(ClaudenAlarmService.ACTION_STOP)
            )
        }
        NotificationManagerCompat.from(this)
            .cancel(ClaudenAlarmNotifications.NOTIFICATION_ID)
        finish()
    }
}

@Composable
private fun AlarmScreen(type: String, message: String, onStop: () -> Unit) {
    val colors = LocalClaudenColors.current
    BackHandler { onStop() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            ClaudenLogo(
                modifier = Modifier.size(104.dp),
                tint = colors.clay,
                variant = ClaudenLogoVariant.Jelly,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (type == "alarm") "Будильник" else "Таймер",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = SimpleDateFormat("HH:mm", Locale("ru")).format(Date()),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 64.sp,
                    lineHeight = 70.sp,
                    fontWeight = FontWeight.Light,
                ),
                color = colors.textPrimary,
            )
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                    ),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(36.dp))
            Surface(
                color = colors.clay,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .height(56.dp)
                    .clickable(onClick = onStop),
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 56.dp)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Стоп",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
