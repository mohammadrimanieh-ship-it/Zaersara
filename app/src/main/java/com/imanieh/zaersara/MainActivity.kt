package com.imanieh.zaersara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imanieh.zaersara.ui.App
import com.imanieh.zaersara.ui.AppViewModel

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{val vm:AppViewModel=viewModel();App(vm)}}}
