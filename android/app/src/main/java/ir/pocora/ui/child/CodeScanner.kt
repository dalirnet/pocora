package ir.pocora.ui.child

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ir.pocora.debug.FileLogger
import kotlin.math.abs

// Runs the back camera into a preview and reads QR codes from the same frames.
// onText runs on the main thread, once for every frame that shows a code.
class CodeScanner(
    context: Context,
    private val onText: (String) -> Unit,
) {
    companion object {
        private const val TAG = "CodeScanner"
        private const val WANTED_WIDTH = 1280
        private const val WANTED_HEIGHT = 720
        private const val FALLBACK_SENSOR_ORIENTATION = 90
        private const val MAXIMUM_IMAGES = 2
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId: String? = chooseCamera()
    private val sensorOrientation: Int =
        cameraId?.let { cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.SENSOR_ORIENTATION) }
            ?: FALLBACK_SENSOR_ORIENTATION

    // The frame size, as the sensor sees it: wider than tall.
    val frameSize: Size = chooseSize()

    private val scanner =
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    private val thread = HandlerThread("pocora-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null

    @Volatile
    private var reading = false

    // The caller has already been granted the camera permission.
    @SuppressLint("MissingPermission")
    fun start(texture: SurfaceTexture) {
        val id = cameraId ?: return
        texture.setDefaultBufferSize(frameSize.width, frameSize.height)
        val preview = Surface(texture)
        val frames = ImageReader.newInstance(frameSize.width, frameSize.height, ImageFormat.YUV_420_888, MAXIMUM_IMAGES)
        frames.setOnImageAvailableListener(::read, handler)
        reader = frames
        val callback =
            object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    openSession(device, preview, frames.surface)
                }

                override fun onDisconnected(device: CameraDevice) = device.close()

                override fun onError(
                    device: CameraDevice,
                    error: Int,
                ) {
                    FileLogger.w(TAG, "Camera error $error")
                    device.close()
                }
            }
        try {
            cameraManager.openCamera(id, callback, handler)
        } catch (error: CameraAccessException) {
            FileLogger.w(TAG, "Camera did not open", error)
        }
    }

    fun stop() {
        handler.post {
            session?.close()
            camera?.close()
            reader?.close()
            scanner.close()
            thread.quitSafely()
        }
    }

    private fun openSession(
        device: CameraDevice,
        preview: Surface,
        frames: Surface,
    ) {
        val callback =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    session = configured
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        request.addTarget(preview)
                        request.addTarget(frames)
                        request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        configured.setRepeatingRequest(request.build(), null, handler)
                    } catch (error: CameraAccessException) {
                        FileLogger.w(TAG, "Preview did not start", error)
                    } catch (error: IllegalStateException) {
                        FileLogger.w(TAG, "Preview did not start", error)
                    }
                }

                override fun onConfigureFailed(failed: CameraCaptureSession) =
                    FileLogger.w(TAG, "Camera session failed")
            }
        try {
            val outputs = listOf(OutputConfiguration(preview), OutputConfiguration(frames))
            device.createCaptureSession(
                SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, { handler.post(it) }, callback),
            )
        } catch (error: CameraAccessException) {
            FileLogger.w(TAG, "Camera session failed", error)
        }
    }

    // One frame at a time. Frames that arrive while one is being read are dropped.
    private fun read(frames: ImageReader) {
        val image =
            try {
                frames.acquireLatestImage()
            } catch (_: IllegalStateException) {
                null
            } ?: return
        if (reading) {
            image.close()
            return
        }
        reading = true
        scanner
            .process(InputImage.fromMediaImage(image, sensorOrientation))
            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onText) }
            .addOnCompleteListener {
                image.close()
                reading = false
            }
    }

    private fun chooseCamera(): String? {
        val ids =
            try {
                cameraManager.cameraIdList
            } catch (_: CameraAccessException) {
                emptyArray()
            }
        return ids.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.firstOrNull()
    }

    // The size the camera offers that is nearest to what a QR code needs. Bigger only costs time.
    private fun chooseSize(): Size {
        val wanted = Size(WANTED_WIDTH, WANTED_HEIGHT)
        val sizes =
            cameraId
                ?.let {
                    cameraManager
                        .getCameraCharacteristics(
                            it,
                        ).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                }?.getOutputSizes(ImageFormat.YUV_420_888)
                ?: return wanted
        return sizes.minByOrNull { abs(it.width * it.height - WANTED_WIDTH * WANTED_HEIGHT) } ?: wanted
    }
}
