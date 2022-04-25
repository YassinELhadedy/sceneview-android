package io.github.sceneview.sample.armodelviewer

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.mlkit.vision.pose.Pose
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.EditableTransform
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position
import io.github.sceneview.utils.doOnApplyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFragment : Fragment(R.layout.fragment_main) {

    lateinit var sceneView: ArSceneView
    lateinit var loadingView: View
    lateinit var actionButton: ExtendedFloatingActionButton

    lateinit var modelNode: ArModelNode

    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    var objectResults: Pose? = null


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        sceneView = view.findViewById(R.id.sceneView)
        loadingView = view.findViewById(R.id.loadingView)
        actionButton = view.findViewById<ExtendedFloatingActionButton>(R.id.actionButton).apply {
            // Add system bar margins
            val bottomMargin = (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            doOnApplyWindowInsets { systemBarsInsets ->
                (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    systemBarsInsets.bottom + bottomMargin
            }
            setOnClickListener { actionButtonClicked() }
        }

        isLoading = true
        modelNode = ArModelNode(placementMode = PlacementMode.BEST_AVAILABLE).apply {
            loadModelAsync(
                context = requireContext(),
                glbFileLocation = "models/spiderbot.glb",
                lifecycle = lifecycle,
                autoAnimate = true,
                autoScale = false,
                // Place the model origin at the bottom center
                centerOrigin = Position(y = -1.0f)
            ) {
                isLoading = false
            }
            onTrackingChanged = { _, isTracking, _ ->
                actionButton.isGone = !isTracking
            }
            editableTransforms = EditableTransform.ALL
        }
        sceneView.addChild(modelNode)
        sceneView.gestureDetector.onTouchNode(modelNode)

        sceneView.apply {
            onArFrame = {
                val cameraImage = try { it.frame.tryAcquireCameraImage() } catch (e: Exception) { null }

                if (cameraImage != null) {
                    // Call our ML model on an IO thread.
                    lifecycleScope.launch(Dispatchers.IO) {
                        objectResults =  MLKitPoserDetector(activity).analyze(cameraImage, 0)
                        cameraImage.close()
                    }
                }

                val objects = objectResults
                if (objects != null) {
                    objectResults = null


//                    val anchors = objects.allPoseLandmarks.mapNotNull { landMark ->
//                        val (atX, atY) = landMark.position.x to landMark.position.y
//                        Log.i("#YASDEBUG", "Created Pose ${atX} , ${atY}from hit test")
//                        val anchor = createAnchor(atX, atY, it.frame) ?: return@mapNotNull null
//                        Log.i("#YASDEBUG", "Created type ${landMark?.landmarkType.toString()}from hit test")
//                        Log.i("#YASDEBUG", "Created anchor ${anchor?.pose} from hit test")
//                    }

                    val anchors = listOf(objects.getPoseLandmark(0)).mapNotNull { landMark ->
                        val (atX, atY) = landMark?.position?.x to landMark?.position?.y
                        Log.i("#YASDEBUG", "Created Pose ${atX} , ${atY}from hit test")
                        val anchor = atX?.let { it1 -> atY?.let { it2 ->
                            createAnchor(it1,
                                it2, it.frame)
                        } } ?: return@mapNotNull null
                        Log.i("#YASDEBUG", "Created type ${landMark?.landmarkType.toString()}from hit test")
                        Log.i("#YASDEBUG", "Created anchor ${anchor?.pose} from hit test")
                    }
                }

            }
        }
    }
    /**
     * Utility method for [Frame.acquireCameraImage] that maps [NotYetAvailableException] to `null`.
     */
    fun Frame.tryAcquireCameraImage() = try {
        acquireCameraImage()
    } catch (e: NotYetAvailableException) {
        Log.i("ygdsadsagdsa",e.message ?:" sdss")

        null
    } catch (e: Throwable) {
        Log.i("ygdsadsagdsa",e.message ?:" sd")
        throw e
    }


    /**
     * Temporary arrays to prevent allocations in [createAnchor].
     */
    private val convertFloats = FloatArray(4)
    private val convertFloatsOut = FloatArray(4)

    /** Create an anchor using (x, y) coordinates in the [Coordinates2d.IMAGE_PIXELS] coordinate space. */
    fun createAnchor(xImage: Float, yImage: Float, frame: Frame): Anchor? {
        // IMAGE_PIXELS -> VIEW
        convertFloats[0] = xImage
        convertFloats[1] = yImage
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        Log.i("convertFloatsOut","x=  ${convertFloatsOut[0]}  y= ${convertFloatsOut[1]}")
        // Conduct a hit test using the VIEW coordinates
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val result = hits.getOrNull(0) ?: return null
        return result.trackable.createAnchor(result.hitPose)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        item.isChecked = !item.isChecked
        modelNode.detachAnchor()
        modelNode.placementMode = when (item.itemId) {
            R.id.menuPlanePlacement -> PlacementMode.PLANE_HORIZONTAL_AND_VERTICAL
            R.id.menuInstantPlacement -> PlacementMode.INSTANT
            R.id.menuDepthPlacement -> PlacementMode.DEPTH
            R.id.menuBestPlacement -> PlacementMode.BEST_AVAILABLE
            else -> PlacementMode.DISABLED
        }
        return true
    }

    fun actionButtonClicked() {
        if (!modelNode.isAnchored && modelNode.anchor()) {
            actionButton.text = getString(R.string.move_object)
            actionButton.setIconResource(R.drawable.ic_target)
            sceneView.planeRenderer.isVisible = false
        } else {
            modelNode.anchor = null
            actionButton.text = getString(R.string.place_object)
            actionButton.setIconResource(R.drawable.ic_anchor)
            sceneView.planeRenderer.isVisible = true
        }
    }
}