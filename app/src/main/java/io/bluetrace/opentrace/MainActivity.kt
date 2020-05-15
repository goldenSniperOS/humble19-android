package io.bluetrace.opentrace

//import android.app.ActivityManager
//import android.content.Context
import android.content.Intent
import android.os.Bundle
//import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.FragmentManager
//import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.iid.FirebaseInstanceId
//import kotlinx.android.synthetic.main.activity_main_new.*
//import io.bluetrace.opentrace.fragment.ForUseByOTCFragment
//import io.bluetrace.opentrace.fragment.HomeFragment
import kotlinx.android.synthetic.main.fragment_home.*
import io.bluetrace.opentrace.logging.CentralLog

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_home)
        Utils.startBluetoothMonitoringService(this)
        animation_view.setOnClickListener {
            if (BuildConfig.DEBUG && ++counter == 2) {
                counter = 0
                var intent = Intent(this, PeekActivity::class.java)
                this?.startActivity(intent)
            }
        }
        getFCMToken()
    }

    private fun getFCMToken() {
        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener { task ->
                if (!task.isSuccessful()) {
                    CentralLog.w(TAG, "failed to get fcm token ${task.exception}")
                    return@addOnCompleteListener
                } else {
                    // Get new Instance ID token
                    val token = task.result?.token
                    // Log and toast
                    CentralLog.d(TAG, "FCM token: $token")
                }
            }


    }
}
