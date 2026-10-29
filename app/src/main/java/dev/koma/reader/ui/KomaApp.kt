package dev.koma.reader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import dev.koma.reader.MainActivity
import dev.koma.reader.R
import dev.koma.reader.core.*
import dev.koma.reader.data.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun sizeLabel(bytes:Long):String = android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current,bytes)
fun statusRes(s:String)=when(s) { "reading"->R.string.reading_status; "done"->R.string.done_status; "later"->R.string.later_status; else->R.string.new_status }
fun modeRes(s:String)=when(s) { "ltr"->R.string.ltr; "vertical"->R.string.vertical; else->R.string.rtl }
@Composable fun IconAction(icon:ImageVector,label:String,onClick:()->Unit) { IconButton(onClick) { Icon(icon,label) } }
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun KomaApp(activity:MainActivity,incoming:Uri?,consume:()->Unit,vm:LibraryViewModel=viewModel()) {
    val nav=rememberNavController(); val entry by nav.currentBackStackEntryAsState(); val route=entry?.destination?.route
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar=remember { SnackbarHostState() }; val scope=rememberCoroutineScope()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if(uris.isNotEmpty()) { vm.prepare(uris,target,append=route=="import"); if(route!="import")nav.navigate("import") } }
    val pick:(String?)->Unit={ id->target=id; picker.launch(arrayOf("application/pdf","application/zip","application/x-cbz","application/vnd.comicbook+zip","application/octet-stream")) }
    LaunchedEffect(incoming) { incoming?.let { vm.prepare(listOf(it),null); nav.navigate("import"); consume() } }
    LaunchedEffect(Unit) { vm.message.collect { key -> snackbar.showSnackbar(activity.getString(messageResource(key))) } }
    val open:(Volume,Int?)->Unit={v,p-> if(v.available && (v.sourceUri!=null || vm.repo.file(v.hash).exists())) nav.navigate("reader/${v.hash}/${p ?: -1}") else pick(v.workId) }
    Scaffold(containerColor=Background,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(route=="home") NavigationBar(containerColor=Surface,tonalElevation=0.dp) {
            listOf(R.string.library to KomaIcons.LocalLibrary,R.string.bookmarks to KomaIcons.BookmarkBorder,R.string.history to KomaIcons.History,R.string.settings to KomaIcons.Tune).forEachIndexed { i,(label,icon)->
                NavigationBarItem(tab==i,{tab=i},icon={Icon(icon,stringResource(label))},label={Text(stringResource(label),fontSize=11.sp,maxLines=1)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Accent,selectedTextColor=Accent,indicatorColor=Elevated))
            }
        }
    }) { padding ->
        NavHost(nav,startDestination="home",modifier=Modifier.padding(padding)) {
            composable("home") {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Logo(Modifier.size(40.dp)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(stringResource(R.string.app_name),fontSize=24.sp,fontWeight=FontWeight.Black); Text(stringResource(R.string.personal),fontSize=10.sp,color=Muted) }
                        IconAction(KomaIcons.Add,stringResource(R.string.add)) { pick(null) }
                    }
                    when(tab) {
                        0->LibraryScreen(vm,{nav.navigate("work/$it")},{open(it,null)},{pick(null)})
                        1->BookmarksScreen(vm,open,snackbar)
                        2->HistoryScreen(vm) { open(it,null) }
                        3->SettingsScreen(vm) {pick(null)}
                    }
                }
            }
            composable("work/{id}") { e -> WorkScreen(vm,e.arguments?.getString("id") ?: "",{nav.popBackStack()},{pick(it)},{open(it,null)}) }
            composable("import") { ImportScreen(vm,{vm.cancelImport();nav.popBackStack()},{vm.commit { nav.popBackStack() }},{open(it,null)},{pick(target)}) }
            composable("reader/{hash}/{page}") { e-> ReaderScreen(activity,vm,e.arguments!!.getString("hash")!!,e.arguments!!.getString("page")!!.toInt(),{nav.popBackStack()},{v-> nav.popBackStack(); open(v,null)}) }
        }
    }
}
@Composable fun ScreenTitle(title:String,subtitle:String) { Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold); Text(subtitle,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=16.dp)) }
@Composable fun BackHeader(title:String,back:()->Unit,extra:@Composable RowScope.()->Unit={}) { Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) { IconAction(KomaIcons.ArrowBack,stringResource(R.string.back),back); Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f)); extra() } }
@Composable fun LibraryScreen(vm:LibraryViewModel,work:(String)->Unit,open:(Volume)->Unit,add:()->Unit) {
    val works by vm.works.collectAsStateWithLifecycle(); val volumes by vm.volumes.collectAsStateWithLifecycle(); val history by vm.history.collectAsStateWithLifecycle(); val p by vm.prefs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }; var filters by rememberSaveable { mutableStateOf(false) }
    val state=rememberLazyListState()
    val config=androidx.compose.ui.platform.LocalConfiguration.current
    val columns=if(config.screenWidthDp>=440 && androidx.compose.ui.platform.LocalDensity.current.fontScale<1.3f)3 else 2
    val filtered=works.filter { w->
        val vs=volumes.filter { it.workId==w.id }
        (w.title.contains(query,true) || vs.any { it.fileName.contains(query,true) }) && (p.status=="all" || p.status==w.status) && (p.format=="all" || vs.any { it.format==p.format })
    }.let { list-> when(p.sort) { "title"->list.sortedWith { a,b->NaturalOrder.compare(a.title,b.title) }; "added"->list.sortedByDescending { it.added }; else->list.sortedByDescending { w->history.filter { h->volumes.any { it.hash==h.hash && it.workId==w.id } }.maxOfOrNull { it.opened } ?: 0 } } }
    val current=history.firstNotNullOfOrNull { h->volumes.find { it.hash==h.hash } }
    LazyColumn(state=state,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.collection,works.size),color=Accent,fontSize=12.sp); ScreenTitle(stringResource(R.string.my_library),stringResource(R.string.library_subtitle)) }
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.search))},singleLine=true,leadingIcon={Icon(KomaIcons.Search,null)},trailingIcon={if(query.isNotEmpty()) IconAction(KomaIcons.Close,stringResource(R.string.clear)){query=""}},shape=RoundedCornerShape(12.dp)) }
        if(works.isEmpty()) item { Empty(stringResource(R.string.empty_library),stringResource(R.string.empty_library_body)) { Primary(stringResource(R.string.choose_files),onClick=add) } }
        if(current!=null) item {
            Section(stringResource(R.string.continue_reading)); Panel {
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Cover(vm.repo,current,Modifier.width(100.dp).height(150.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.now_reading),fontSize=11.sp,color=Accent)
                        Text(works.find { it.id==current.workId }?.title ?: current.title,fontSize=24.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${current.title} · ${current.format}",color=Muted,maxLines=2)
                        Text(stringResource(R.string.position,current.page+1,current.pages),color=Muted,fontSize=13.sp)
                        LinearProgressIndicator(progress={(current.page+1f)/current.pages},modifier=Modifier.fillMaxWidth(),color=Accent,trackColor=Border)
                    }
                }
                Primary(stringResource(R.string.continue_action)) { open(current) }
            }
        }
        if(works.isNotEmpty()) item {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.all_works),fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f)); IconAction(KomaIcons.FilterList,stringResource(R.string.filter)){filters=!filters}; IconAction(if(p.grid) KomaIcons.ViewList else KomaIcons.GridView,stringResource(if(p.grid) R.string.list else R.string.grid)){vm.save(p.copy(grid=!p.grid))} }
            if(filters) Panel {
                Text(stringResource(R.string.status),color=Muted)
                FlowChoices(listOf("all","new","reading","done","later"),p.status,{if(it=="all") stringResource(R.string.all) else stringResource(statusRes(it))}){vm.save(p.copy(status=it))}
                Text(stringResource(R.string.format),color=Muted)
                FlowChoices(listOf("all","PDF","CBZ"),p.format,{if(it=="all") stringResource(R.string.all) else it}){vm.save(p.copy(format=it))}
                Text(stringResource(R.string.sort),color=Muted)
                FlowChoices(listOf("recent","added","title"),p.sort,{stringResource(when(it){"added"->R.string.sort_added;"title"->R.string.sort_title;else->R.string.sort_recent})}){vm.save(p.copy(sort=it))}
            }
        }
        if(works.isNotEmpty() && filtered.isEmpty()) item { Empty(stringResource(R.string.nothing),stringResource(R.string.nothing_body)) { TextButton({query="";vm.save(p.copy(status="all",format="all"))}) { Text(stringResource(R.string.clear)) } } }
        if(p.grid) items(filtered.chunked(columns),key={it.first().id}) { row-> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { row.forEach { w-> WorkCard(vm,w,volumes.filter { it.workId==w.id },Modifier.weight(1f)){work(w.id)} }; repeat(columns-row.size) { Spacer(Modifier.weight(1f)) } } }
        else items(filtered,key={it.id}) { w-> Panel(Modifier.clickable { work(w.id) }) { Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) { Cover(vm.repo,volumes.find { it.workId==w.id },Modifier.size(64.dp,88.dp).clip(RoundedCornerShape(8.dp))); Column { Text(w.title,fontWeight=FontWeight.Bold); Text(stringResource(statusRes(w.status)),color=Accent); Text(stringResource(R.string.work_meta,volumes.count { it.workId==w.id },volumes.filter { it.workId==w.id }.map { it.format }.distinct().joinToString(" / ")),color=Muted) } } } }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun FlowChoices(options:List<String>,value:String,label:@Composable (String)->String,onSelect:(String)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { options.forEach { FilterChip(value==it,{onSelect(it)},label={Text(label(it))}) } } }
@Composable fun WorkCard(vm:LibraryViewModel,w:Work,vs:List<Volume>,modifier:Modifier,onClick:()->Unit) {
    Column(modifier.clickable(onClick=onClick),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Cover(vm.repo,vs.firstOrNull(),Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(12.dp)))
        Text(w.title,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(stringResource(R.string.work_meta,vs.size,vs.map { it.format }.distinct().joinToString(" / ")),fontSize=12.sp,color=Muted)
        Text(stringResource(statusRes(w.status)),fontSize=12.sp,color=Accent)
    }
}
@Composable fun TextDialog(title:String,initial:String,dismiss:()->Unit,allowEmpty:Boolean=false,save:(String)->Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={OutlinedTextField(text,{text=it},label={Text(stringResource(R.string.title))})},confirmButton={TextButton({save(text.trim());dismiss()},enabled=allowEmpty || text.isNotBlank()){Text(stringResource(R.string.save))}},dismissButton={TextButton(dismiss){Text(stringResource(R.string.cancel))}})
}
@Composable fun Confirm(title:String,body:String,dismiss:()->Unit,action:()->Unit) { AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton({action();dismiss()}){Text(stringResource(R.string.confirm))}},dismissButton={TextButton(dismiss){Text(stringResource(R.string.cancel))}}) }
@Composable fun WorkScreen(vm:LibraryViewModel,id:String,back:()->Unit,add:(String)->Unit,open:(Volume)->Unit) {
    val works by vm.works.collectAsStateWithLifecycle(); val volumes by vm.volumes.collectAsStateWithLifecycle(); val history by vm.history.collectAsStateWithLifecycle()
    val w=works.find { it.id==id } ?: return
    val vs=volumes.filter { it.workId==id }.sortedBy { it.order }
    val current=history.firstNotNullOfOrNull { h->vs.find { it.hash==h.hash } } ?: vs.firstOrNull()
    var rename by remember { mutableStateOf(false) }; var actions by remember { mutableStateOf<Volume?>(null) }; var edit by remember { mutableStateOf<Volume?>(null) }; var deleting by remember { mutableStateOf<Volume?>(null) }; var move by remember { mutableStateOf<Volume?>(null) }; var setting by remember { mutableStateOf(false) }
    Column {
        BackHeader(stringResource(R.string.work),back) { IconAction(KomaIcons.Edit,stringResource(R.string.rename)){rename=true} }
        LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Cover(vm.repo,vs.firstOrNull(),Modifier.width(104.dp).height(156.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(w.title,fontSize=28.sp,fontWeight=FontWeight.Bold); Text(stringResource(R.string.work_meta,vs.size,vs.map { it.format }.distinct().joinToString(" / ")),color=Muted); Text(stringResource(statusRes(w.status)),color=Accent) }
            } }
            item { FlowChoices(listOf("new","reading","done","later"),w.status,{stringResource(statusRes(it))}){vm.work(w.copy(status=it))} }
            current?.let { v-> item { Primary(stringResource(R.string.continue_action)){open(v)} } }
            item { Section(stringResource(R.string.volumes)) }
            items(vs,key={it.hash}) { v-> Panel {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Cover(vm.repo,v,Modifier.size(56.dp,80.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).clickable { open(v) },verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(v.title,fontWeight=FontWeight.Bold); Text(stringResource(R.string.volume_meta,v.format,v.pages),color=Muted,fontSize=13.sp)
                        Text(stringResource(if(!v.available) R.string.missing_file else if(v.completed) R.string.completed else R.string.position, v.page+1,v.pages),color=if(!v.available) Accent else Muted,fontSize=12.sp)
                        LinearProgressIndicator(progress={(v.page+1f)/v.pages},Modifier.fillMaxWidth(),color=Accent,trackColor=Border)
                    }
                    IconAction(KomaIcons.PlayArrow,stringResource(R.string.open)){open(v)}
                    IconAction(KomaIcons.MoreVert,stringResource(R.string.volume_actions)){actions=v}
                }
            } }
            item { OutlinedButton({add(id)},Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(12.dp)){Icon(KomaIcons.Add,null);Text(stringResource(R.string.add_volume))} }
            item { OutlinedButton({setting=true},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(stringResource(R.string.individual))} }
        }
    }
    if(rename) TextDialog(stringResource(R.string.rename_work),w.title,{rename=false}){vm.work(w.copy(title=it))}
    edit?.let { v->TextDialog(stringResource(R.string.rename),v.title,{edit=null}){vm.volume(v.copy(title=it))} }
    deleting?.let { v->Confirm(stringResource(R.string.delete_volume),stringResource(R.string.delete_volume_body),{deleting=null}){vm.remove(v)} }
    actions?.let { v-> AlertDialog(onDismissRequest={actions=null},title={Text(v.title)},text={Column {
        TextButton({edit=v;actions=null}){Text(stringResource(R.string.rename))}
        TextButton({vm.reorder(v,-1);actions=null}){Text(stringResource(R.string.up))}
        TextButton({vm.reorder(v,1);actions=null}){Text(stringResource(R.string.down))}
        TextButton({move=v;actions=null}){Text(stringResource(R.string.move))}
        TextButton({deleting=v;actions=null}){Text(stringResource(R.string.delete))}
    }},confirmButton={TextButton({actions=null}){Text(stringResource(R.string.close))}}) }
    move?.let { v-> AlertDialog(onDismissRequest={move=null},title={Text(stringResource(R.string.move))},text={Column(Modifier.verticalScroll(rememberScrollState())){works.filter { it.id!=id }.forEach { dest->TextButton({vm.volume(v.copy(workId=dest.id,order=(volumes.filter { it.workId==dest.id }.maxOfOrNull { it.order } ?: -1)+1));move=null}){Text(dest.title)} }}},confirmButton={TextButton({move=null}){Text(stringResource(R.string.cancel))}}) }
    if(setting) ReadingSettingsDialog(vm,w,{setting=false})
}
@Composable fun ImportScreen(vm:LibraryViewModel,back:()->Unit,commit:()->Unit,open:(Volume)->Unit,more:()->Unit) {
    val items by vm.candidates.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle(); val progress by vm.progress.collectAsStateWithLifecycle(); val name by vm.progressName.collectAsStateWithLifecycle(); val works by vm.works.collectAsStateWithLifecycle()
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    val count=items.count { it.error==null && it.duplicate==null }
    androidx.activity.compose.BackHandler { back() }
    Column {
        BackHeader(stringResource(R.string.import_title),back)
        LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { ScreenTitle(stringResource(R.string.import_heading),stringResource(R.string.import_subtitle)) }
            if(busy) item { Panel { LinearProgressIndicator(Modifier.fillMaxWidth(),color=Accent);Text(stringResource(R.string.preparing));Text(stringResource(R.string.import_progress,name,sizeLabel(progress)),color=Muted);TextButton(back){Text(stringResource(R.string.cancel_import))} } }
            itemsIndexed(items,key={i,c->"$i-${c.uri}"}) { index,c-> Panel {
                Text(c.name,fontWeight=FontWeight.Bold)
                if(volumes.any {it.hash==c.hash && it.sourceUri==null})Text(stringResource(R.string.relink_copy),color=Accent)
                when {
                    c.error!=null->{ Text(stringResource(errorResource(c.error)),color=Accent);TextButton({vm.prepare(listOf(Uri.parse(c.uri)),c.existingWork)}){Text(stringResource(R.string.retry))} }
                    c.duplicate!=null->{Text(stringResource(R.string.duplicate),color=Accent);TextButton({open(c.duplicate)}){Text(stringResource(R.string.open))} }
                    else->{
                        Text("${c.format} · ${c.pages} · ${sizeLabel(c.bytes)}",color=Muted)
                        OutlinedTextField(c.title,{vm.updateCandidate(index,c.copy(title=it))},label={Text(stringResource(R.string.file_name))},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                        OutlinedTextField(c.workTitle,{vm.updateCandidate(index,c.copy(workTitle=it,existingWork=null))},label={Text(stringResource(R.string.work_name))},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                        if(works.isNotEmpty()) { var choose by remember { mutableStateOf(false) }; TextButton({choose=true},enabled=!busy){Text(works.find { it.id==c.existingWork }?.title ?: stringResource(R.string.choose_work))}; DropdownMenu(choose,{choose=false}) { DropdownMenuItem(text={Text(stringResource(R.string.new_work))},onClick={vm.updateCandidate(index,c.copy(existingWork=null));choose=false});works.forEach { w->DropdownMenuItem(text={Text(w.title)},onClick={vm.updateCandidate(index,c.copy(existingWork=w.id,workTitle=w.title));choose=false}) } } }
                    }
                }
            } }
            item { TextButton(more,enabled=!busy){Text(stringResource(R.string.choose_more))} }
            item { Section(stringResource(R.string.storage));Panel { Text(stringResource(R.string.copy_app),fontWeight=FontWeight.Bold);Text(stringResource(R.string.copy_body),color=Muted);Text(stringResource(R.string.files_total,count,sizeLabel(items.filter { it.error==null && it.duplicate==null }.sumOf { it.bytes }))) } }
            item { Primary(stringResource(R.string.add_count,count),enabled=!busy && count>0 && items.filter { it.error==null && it.duplicate==null }.all { it.title.isNotBlank() && it.workTitle.isNotBlank() },onClick=commit) }
        }
    }
}
fun errorResource(key:String)=when(key) { "error_persistent"->R.string.error_persistent;"error_seekable"->R.string.error_seekable;"error_password"->R.string.error_password;"error_access"->R.string.error_access;"error_space"->R.string.error_space;"error_format"->R.string.error_format;"error_empty"->R.string.error_empty;"error_duplicate"->R.string.error_duplicate;else->R.string.error_damaged }
@Composable fun BookmarksScreen(vm:LibraryViewModel,open:(Volume,Int?)->Unit,snackbar:SnackbarHostState) {
    val marks by vm.bookmarks.collectAsStateWithLifecycle();val volumes by vm.volumes.collectAsStateWithLifecycle();val works by vm.works.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") };var filter by rememberSaveable { mutableStateOf("all") };var note by remember { mutableStateOf<Bookmark?>(null) };val scope=rememberCoroutineScope()
    val undo=stringResource(R.string.undo);val removed=stringResource(R.string.bookmark_removed)
    val selected=marks.filter { b-> val v=volumes.find { it.hash==b.hash }; val w=works.find { it.id==v?.workId }; (filter=="all" || w?.id==filter) && (b.note.contains(query,true) || w?.title?.contains(query,true)==true || v?.title?.contains(query,true)==true) }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle(stringResource(R.string.bookmarks),stringResource(R.string.bookmark_subtitle));OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.bookmark_search))}) }
        item { LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { item { FilterChip(filter=="all",{filter="all"},label={Text(stringResource(R.string.all))}) }; items(works){w->FilterChip(filter==w.id,{filter=w.id},label={Text(w.title)})} } }
        if(marks.isEmpty()) item { Empty(stringResource(R.string.bookmark_empty),stringResource(R.string.bookmark_empty_body)) }
        else if(selected.isEmpty()) item { Empty(stringResource(R.string.nothing),stringResource(R.string.nothing_body)) }
        items(selected,key={"${it.hash}:${it.page}"}) { b-> val v=volumes.find { it.hash==b.hash }; if(v!=null) Panel {
            Row(Modifier.clickable { open(v,b.page) },horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Cover(vm.repo,v,Modifier.width(96.dp).height(128.dp).clip(RoundedCornerShape(8.dp)),b.page)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(works.find { it.id==v.workId }?.title ?: v.title,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(v.title,color=Muted);Text(stringResource(R.string.position,b.page+1,v.pages),color=Muted);if(b.note.isNotEmpty()) Text(b.note) }
            }
            Row { TextButton({note=b}){Text(stringResource(R.string.note))};TextButton({vm.unmark(b);scope.launch { if(snackbar.showSnackbar(removed,undo)==SnackbarResult.ActionPerformed) vm.bookmark(b) }}){Text(stringResource(R.string.delete))} }
        } }
    }
    note?.let { b->TextDialog(stringResource(R.string.note),b.note,{note=null},allowEmpty=true){vm.bookmark(b.copy(note=it))} }
}
@Composable fun HistoryScreen(vm:LibraryViewModel,open:(Volume)->Unit) {
    val history by vm.history.collectAsStateWithLifecycle();val volumes by vm.volumes.collectAsStateWithLifecycle();val works by vm.works.collectAsStateWithLifecycle();var clear by remember { mutableStateOf(false) }
    val today=LocalDate.now();val groups=history.groupBy { val d=Instant.ofEpochMilli(it.opened).atZone(ZoneId.systemDefault()).toLocalDate();when(d){today->R.string.today;today.minusDays(1)->R.string.yesterday;else->R.string.earlier} }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle(stringResource(R.string.history),stringResource(R.string.history_subtitle));if(history.isNotEmpty()) TextButton({clear=true}){Text(stringResource(R.string.clear))} }
        if(history.isEmpty()) item { Empty(stringResource(R.string.history_empty),stringResource(R.string.history_empty_body)) }
        groups.forEach { (day,entries)-> item { Section(stringResource(day)) };items(entries,key={it.hash}) { h->val v=volumes.find { it.hash==h.hash };if(v!=null) Panel(Modifier.clickable { open(v) }) { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Cover(vm.repo,v,Modifier.size(64.dp,88.dp).clip(RoundedCornerShape(8.dp)));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(works.find { it.id==v.workId }?.title ?: v.title,fontWeight=FontWeight.Bold);Text(v.title,color=Muted);Text(stringResource(R.string.position,v.page+1,v.pages),color=Muted);Text(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(Instant.ofEpochMilli(h.opened).atZone(ZoneId.systemDefault())),fontSize=12.sp,color=Muted) } } } } }
    }
    if(clear) Confirm(stringResource(R.string.clear_history),stringResource(R.string.clear_history_body),{clear=false}){vm.clearHistory()}
}

fun messageResource(key:String)=when(key) {
    "backup_saved"->R.string.backup_saved
    "backup_restored"->R.string.backup_restored
    "error_backup"->R.string.error_backup
    "cache_cleared"->R.string.cache_cleared
    "import_report"->R.string.import_report
    "error_generic"->R.string.error_generic
    else->errorResource(key)
}
