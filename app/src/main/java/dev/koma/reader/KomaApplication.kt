package dev.koma.reader
import android.app.Application
import dev.koma.reader.data.Repository
class KomaApplication:Application() { val repository by lazy { Repository(this) } }
