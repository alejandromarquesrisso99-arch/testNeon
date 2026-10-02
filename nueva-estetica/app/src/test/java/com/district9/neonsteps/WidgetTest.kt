package com.district9.neonsteps

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.widget.StepsWidget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class WidgetTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        StepRepository.resetForTests()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    @After
    fun tearDown() = StepRepository.resetForTests()

    @Test
    fun widgetShowsTodaysSteps() {
        StepRepository.get(app).addSteps(6_482)
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(StepsWidget::class.java, R.layout.widget_steps)
        StepsWidget.refresh(app, force = true)
        val view = shadowOf(manager).getViewFor(id)
        assertEquals("6.482", view.findViewById<TextView>(R.id.widget_steps).text.toString())
        assertEquals("81%", view.findViewById<TextView>(R.id.widget_percent).text.toString())
        capture(view, "widget.png")
    }

    @Test
    fun widgetCelebratesTheGoal() {
        StepRepository.get(app).addSteps(8_400)
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(StepsWidget::class.java, R.layout.widget_steps)
        StepsWidget.refresh(app, force = true)
        capture(shadowOf(manager).getViewFor(id), "widget_goal.png")
    }

    /** Lays the widget out at its default 4×2 size (250 × 110 dp) and saves it. */
    private fun capture(view: View, name: String) {
        val d = app.resources.displayMetrics.density
        val w = (250 * d).toInt()
        val h = (110 * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
