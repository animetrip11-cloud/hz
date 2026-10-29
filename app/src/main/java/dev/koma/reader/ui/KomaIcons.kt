package dev.koma.reader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** A curated 24 dp outline set; no bundled catalogue of unused icons. */
object KomaIcons {
    private fun icon(name:String,path:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).addPath(
        pathData=PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round
    ).build()
    val Add=icon("Add","M12 5V19M5 12H19")
    val ArrowBack=icon("Back","M19 12H5M11 5L4 12L11 19")
    val Search=icon("Search","M20 20L15 15M17 10A7 7 0 1 1 3 10A7 7 0 1 1 17 10")
    val Close=icon("Close","M6 6L18 18M6 18L18 6")
    val Edit=icon("Edit","M4 16L15 5L19 9L8 20H4ZM14 6L18 10M16 4L18 2L22 6L20 8")
    val MoreVert=icon("More","M12 4V5M12 11V12M12 18V19")
    val PlayArrow=icon("Read","M8 4L20 12L8 20Z")
    val LocalLibrary=icon("Library","M4 5V20M9 4V19M14 5V20M18 4L22 19")
    val BookmarkBorder=icon("Bookmark","M6 3H18V21L12 16L6 21Z")
    val Bookmark=icon("Bookmarked","M6 3H18V21L12 16L6 21ZM9 8L11 10L15 6")
    val History=icon("History","M4 6V11H9M4 10A9 9 0 1 1 5 18M12 7V12L16 14")
    val Tune=icon("Settings","M3 6H8M12 6H21M3 12H14M18 12H21M3 18H6M10 18H21M8 3V9H12V3ZM14 9V15H18V9ZM6 15V21H10V15Z")
    val ViewList=icon("List","M3 4H7V8H3ZM10 6H21M3 10H7V14H3ZM10 12H21M3 16H7V20H3ZM10 18H21")
    val GridView=icon("Grid","M3 3H9V9H3ZM15 3H21V9H15ZM3 15H9V21H3ZM15 15H21V21H15Z")
    val FilterList=icon("Filter","M3 4H21L14 12V20L10 18V12Z")
    val CollectionsBookmark=icon("Pages","M7 3H21V19H7ZM3 7V23H17M15 3V10L17 8L19 10V3")
    val ViewAgenda=icon("Mode","M4 3H20V10H4ZM4 14H20V21H4Z")
}
