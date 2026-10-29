package dev.koma.reader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.koma.reader.R
import dev.koma.reader.core.Volume
import dev.koma.reader.reader.PageDimensions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.*

private val tileDecodeSlots=Semaphore(2)

private data class Slice(val page:Int,val tile:Int,val size:PageDimensions?,val region:Boolean) {
    val tiled get()=region && size?.isLong==true
    val start get()=if(tiled)size!!.top(tile).toFloat()/size.height else 0f
    val end get()=if(tiled)size!!.bottom(tile).toFloat()/size.height else 1f
    val ratio get()=size?.let {it.width.toFloat()/if(tiled)(it.bottom(tile)-it.top(tile)) else it.height} ?: .7f
}

/** One bounded LazyColumn: long pages are decoded as visible regions, never one giant bitmap. */
@Composable fun StripReader(vm:LibraryViewModel,v:Volume,page:Int,offset:Float,allPages:Boolean,toggle:()->Unit,turn:(Int)->Unit,onEnd:(Boolean)->Unit={},onPosition:(Int,Float)->Unit) {
    val sizes=remember(v.hash) {mutableStateMapOf<Int,PageDimensions>()}
    val metaErrors=remember(v.hash) {mutableStateMapOf<Int,Boolean>()}
    val revisions=remember(v.hash) {mutableStateMapOf<Int,Int>()}
    val initialPage=remember(v.hash,allPages,if(allPages)-1 else page) {page}
    val initialOffset=remember(v.hash,allPages,if(allPages)-1 else page) {offset}
    var ready by remember(v.hash,allPages,if(allPages)-1 else page) {mutableStateOf(false)}
    var lastReported by remember(v.hash) {mutableIntStateOf(page)}
    val slices=buildList {
        val range=if(allPages)0 until v.pages else page..page
        for(p in range) {val size=sizes[p];repeat(if(v.format=="CBZ" && size?.isLong==true)size.tiles else 1){add(Slice(p,it,size,v.format=="CBZ"))}}
    }
    val latestSlices by rememberUpdatedState(slices)
    val latestPosition by rememberUpdatedState(onPosition)
    val latestEnd by rememberUpdatedState(onEnd)
    val list=rememberLazyListState(initialFirstVisibleItemIndex=if(allPages)page else 0)
    LaunchedEffect(list,v.hash,allPages,if(allPages)-1 else page) {
        snapshotFlow {val item=list.layoutInfo.visibleItemsInfo.firstOrNull();Triple(list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset,item?.size ?: 1)}.distinctUntilChanged().collect { (i,pixels,height)->
            if(ready)latestSlices.getOrNull(i)?.let {s->
                val fraction=(s.start+(pixels.toFloat()/height.coerceAtLeast(1)).coerceIn(0f,1f)*(s.end-s.start)).coerceIn(0f,1f)
                lastReported=s.page;latestPosition(s.page,fraction)
            }
        }
    }
    LaunchedEffect(list) {snapshotFlow {ready && !list.canScrollForward}.distinctUntilChanged().collect {latestEnd(it)}}
    LaunchedEffect(page,allPages) {
        if(allPages && ready && page!=lastReported) {
            val i=slices.indexOfFirst {it.page==page}.coerceAtLeast(0)
            lastReported=page;list.scrollToItem(i)
        }
    }
    LaunchedEffect(sizes[initialPage],initialPage,initialOffset,allPages) {
        val size=sizes[initialPage]
        if(!ready && size!=null) {
            val candidates=slices.withIndex().filter {it.value.page==initialPage}
            val item=candidates.lastOrNull {it.value.start<=initialOffset} ?: candidates.firstOrNull()
            if(item!=null) {
                list.scrollToItem(item.index)
                withFrameNanos { };withFrameNanos { }
                val pixels=list.layoutInfo.visibleItemsInfo.firstOrNull {it.index==item.index}?.size ?: 1
                val inside=((initialOffset-item.value.start)/(item.value.end-item.value.start)).coerceIn(0f,1f)
                list.scrollToItem(item.index,(pixels*inside).roundToInt())
            }
            ready=true
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidth=maxWidth
        LazyColumn(state=list,modifier=Modifier.fillMaxSize()) {
            items(slices,key={"${it.page}:${it.tile}"}) {s->
                LaunchedEffect(v.hash,s.page,revisions[s.page]) {
                    if(sizes[s.page]==null)runCatching {withContext(Dispatchers.IO){vm.repo.openSource(v).use {it.dimensions(s.page)}}}.onSuccess {sizes[s.page]=it;metaErrors.remove(s.page)}.onFailure {metaErrors[s.page]=true}
                }
                StripImage(vm,v,s,Modifier.fillMaxWidth().height(viewportWidth/s.ratio),toggle,turn,metaErrors[s.page]==true) {metaErrors.remove(s.page);revisions[s.page]=(revisions[s.page] ?: 0)+1}
            }
        }
    }
}
@Composable private fun StripImage(vm:LibraryViewModel,v:Volume,s:Slice,modifier:Modifier,toggle:()->Unit,turn:(Int)->Unit,metadataError:Boolean,retryMetadata:()->Unit) {
    var bitmap by remember(v.hash,s.page,s.tile,s.tiled) {mutableStateOf<Bitmap?>(null)}
    var error by remember(v.hash,s.page,s.tile,s.tiled) {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    var scale by remember(s.page,s.tile) {mutableFloatStateOf(1f)}
    var pan by remember(s.page,s.tile) {mutableStateOf(Offset.Zero)}
    val width=(androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width).coerceIn(64,1600)
    LaunchedEffect(v.hash,v.sourceUri,s.page,s.tile,s.size,width,retry) {
        error=false
        if(s.size==null)return@LaunchedEffect
        try {
            bitmap=tileDecodeSlots.withPermit {
                withContext(Dispatchers.IO) {
                    if(s.tiled)vm.repo.openSource(v).use {it.renderTile(s.page,width,s.tile)}
                    else vm.repo.bitmap(v,s.page,width)
                }
            }
        } catch(e:CancellationException) {throw e}
        catch(e:Exception) {error=true}
    }
    val currentScale by rememberUpdatedState(scale)
    Box(modifier.clipToBounds()
        .pointerInput(s.page,s.tile) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false)
                var dx=0f;var dy=0f;var transformed=false
                do {
                    val event=awaitPointerEvent();val delta=event.calculatePan()
                    if(event.changes.count {it.pressed}>1 || currentScale>1f) {
                        transformed=true;scale=(scale*event.calculateZoom()).coerceIn(1f,4f)
                        pan=if(scale<=1f)Offset.Zero else Offset((pan.x+delta.x).coerceIn(-size.width*(scale-1)/2,size.width*(scale-1)/2),(pan.y+delta.y).coerceIn(-size.height*(scale-1)/2,size.height*(scale-1)/2))
                        event.changes.forEach {it.consume()}
                    } else {dx+=delta.x;dy+=delta.y}
                } while(event.changes.any {it.pressed})
                if(!transformed && abs(dx)>80 && abs(dx)>abs(dy)*1.5f)turn(if(dx<0)1 else -1)
            }
        }
        .pointerInput(s.page,s.tile) {detectTapGestures(onTap={toggle()},onDoubleTap={scale=if(scale>1f)1f else 2.5f;pan=Offset.Zero})},contentAlignment=Alignment.Center) {
        bitmap?.let {Image(it.asImageBitmap(),stringResource(R.string.tile_description,s.page+1,s.tile+1),Modifier.fillMaxSize().graphicsLayer(scaleX=scale,scaleY=scale,translationX=pan.x,translationY=pan.y),contentScale=ContentScale.FillBounds)}
        if(bitmap==null && !error && !metadataError)CircularProgressIndicator(color=Accent)
        if(error || metadataError)Column(horizontalAlignment=Alignment.CenterHorizontally) {Text(stringResource(R.string.render_error));Text(stringResource(R.string.source_missing),color=Muted);TextButton({if(s.size==null)retryMetadata() else retry++}){Text(stringResource(R.string.retry))}}
    }
}
