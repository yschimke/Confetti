package dev.johnoreilly.confetti.wear.tile

import android.app.Presentation
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.roundToIntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.DpProp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import com.google.android.horologist.tiles.images.toImageResource
import com.google.android.horologist.tiles.render.SingleTileLayoutRenderer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GraphicsLayerTileRenderer(context: Context) : SingleTileLayoutRenderer<Unit, ImageBitmap>(context) {
    override fun renderTile(
        state: Unit,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId("graphicsLayer")
                    .setWidth(DpProp.Builder(200f).build())
                    .setHeight(DpProp.Builder(200f).build())
                    .build()
            )
            .build()
    }

    override fun ResourceBuilders.Resources.Builder.produceRequestedResources(
        resourceState: ImageBitmap,
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        resourceIds: List<String>
    ) {
        addIdToImageMapping(
            "graphicsLayer",
            resourceState.asAndroidBitmap().toImageResource()
        )
    }
}


/** Use virtualDisplay to capture composables into a virtual (i.e. invisible) display. */
suspend fun <T> useVirtualDisplay(context: Context, callback: suspend (display: Display) -> T): T {
    val texture = SurfaceTexture(false)
    val surface = Surface(texture)
    // Size of virtual display doesn't matter, because images are captured from compose, not the display surface.
    val virtualDisplay = context.getDisplayManager().createVirtualDisplay(
        "virtualDisplay", 1, 1, 72, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
    )

    val result = callback(virtualDisplay.display)

    virtualDisplay.release()
    surface.release()
    texture.release()

    return result
}


/** Captures composable content, by default using a hidden window on the default display.
 *
 *  Be sure to invoke capture() within the composable content (e.g. in a LaunchedEffect) to perform the capture.
 *  This gives some level of control over when the capture occurs, so it's possible to wait for async resources */
suspend fun captureComposable(
    context: Context,
    size: DpSize,
    density: Density = Density(context),
    display: Display,
    content: @Composable () -> Unit,
): ImageBitmap {
    println(1)
    val presentation = Presentation(context.applicationContext, display).apply {
        window?.decorView?.let { view ->
            view.setViewTreeLifecycleOwner(ProcessLifecycleOwner.get())
            view.setViewTreeSavedStateRegistryOwner(EmptySavedStateRegistryOwner.shared)
        }
    }
    println(2)

    val composeView = ComposeView(context).apply {
        val intSize = with(density) { size.toSize().roundToIntSize() }
        println("View size " + intSize)
        require(intSize.width > 0 && intSize.height > 0) { "pixel size must not have zero dimension" }

        layoutParams = ViewGroup.LayoutParams(intSize.width, intSize.height)
    }
    println(3)

    presentation.setContentView(composeView, composeView.layoutParams)
    println(4)
    presentation.show()
    println(5)

    val future = CompletableDeferred<ImageBitmap>()

    composeView.setContent {
        val graphicsLayer = rememberGraphicsLayer()
        val coroutineScope = rememberCoroutineScope()
        val configuration = LocalConfiguration.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    println("Compose size " + configuration.screenWidthDp)
                    println("drawWithContents")
                    graphicsLayer.record {
                        println("record")
                        this@drawWithContent.drawContent()
                    }
                    println("Size: ${graphicsLayer.size}")
                    println("after record")
                    coroutineScope.launch(Dispatchers.IO) {
                        println("before toImageBitmap")
                        future.complete(graphicsLayer.toImageBitmap())
                        println("after toImageBitmap")
                    }
                },
        ) {
            content()
        }
        SideEffect {
            println("SideEffect")
        }
    }
    println(6)

    val await = future.await()
    println(7)
    presentation.dismiss()
    return await
}

private fun Context.getDisplayManager(): DisplayManager =
    getSystemService(Context.DISPLAY_SERVICE) as DisplayManager


private class EmptySavedStateRegistryOwner : SavedStateRegistryOwner {
    private val controller = SavedStateRegistryController.create(this).apply {
        performRestore(null)
    }

    private val lifecycleOwner: LifecycleOwner? = ProcessLifecycleOwner.get()

    override val lifecycle: Lifecycle
        get() {
            val value = object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    lifecycleOwner?.lifecycle?.addObserver(observer)
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    lifecycleOwner?.lifecycle?.removeObserver(observer)
                }

                override val currentState = State.INITIALIZED
            }
            return value
        }

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    companion object {
        val shared = EmptySavedStateRegistryOwner()
    }
}
