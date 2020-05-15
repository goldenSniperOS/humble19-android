package io.bluetrace.opentrace

//import android.app.ActivityManager
//import android.content.Context
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
//import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.FragmentManager
//import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.google.gson.Gson
//import kotlinx.android.synthetic.main.activity_main_new.*
//import io.bluetrace.opentrace.fragment.ForUseByOTCFragment
//import io.bluetrace.opentrace.fragment.HomeFragment
import kotlinx.android.synthetic.main.fragment_home.*
import io.bluetrace.opentrace.logging.CentralLog
import io.bluetrace.opentrace.services.BluetoothMonitoringService
import io.bluetrace.opentrace.status.persistence.StatusRecord
import io.bluetrace.opentrace.status.persistence.StatusRecordStorage
import io.bluetrace.opentrace.streetpass.persistence.StreetPassRecord
import io.bluetrace.opentrace.streetpass.persistence.StreetPassRecordStorage
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private var counter = 0
    private var disposeObj: Disposable? = null

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
        buttonUpload.setOnClickListener {
            uploadRecords()
        }
        getFCMToken()

    }

    private fun uploadRecords() {
        CentralLog.i(TAG, "Uploading Records")
        var observableStreetRecords = Observable.create<List<StreetPassRecord>> {
            val result = StreetPassRecordStorage(this.applicationContext).getAllRecords()
            it.onNext(result)
        }
        var observableStatusRecords = Observable.create<List<StatusRecord>> {
            val result = StatusRecordStorage(this.applicationContext).getAllRecords()
            it.onNext(result)
        }

        disposeObj = Observable.zip(observableStreetRecords, observableStatusRecords,
            BiFunction<List<StreetPassRecord>, List<StatusRecord>, ExportData> { records, status ->
                ExportData(records, status)
            }
        ).observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe { exportedData ->
                Log.d(TAG, "records: ${exportedData.recordList}")
                Log.d(TAG, "status: ${exportedData.statusList}")
                try {
                    var task = writeToInternalStorageAndUpload(
                        this.applicationContext,
                        exportedData.recordList,
                        exportedData.statusList
                    )
                    task.addOnFailureListener {
                        CentralLog.d(TAG, "failed to upload")
                    }.addOnSuccessListener {
                        CentralLog.d(TAG, "uploaded successfully")
                    }
                } catch (e: Throwable) {
                    CentralLog.d(TAG, "Failed to upload data: ${e.message}")
                }
            }
    }

    private fun writeToInternalStorageAndUpload(
        context: Context,
        deviceDataList: List<StreetPassRecord>,
        statusList: List<StatusRecord>
    ): UploadTask {
        var date = Utils.getDateFromUnix(System.currentTimeMillis())
        var gson = Gson()

        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        var updatedDeviceList = deviceDataList.map {
            it.timestamp = it.timestamp / 1000
            return@map it
        }

        var updatedStatusList = statusList.map {
            it.timestamp = it.timestamp / 1000
            return@map it
        }

        var map: MutableMap<String, Any> = HashMap()
        map["records"] = updatedDeviceList as Any
        map["events"] = updatedStatusList as Any

        val mapString = gson.toJson(map)

        val fileName = "StreetPassRecord_${manufacturer}_${model}_$date.json"
        val fileOutputStream: FileOutputStream

        val uploadDir = File(context.filesDir, "upload")

        if (uploadDir.exists()) {
            uploadDir.deleteRecursively()
        }

        uploadDir.mkdirs()
        val fileToUpload = File(uploadDir, fileName)
        fileOutputStream = FileOutputStream(fileToUpload)

        fileOutputStream.write(mapString.toByteArray())
        fileOutputStream.close()

        CentralLog.i(TAG, "File wrote: ${fileToUpload.absolutePath}")

        return uploadToCloudStorage(context, fileToUpload)
    }

    private fun uploadToCloudStorage(context: Context, fileToUpload: File): UploadTask {
        CentralLog.d(TAG, "Uploading to Cloud Storage")

        val bucketName = BuildConfig.FIREBASE_UPLOAD_BUCKET
        val storage = FirebaseStorage.getInstance("gs://${bucketName}")
        var storageRef = storage.getReferenceFromUrl("gs://${bucketName}")

        val dateString = SimpleDateFormat("YYYYMMdd").format(Date())
        var streetPassRecordsRef =
            storageRef.child("streetPassRecords/$dateString/${fileToUpload.name}")

        val fileUri: Uri =
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                fileToUpload
            )

        var uploadTask = streetPassRecordsRef.putFile(fileUri)
        uploadTask.addOnCompleteListener {
            try {
                fileToUpload.delete()
                CentralLog.i(TAG, "upload file deleted")
            } catch (e: Exception) {
                CentralLog.e(TAG, "Failed to delete upload file")
            }
        }
        return uploadTask
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
