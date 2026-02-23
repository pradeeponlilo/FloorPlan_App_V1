package com.floorplan.tool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.floorplan.tool.ui.FloorPlanScreen
import com.floorplan.tool.ui.theme.FloorPlanToolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloorPlanToolTheme {
                FloorPlanScreen()
            }
        }
    }
}
