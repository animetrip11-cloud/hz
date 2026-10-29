package dev.koma.reader

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import dev.koma.reader.ui.*

class MainActivity:ComponentActivity() {
    var incoming by mutableStateOf<android.net.Uri?>(null)
    var volumeAction:((Int)->Boolean)?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        installSplashScreen(); super.onCreate(savedInstanceState); enableEdgeToEdge()
        if(savedInstanceState==null) handle(intent)
        setContent { KomaTheme { KomaApp(this,incoming,consume={ incoming=null }) } }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    @Suppress("DEPRECATION") private fun handle(i:Intent?) { incoming=if(i?.action==Intent.ACTION_VIEW) i.data else if(i?.action==Intent.ACTION_SEND) i.getParcelableExtra(Intent.EXTRA_STREAM) else null }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        if(keyCode in listOf(KeyEvent.KEYCODE_VOLUME_DOWN,KeyEvent.KEYCODE_VOLUME_UP)) {
            volumeAction?.let { return it(if(keyCode==KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1) }
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onKeyUp(keyCode:Int,event:KeyEvent):Boolean {
        if(volumeAction!=null && keyCode in listOf(KeyEvent.KEYCODE_VOLUME_DOWN,KeyEvent.KEYCODE_VOLUME_UP))return true
        return super.onKeyUp(keyCode,event)
    }
}
