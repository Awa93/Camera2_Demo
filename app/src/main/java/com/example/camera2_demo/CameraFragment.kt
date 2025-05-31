package com.example.camera2_demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.camera2_demo.databinding.LayoutFragmentCameraBinding
import java.io.File
import java.util.*

class CameraFragment : Fragment() {

    private var _binding: LayoutFragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var sensorOrientation = 0


    private lateinit var cameraId: String
    private var isFrontCamera = false
    private var cameraDevice: CameraDevice? = null
    private lateinit var previewSize: Size
    private var captureSession: CameraCaptureSession? = null
    private lateinit var captureRequestBuilder: CaptureRequest.Builder

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var imageReader: ImageReader


    private val cameraManager: CameraManager by lazy {
        requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutFragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.textureView.surfaceTextureListener = surfaceTextureListener

        binding.btnSwitch.setOnClickListener {
            isFrontCamera = !isFrontCamera
            closeCamera()
            openCamera()
        }

        binding.btnCapture.setOnClickListener {
            takePicture()
        }

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (binding.textureView.isAvailable) openCamera()
        else binding.textureView.surfaceTextureListener = surfaceTextureListener
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun hasPermissions(): Boolean {
        val context = requireContext()
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    private fun getCameraId(): String {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (isFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
            if (!isFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return cameraManager.cameraIdList[0]
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
//        if (!hasPermissions()) {
//            requestPermissions(arrayOf(
//                Manifest.permission.CAMERA,
//                Manifest.permission.RECORD_AUDIO,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE
//            ), REQUEST_CAMERA_PERMISSION)
//            return
//        }

        Log.d("AwadheshSingh : ", "openCamera: ")
        cameraId = getCameraId()
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        previewSize = map!!.getOutputSizes(SurfaceTexture::class.java)[0]

        imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            saveImageToGallery(image)
            image.close()
        }, backgroundHandler)

        configureTransform(binding.textureView.width, binding.textureView.height)
        cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

//    private fun startPreview() {
//        val surfaceTexture = binding.textureView.surfaceTexture!!
//        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
//        val previewSurface = Surface(surfaceTexture)
//
//        captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
//        captureRequestBuilder.addTarget(previewSurface)
//
//        cameraDevice?.createCaptureSession(
//            listOf(previewSurface),
//            object : CameraCaptureSession.StateCallback() {
//                override fun onConfigured(session: CameraCaptureSession) {
//                    captureSession = session
//                    session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
//                }
//
//                override fun onConfigureFailed(session: CameraCaptureSession) {}
//            },
//            backgroundHandler
//        )
//    }

    private fun startPreview() {
        val surfaceTexture = binding.textureView.surfaceTexture!!
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(previewSurface)

        cameraDevice?.createCaptureSession(
            listOf(previewSurface, imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                    // 🔁 Mirror preview if using front camera
                    binding.textureView.scaleX = if (isFrontCamera) -1f else 1f
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            backgroundHandler
        )
    }


    private fun startRecording() {
        try {
            val videoFile = getVideoFile(requireContext())
            if (mediaRecorder == null) mediaRecorder = MediaRecorder()
            val rotation = requireActivity().windowManager.defaultDisplay.rotation
            val orientationHint = when (rotation) {
                Surface.ROTATION_0 -> 90
                Surface.ROTATION_90 -> 0
                Surface.ROTATION_180 -> 270
                Surface.ROTATION_270 -> 180
                else -> 90
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFile.absolutePath)
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoSize(previewSize.width, previewSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOrientationHint(orientationHint)
                prepare()
            }

            val surfaceTexture = binding.textureView.surfaceTexture!!
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(surfaceTexture)
            val recordSurface = mediaRecorder!!.surface

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            captureRequestBuilder.addTarget(previewSurface)
            captureRequestBuilder.addTarget(recordSurface)

            cameraDevice!!.createCaptureSession(
                listOf(previewSurface, recordSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                        mediaRecorder?.start()
                        isRecording = true
                        Toast.makeText(context, "Recording Started", Toast.LENGTH_SHORT).show()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            reset()
        }
        isRecording = false
        mediaRecorder = null
        startPreview()
        Toast.makeText(context, "Recording Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun getVideoFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(dir, "VID_${System.currentTimeMillis()}.mp4")
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        mediaRecorder?.release()
        mediaRecorder = null
    }

//    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
//        val rotation = requireActivity().windowManager.defaultDisplay.rotation
//        val matrix = Matrix()
//        val viewRect = android.graphics.RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
//        val bufferRect = android.graphics.RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
//        val centerX = viewRect.centerX()
//        val centerY = viewRect.centerY()
//        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
//        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
//        matrix.postRotate(90f * (rotation - 2), centerX, centerY)
//        binding.textureView.setTransform(matrix)
//    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = requireActivity().windowManager.defaultDisplay.rotation
        val matrix = Matrix()

        val viewRect = android.graphics.RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = android.graphics.RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)

        // Calculate total rotation
        val rotationDegrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

//        val totalRotation = (sensorOrientation - rotationDegrees + 360) % 360
//        matrix.postRotate(totalRotation.toFloat(), centerX, centerY)

        // Mirror if front camera
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, centerX, centerY)
        }

        binding.textureView.setTransform(matrix)
    }


    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun takePicture() {
        if (cameraDevice == null) return

        val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureBuilder.addTarget(imageReader.surface)
        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        // Orientation
        val rotation = requireActivity().windowManager.defaultDisplay.rotation
        val orientations = SparseIntArray().apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }
        val jpegOrientation = (orientations.get(rotation) + sensorOrientation + 270) % 360
        Log.d("AwadheshSingh : ", "takePicture: $jpegOrientation")

        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)

        captureSession?.stopRepeating()
        captureSession?.abortCaptures()
        captureSession?.capture(captureBuilder.build(), null, backgroundHandler)


        captureSession?.apply {
            stopRepeating()
            abortCaptures()
            capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    startPreview()  // 🔁 Restart preview to allow next capture
                }
            }, backgroundHandler)
        }
    }

    private fun saveImageToGallery(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val resolver = requireContext().contentResolver
        val filename = "IMG_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(bytes)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(requireContext(), "Image saved to gallery", Toast.LENGTH_SHORT).show()
        }
    }

}
