package dev.koma.reader.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.koma.reader.MainActivity
import dev.koma.reader.R
import dev.koma.reader.core.*
import dev.koma.reader.data.codec
import dev.koma.reader.reader.PageSource
import dev.koma.reader.reader.PageBitmaps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.*

@Composable fun ReaderScreen(activity:MainActivity,vm:LibraryViewModel,hash:String,start:Int,back:()->Unit,next:(Volume)->Unit) {
    val volumes by vm.volumes.collectAsStateWithLifecycle();val works by vm.works.collectAsStateWithLifecycle();val marks by vm.bookmarks.collectAsStateWithLifecycle();val prefs by vm.prefs.collectAsStateWithLifecycle()
    val volume=volumes.find { it.hash==hash } ?: return
    val work=works.find { it.id==volume.workId }
    val override=work?.overrides?.let { runCatching { codec.decodeFromString<Overrides>(it) }.getOrNull() } ?: Overrides()
    val settings=override.resolve(prefs.reading)
    var page by rememberSaveable(hash) { mutableIntStateOf((if(start>=0)start else volume.page).coerceIn(0,volume.pages-1)) }
    var controls by rememberSaveable(hash) { mutableStateOf(true) };var showSettings by remember { mutableStateOf(false) };var thumbnails by remember { mutableStateOf(false) };var preview by remember { mutableStateOf<Float?>(null) };var mode by remember { mutableStateOf(false) };var finish by remember { mutableStateOf(false) }
    var offset by rememberSaveable(hash) { mutableFloatStateOf(volume.offset) }
    val scope=rememberCoroutineScope()
    val change:(Int)->Unit={new->page=new.coerceIn(0,volume.pages-1);offset=0f;vm.position(hash,page)}
    val latestPage by rememberUpdatedState(page);val latestOffset by rememberUpdatedState(offset)
    LaunchedEffect(hash) { vm.repo.open(hash); if(start>=0) vm.position(hash,page) }
    DisposableEffect(settings,hash) {
        val attrs=activity.window.attributes;attrs.screenBrightness=settings.brightness;activity.window.attributes=attrs
        if(settings.keepAwake)activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.volumeAction=if(settings.volumeKeys) { delta -> change(latestPage+delta); true } else null
        onDispose { activity.volumeAction=null;activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);val a=activity.window.attributes;a.screenBrightness=-1f;activity.window.attributes=a }
    }
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(hash,lifecycle) {
        val observer=LifecycleEventObserver { _,e->if(e==Lifecycle.Event.ON_STOP)vm.position(hash,latestPage,latestOffset) }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer);vm.position(hash,latestPage,latestOffset) }
    }
    androidx.activity.compose.BackHandler { vm.position(hash,page,offset);back() }
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if(controls) BackHeader(work?.title ?: volume.title,{vm.position(hash,page,offset);back()}) {
            IconAction(if(marks.any { it.hash==hash && it.page==page })KomaIcons.Bookmark else KomaIcons.BookmarkBorder,stringResource(R.string.bookmark_action)){vm.mark(hash,page)}
            IconAction(KomaIcons.Tune,stringResource(R.string.reader_settings)){showSettings=true}
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            var metadataFailed by remember(hash,page) {mutableStateOf(false)}
            var dimensions by remember(hash,page) {mutableStateOf<dev.koma.reader.reader.PageDimensions?>(null)}
            LaunchedEffect(hash,page,volume.sourceUri) {
                val result=withContext(Dispatchers.IO) {runCatching {vm.repo.openSource(volume).use {it.dimensions(page)}}}
                dimensions=result.getOrNull();metadataFailed=result.isFailure
            }
            var stripAtEnd by remember(hash,page,settings.mode) {mutableStateOf(false)}
            val autoStrip=volume.format=="CBZ" && dimensions?.isLong==true && (settings.autoLongPages || settings.fitWidth)
            if(settings.mode=="vertical" || autoStrip) {
                StripReader(vm,volume,page,offset,settings.mode=="vertical",{controls=!controls},{delta->if(settings.mode!="vertical")change(page+if(settings.mode=="rtl")-delta else delta)},onEnd={stripAtEnd=it}) {p,o->
                    page=p;offset=o;vm.position(hash,p,o)
                }
            } else if(volume.format=="CBZ" && settings.autoLongPages && dimensions==null && !metadataFailed) {
                CircularProgressIndicator(Modifier.align(Alignment.Center),color=Accent)
            } else if(settings.fitWidth) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PageImage(vm,volume,page,true,Modifier.fillMaxWidth(),{controls=!controls},{delta->change(page+if(settings.mode=="rtl")-delta else delta)})
                }
            } else PageImage(vm,volume,page,false,Modifier.fillMaxSize(),{controls=!controls},{delta->change(page+if(settings.mode=="rtl")-delta else delta)})
            if(controls && ((settings.mode=="vertical" && stripAtEnd) || (page==volume.pages-1 && settings.mode!="vertical" && (!autoStrip || stripAtEnd)))) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp),shape=RoundedCornerShape(12.dp),color=Surface) { Column(Modifier.padding(8.dp)) {
                TextButton({vm.complete(volume.hash);finish=true}){Text(stringResource(R.string.finish_volume))}
                val following=volumes.filter { it.workId==volume.workId && it.order>volume.order }.minByOrNull { it.order }
                if(following!=null) TextButton({next(following)}){Text(stringResource(R.string.next_volume))}
            } }
        }
        if(controls) Surface(color=Surface) {
            Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {
                Text(stringResource(R.string.position,(preview?.toInt() ?: page)+1,volume.pages),Modifier.align(Alignment.CenterHorizontally))
                preview?.let { n-> Row(Modifier.align(Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) { Cover(vm.repo,volume,Modifier.size(48.dp,64.dp),n.toInt());Text(stringResource(R.string.page_preview,n.toInt()+1),Modifier.padding(8.dp)) } }
                Slider(value=preview ?: page.toFloat(),onValueChange={preview=it},onValueChangeFinished={preview?.let { change(it.roundToInt()) };preview=null},valueRange=0f..max(1,volume.pages-1).toFloat(),enabled=volume.pages>1)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    TextButton({thumbnails=!thumbnails}){Icon(KomaIcons.CollectionsBookmark,null);Text(stringResource(R.string.pages))}
                    TextButton({mode=true}){Icon(KomaIcons.ViewAgenda,null);Text(stringResource(R.string.mode))}
                    IconAction(KomaIcons.Tune,stringResource(R.string.settings)){showSettings=true}
                }
                if(thumbnails) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(volume.pages){i->Column(Modifier.clickable { change(i);thumbnails=false }.border(if(i==page)2.dp else 0.dp,if(i==page)Accent else Surface)) { Cover(vm.repo,volume,Modifier.size(56.dp,80.dp),i);Text("${i+1}") } } }
            }
        }
    }
    if(mode) AlertDialog(
        onDismissRequest={mode=false},
        title={Text(stringResource(R.string.mode))},
        text={
            Column {
                listOf("rtl","ltr","vertical").forEach { m ->
                    TextButton(onClick={
                        work?.let { vm.work(it.copy(overrides=codec.encodeToString(override.copy(mode=m)))) }
                        mode=false
                    }) { Text(stringResource(modeRes(m))) }
                }
            }
        },
        confirmButton={TextButton({mode=false}){Text(stringResource(R.string.close))}}
    )
    if(showSettings) ReadingSettingsDialog(vm,work){showSettings=false}
    if(finish && work!=null) {
        if(volumes.filter { it.workId==work.id && it.hash!=hash }.all { it.completed }) Confirm(stringResource(R.string.completed),stringResource(R.string.finish_work),{finish=false}){vm.work(work.copy(status="done"))}
        else finish=false
    }
}
@Composable private fun PageImage(vm:LibraryViewModel,v:Volume,page:Int,fitWidth:Boolean,modifier:Modifier,toggle:()->Unit,turn:(Int)->Unit,onLoaded:()->Unit={}) {
    var bitmap by remember(v.hash,page) { mutableStateOf<Bitmap?>(null) };var error by remember(v.hash,page) { mutableStateOf(false) };var retry by remember { mutableIntStateOf(0) };var scale by remember(v.hash,page) { mutableFloatStateOf(1f) };var pan by remember(v.hash,page) { mutableStateOf(Offset.Zero) };var width by remember { mutableIntStateOf(1080) }
    val renderWidth=(width*if(scale>1.5f)2 else 1).coerceIn(64,2400)
    LaunchedEffect(v.hash,v.sourceUri,page,renderWidth,retry) {
        error=false
        try { val b=withContext(Dispatchers.IO) { vm.repo.bitmap(v,page,renderWidth) };bitmap=b;onLoaded()
            if(page+1<v.pages && !fitWidth) withContext(Dispatchers.IO) {
                ensureActive()
                runCatching { vm.repo.bitmap(v,page+1,renderWidth) }
            }
        }
        catch(e:CancellationException){throw e} catch(e:Exception){error=true}
    }
    val currentScale by rememberUpdatedState(scale)
    val desc=stringResource(R.string.page_preview,page+1)
    val ratio=bitmap?.let { it.width.toFloat()/it.height } ?: .7f
    Box(modifier.then(if(fitWidth)Modifier.aspectRatio(ratio) else Modifier).clipToBounds().onSizeChanged { width=it.width }
        .pointerInput(v.hash,page) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false)
                var totalX=0f
                var transformed=false
                do {
                    val event=awaitPointerEvent()
                    val zoom=event.calculateZoom()
                    val delta=event.calculatePan()
                    val multiple=event.changes.count { it.pressed }>1
                    if(multiple || currentScale>1f) {
                        transformed=true
                        scale=(scale*zoom).coerceIn(1f,4f)
                        pan=if(scale<=1f) Offset.Zero else Offset((pan.x+delta.x).coerceIn(-size.width*(scale-1)/2,size.width*(scale-1)/2),(pan.y+delta.y).coerceIn(-size.height*(scale-1)/2,size.height*(scale-1)/2))
                        event.changes.forEach { it.consume() }
                    } else totalX+=delta.x
                } while(event.changes.any { it.pressed })
                if(!transformed && abs(totalX)>80)turn(if(totalX<0)1 else -1)
            }
        }
        .pointerInput(v.hash,page) { detectTapGestures(onDoubleTap={scale=if(scale>1f)1f else 2.5f;pan=Offset.Zero},onTap={p->if(currentScale<=1f) { when { p.x<size.width*.24f->turn(-1);p.x>size.width*.76f->turn(1);else->toggle() } } else toggle()}) }
,contentAlignment=Alignment.Center) {
        bitmap?.let { b->Image(b.asImageBitmap(),desc,Modifier.fillMaxSize().graphicsLayer(scaleX=scale,scaleY=scale,translationX=pan.x,translationY=pan.y),contentScale=ContentScale.Fit) }
        if(bitmap==null && !error) CircularProgressIndicator(color=Accent)
        if(error) Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(stringResource(R.string.render_error));Text(stringResource(R.string.source_missing),color=Muted);TextButton({retry++}){Text(stringResource(R.string.retry))} }
    }
}
