package dev.koma.reader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.koma.reader.R
import dev.koma.reader.core.Volume
import dev.koma.reader.data.Repository
import dev.koma.reader.reader.PageSource
import kotlinx.coroutines.*
import java.io.File

@Composable fun Cover(repo:Repository,volume:Volume?,modifier:Modifier=Modifier,page:Int=0) {
    var bitmap by remember(volume?.hash,page) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(volume?.hash,page,volume?.available) {
        bitmap=null
        if(volume?.available==true) bitmap=withContext(Dispatchers.IO) {
            runCatching {
                val cache=File(repo.context.cacheDir,"${volume.hash}-$page.jpg")
                if(cache.exists()) android.graphics.BitmapFactory.decodeFile(cache.path) else {
                    val b=repo.openSource(volume).use { it.render(page,220) }
                    // Keep the disk cache bounded; only recreatable thumbnails live here.
                    if((repo.context.cacheDir.listFiles()?.sumOf { it.length() } ?: 0)>32L*1024*1024) repo.context.cacheDir.listFiles()?.forEach { it.delete() }
                    cache.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG,82,it) }; b
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(Elevated),contentAlignment=Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(),stringResource(R.string.cover),Modifier.fillMaxSize(),contentScale=ContentScale.Crop) } ?: Logo(Modifier.size(40.dp))
    }
}
