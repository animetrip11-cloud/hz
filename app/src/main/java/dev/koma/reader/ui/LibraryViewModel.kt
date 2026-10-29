package dev.koma.reader.ui

import android.app.Application
import android.net.Uri
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.koma.reader.KomaApplication
import dev.koma.reader.core.*
import dev.koma.reader.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LibraryViewModel(app:Application):AndroidViewModel(app) {
    val repo=(app as KomaApplication).repository
    val works=repo.dao.works().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val volumes=repo.dao.volumes().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val bookmarks=repo.dao.bookmarks().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val history=repo.dao.history().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val prefs=repo.preferences.stateIn(viewModelScope,SharingStarted.Eagerly,Preferences())
    val candidates=MutableStateFlow<List<Candidate>>(emptyList())
    val busy=MutableStateFlow(false)
    val progress=MutableStateFlow(0L)
    val progressName=MutableStateFlow("")
    val message=MutableSharedFlow<String>(extraBufferCapacity=8)
    private var importJob:Job?=null
    init { viewModelScope.launch { runCatching { repo.initialize() }.onFailure { message.tryEmit("error_generic") } } }
    fun prepare(uris:List<Uri>,target:String?,append:Boolean=false) {
        val previousJob=importJob
        previousJob?.cancel()
        importJob=viewModelScope.launch {
            previousJob?.join()
            if(!append)repo.discardStaging()
            val previous=if(append)candidates.value else emptyList()
            busy.value=true
            if(!append)candidates.value=emptyList()
            try {
                val added=repo.prepare(uris,target) { n,b->progressName.value=n; progress.value=b }
                candidates.value=previous+added.map { c-> if(c.hash.isNotEmpty() && previous.any { it.hash==c.hash })c.copy(error="error_duplicate") else c }
            }
            catch(e:CancellationException) { withContext(NonCancellable) { repo.discardStaging() }; throw e }
            catch(e:Exception) { message.emit(repo.errorKey(e)) }
            finally { busy.value=false }
        }
    }
    fun cancelImport() { importJob?.cancel(); viewModelScope.launch { importJob?.join(); repo.discardStaging(); candidates.value=emptyList() } }
    fun commit(done:()->Unit) {
        importJob=viewModelScope.launch {
            busy.value=true
            try { repo.commit(candidates.value) { progress.value=it.toLong() }
                val failures=candidates.value.filter { it.error!=null }
                candidates.value=failures
                if(failures.isEmpty())done() else message.emit("import_report") }
            catch(e:CancellationException) { withContext(NonCancellable) { repo.discardStaging() }; throw e }
            catch(e:Exception) { message.emit(repo.errorKey(e)) }
            finally { busy.value=false }
        }
    }
    fun updateCandidate(index:Int,c:Candidate) { candidates.value=candidates.value.toMutableList().apply { set(index,c) } }
    fun save(p:Preferences) { viewModelScope.launch { repo.preferences(p) } }
    fun work(w:Work) { viewModelScope.launch { repo.dao.put(w) } }
    fun volume(v:Volume) { viewModelScope.launch { repo.db.withTransaction {
        val old=repo.dao.volume(v.hash);repo.dao.put(v)
        if(old!=null && old.workId!=v.workId) repo.dao.removeEmptyWork(old.workId)
    } } }
    fun complete(hash:String) { viewModelScope.launch { repo.dao.volume(hash)?.let { repo.dao.put(it.copy(completed=true)) } } }
    fun remove(v:Volume) { viewModelScope.launch { repo.delete(v) } }
    fun position(hash:String,page:Int,offset:Float=0f) { viewModelScope.launch { repo.dao.position(hash,page,offset) } }
    fun mark(hash:String,page:Int) { viewModelScope.launch {
        if(bookmarks.value.any { it.hash==hash && it.page==page }) repo.dao.removeBookmark(hash,page) else repo.dao.put(Bookmark(hash,page))
    } }
    fun bookmark(b:Bookmark) { viewModelScope.launch { repo.dao.put(b) } }
    fun unmark(b:Bookmark) { viewModelScope.launch { repo.dao.removeBookmark(b.hash,b.page) } }
    fun clearHistory() { viewModelScope.launch { repo.dao.clearHistory() } }
    fun reorder(v:Volume,delta:Int) {
        val list=volumes.value.filter { it.workId==v.workId }.sortedBy { it.order }.toMutableList()
        val i=list.indexOfFirst { it.hash==v.hash }; val j=(i+delta).coerceIn(0,list.lastIndex)
        if(i>=0 && i!=j) { java.util.Collections.swap(list,i,j); viewModelScope.launch { repo.db.withTransaction { list.forEachIndexed { n,item->repo.dao.put(item.copy(order=n)) } } } }
    }
}
