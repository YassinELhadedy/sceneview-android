package io.github.sceneview.sample.armodelviewer

import android.app.Activity
import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.tasks.asDeferred

class MLKitPoserDetector(context: Activity) : PoseDetector(context) {

    val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()
    val detector = PoseDetection.getClient(options)


    override suspend fun analyze(image: Image, imageRotation: Int): Pose {
        // `image` is in YUV (https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#acquireCameraImage()),
        val convertYuv = convertYuv(image)

        // The model performs best on upright images, so rotate it.
        val rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)

        val inputImage = InputImage.fromBitmap(rotatedImage, 0)

        val mlKitPoseDetectedObjects: Pose = detector.process(inputImage).asDeferred().await()
        return mlKitPoseDetectedObjects
        }
    }
