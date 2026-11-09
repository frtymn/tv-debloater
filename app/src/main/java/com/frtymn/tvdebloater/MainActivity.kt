package com.frtymn.tvdebloater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.frtymn.tvdebloater.ui.App
import com.frtymn.tvdebloater.ui.MainViewModel
import com.frtymn.tvdebloater.ui.TvDebloaterTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvDebloaterTheme { App(vm) } }
    }
}
