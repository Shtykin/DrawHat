package com.esh.camerax_v3


import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.camera.core.*
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import androidx.core.graphics.red
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.esh.camerax_v3.databinding.ActivityMainBinding
import com.google.android.material.shape.TriangleEdgeTreatment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var imageV: ImageView
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        viewBinding.imageCaptureButton.setOnClickListener { changeCamera() }
        imageV=findViewById(R.id.imageV)
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun changeCamera(){
        Log.d ("CameraXApp", "changeCamera")
        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            startCamera()
        }
        else {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            imageCapture = ImageCapture.Builder().build()
            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, imageAnalysis)
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
            }
            catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        },ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 0
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                }
            }.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            Log.d("CameraXApp", "requestCode=$requestCode")
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private var imageAnalysis = ImageAnalysis.Analyzer { imageProxy ->
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val heightImage=image.height
            val widthtImage=image.width
            val detector = FaceDetection.getClient(options)
            val result = detector.process(image)
                .addOnSuccessListener { faces ->
                    processFaceList(faces, heightImage, widthtImage)
                }.addOnCompleteListener {
                    imageProxy.close()
                }
                .addOnFailureListener { e ->
                }
        }
    }

    private fun processFaceList(faces: List<Face>, heightImage: Int, widthImage: Int) {
        if (faces.isNullOrEmpty()) imageV.visibility= View.INVISIBLE
        else imageV.visibility= View.VISIBLE
        for (face in faces) {
            val bitmap: Bitmap = Bitmap.createBitmap(
                viewBinding.viewFinder.resources.displayMetrics.widthPixels,
                viewBinding.viewFinder.resources.displayMetrics.heightPixels,
                Bitmap.Config.ARGB_8888
            )
            val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
            if (leftEyeContour != null) {
                drawPoint(leftEyeContour, bitmap, Color.YELLOW, heightImage, widthImage)
            }
            val rightEyeContour = face.getContour(FaceContour.RIGHT_EYE)?.points
            if (rightEyeContour != null) {
                drawPoint(rightEyeContour, bitmap, Color.YELLOW, heightImage, widthImage)
            }
            val upperLipBottomContour = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
            if (upperLipBottomContour != null) {
                drawPoint(upperLipBottomContour, bitmap, Color.RED, heightImage, widthImage)
            }
            val lowerLipTopContour = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
            if (lowerLipTopContour != null) {
                drawPoint(lowerLipTopContour, bitmap, Color.RED, heightImage, widthImage)
            }
            val faceContour = face.getContour(FaceContour.FACE)?.points
            if (faceContour != null) {
                drawPoint(faceContour, bitmap, Color.WHITE, heightImage, widthImage)
                drawHat(faceContour, bitmap, Color.BLACK, heightImage, widthImage)
            }
            imageV.background = BitmapDrawable(getResources(), bitmap)
        }
    }

    @SuppressLint("ResourceAsColor")
    fun drawPoint (contour: List<PointF>, bitmap: Bitmap, color: Int, heightImage: Int, widthtImage: Int){
        var a=contour
        a.forEach { pointF ->
            var left = 0.0
            var top = 0.0
            var right = 0.0
            var bottom = 0.0

            val heightView=imageV.height
            val widthView=imageV.width

        if (heightView>widthView){
            left=pointF.x.toDouble()*heightView/widthtImage-((heightImage*heightView/widthtImage-widthView)/2)
            top=pointF.y.toDouble()*heightView/widthtImage
        }
            else{
            left=pointF.x.toDouble()*widthView/widthtImage
            top=pointF.y.toDouble()*widthView/widthtImage-((heightImage*widthView/widthtImage-heightView)/2)
        }
            right=left+10.0
            bottom=top+10.0
            if (cameraSelector== CameraSelector.DEFAULT_FRONT_CAMERA){
                right=widthView-right
                left=right-10.0
            }
            var cLeft = left.toInt()
            var cTop = top.toInt()
            var cRight = right.toInt()
            var cBottom = bottom.toInt()
            var shapeDrawable: ShapeDrawable

            val canvas: Canvas = Canvas(bitmap)
            shapeDrawable = ShapeDrawable(OvalShape())
            shapeDrawable.setBounds( cLeft, cTop, cRight, cBottom)
            shapeDrawable.getPaint().setColor(color)
            shapeDrawable.draw(canvas)
        }
    }

    fun drawHat(contour: List<PointF>, bitmap: Bitmap, color: Int, heightImage: Int, widthtImage: Int){
        var a=contour
        var i=0
        lateinit var point1:PointF
        lateinit var point2:PointF

        a.forEach { pointF ->
            if (i==3) point1=pointF
            if (i==32) point2=pointF
            i++
        }

        var left = 0.0
        var top = 0.0
        var right = 0.0
        var bottom = 0.0

        val heightView=imageV.height
        val widthView=imageV.width

        if (heightView>widthView){
            left=point2.x.toDouble()*heightView/widthtImage-((heightImage*heightView/widthtImage-widthView)/2)
            top=point2.y.toDouble()*heightView/widthtImage
            right=point1.x.toDouble()*heightView/widthtImage-((heightImage*heightView/widthtImage-widthView)/2)
            bottom=point2.y.toDouble()*heightView/widthtImage
        }
        else{
            left=point2.x.toDouble()*widthView/widthtImage
            top=point2.y.toDouble()*widthView/widthtImage-((heightImage*widthView/widthtImage-heightView)/2)
            right=point1.x.toDouble()*widthView/widthtImage
            bottom=point2.y.toDouble()*widthView/widthtImage-((heightImage*widthView/widthtImage-heightView)/2)
        }

        if (cameraSelector== CameraSelector.DEFAULT_FRONT_CAMERA){
           val  temp=right
            right = widthView-left
            left = widthView-temp
        }
        var cLeft = left.toInt()
        var cTop = top.toInt()
        var cRight = right.toInt()
        var cBottom = bottom.toInt()

        val temp = cRight-cLeft
        cTop=cTop-temp/2
        cLeft=cLeft-temp/3
        cRight=cRight+temp/3
        cBottom=cBottom-temp/4

        var shapeDrawable: ShapeDrawable
        val canvas: Canvas = Canvas(bitmap)
        shapeDrawable =ShapeDrawable(RectShape())
        shapeDrawable.setBounds( cLeft, cTop, cRight, cBottom)
        shapeDrawable.getPaint().setColor(color)
        shapeDrawable.draw(canvas)

        shapeDrawable =ShapeDrawable(RectShape())
        shapeDrawable.setBounds( (cLeft+cRight)/2-temp/2, cTop-temp, (cLeft+cRight)/2+temp/2, cTop)
        shapeDrawable.getPaint().setColor(color)
        shapeDrawable.draw(canvas)
    }
}

