package dev.koma.reader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.koma.reader.R
import dev.koma.reader.core.*
import dev.koma.reader.data.codec
import kotlinx.coroutines.*

@Composable fun SettingsScreen(vm:LibraryViewModel) {
    val p by vm.prefs.collectAsStateWithLifecycle(); val scope=rememberCoroutineScope(); var cache by remember { mutableLongStateOf(0L) };var confirmRestore by remember { mutableStateOf(false) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri->if(uri!=null) scope.launch { runCatching { val text=vm.repo.export();withContext(Dispatchers.IO) { vm.repo.context.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(text) } } }.onSuccess {vm.message.tryEmit("backup_saved")}.onFailure {vm.message.tryEmit("error_generic")} } }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null) scope.launch { runCatching { withContext(Dispatchers.IO) {
        val text=vm.repo.context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader->val out=StringBuilder();val buffer=CharArray(8192);while(true){val n=reader.read(buffer);if(n<0)break;require(out.length+n<=16*1024*1024);out.append(buffer,0,n)};out.toString() };vm.repo.restore(text)
    } }.onSuccess {vm.message.tryEmit("backup_restored")}.onFailure {vm.message.tryEmit("error_backup")} } }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { cache=vm.repo.context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } } }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle(stringResource(R.string.settings),stringResource(R.string.settings_subtitle)) }
        item { Section(stringResource(R.string.defaults));Panel { ReadingControls(p.reading){vm.save(p.copy(reading=it))} } }
        item { Section(stringResource(R.string.memory));Panel {
            Text("${stringResource(R.string.cache)} · ${sizeLabel(cache)}")
            OutlinedButton({scope.launch { withContext(Dispatchers.IO) { vm.repo.context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } };dev.koma.reader.reader.PageBitmaps.clear();cache=0;vm.message.emit("cache_cleared") }}){Text(stringResource(R.string.clear_cache))}
        } }
        item { Section(stringResource(R.string.backup));Panel { Text(stringResource(R.string.backup_title));Text(stringResource(R.string.backup_body),color=Muted);Primary(stringResource(R.string.export)){export.launch("KOMA-backup.json")};OutlinedButton({confirmRestore=true},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(stringResource(R.string.restore))} } }
        item { Section(stringResource(R.string.about));Panel { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) { Logo(Modifier.size(48.dp));Text(stringResource(R.string.about_body)) } } }
    }
    if(confirmRestore) Confirm(stringResource(R.string.restore),stringResource(R.string.restore_body),{confirmRestore=false}){restore.launch(arrayOf("application/json","application/octet-stream","text/plain"))}
}
@Composable fun ReadingControls(s:ReadingSettings,change:(ReadingSettings)->Unit) {
    Text(stringResource(R.string.mode),color=Muted)
    FlowChoices(listOf("rtl","ltr","vertical"),s.mode,{stringResource(modeRes(it))}){change(s.copy(mode=it))}
    Toggle(stringResource(R.string.auto_long_pages),s.autoLongPages){change(s.copy(autoLongPages=it))}
    Text(stringResource(R.string.scale),color=Muted)
    Row { FilterChip(!s.fitWidth,{change(s.copy(fitWidth=false))},label={Text(stringResource(R.string.whole_page))});Spacer(Modifier.width(8.dp));FilterChip(s.fitWidth,{change(s.copy(fitWidth=true))},label={Text(stringResource(R.string.fit_width))}) }
    Toggle(stringResource(R.string.awake),s.keepAwake){change(s.copy(keepAwake=it))}
    Toggle(stringResource(R.string.volume_keys),s.volumeKeys){change(s.copy(volumeKeys=it))}
    Toggle(stringResource(R.string.system_brightness),s.brightness<0){change(s.copy(brightness=if(it)-1f else .5f))}
    if(s.brightness>=0) { Text(stringResource(R.string.brightness),color=Muted);Slider(s.brightness,{change(s.copy(brightness=it))},valueRange=.01f..1f) }
}
@Composable fun Toggle(label:String,value:Boolean,change:(Boolean)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f));Switch(value,change) } }
@Composable fun ReadingSettingsDialog(vm:LibraryViewModel,w:Work?,dismiss:()->Unit) {
    val p by vm.prefs.collectAsStateWithLifecycle()
    val initial=w?.overrides?.let { runCatching { codec.decodeFromString<Overrides>(it) }.getOrNull() } ?: Overrides()
    var overrides by remember(w?.id,w?.overrides) { mutableStateOf(initial) }
    var global by remember { mutableStateOf(p.reading) }
    AlertDialog(onDismissRequest=dismiss,title={Text(stringResource(if(w==null) R.string.defaults else R.string.individual))},text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        if(w==null) ReadingControls(global){global=it} else {
            Text(stringResource(R.string.inherited),color=Muted)
            val effective=overrides.resolve(p.reading)
            ReadingControls(effective) { updated->
                // Only fields actually changed become overrides; inherited fields stay null.
                overrides=overrides.copy(mode=if(updated.mode!=effective.mode) updated.mode else overrides.mode,fitWidth=if(updated.fitWidth!=effective.fitWidth) updated.fitWidth else overrides.fitWidth,keepAwake=if(updated.keepAwake!=effective.keepAwake) updated.keepAwake else overrides.keepAwake,volumeKeys=if(updated.volumeKeys!=effective.volumeKeys) updated.volumeKeys else overrides.volumeKeys,brightness=if(updated.brightness!=effective.brightness) updated.brightness else overrides.brightness,autoLongPages=if(updated.autoLongPages!=effective.autoLongPages)updated.autoLongPages else overrides.autoLongPages)
            }
            TextButton({overrides=Overrides()}){Text(stringResource(R.string.as_default))}
        }
    }},confirmButton={TextButton({if(w==null)vm.save(p.copy(reading=global)) else vm.work(w.copy(overrides=if(overrides==Overrides())null else codec.encodeToString(overrides)));dismiss()}){Text(stringResource(R.string.save))}},dismissButton={TextButton(dismiss){Text(stringResource(R.string.cancel))}})
}
