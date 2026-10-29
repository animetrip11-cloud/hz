package dev.koma.reader.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.koma.reader.R

val Background=Color(0xFF0D1117)
val Surface=Color(0xFF191F28)
val Elevated=Color(0xFF222A35)
val Border=Color(0xFF2A323E)
val Ink=Color(0xFFF2F4F7)
val Muted=Color(0xFFAFB6C2)
val Accent=Color(0xFFE65A7A)
val Action=Color(0xFFCD3C60)
@Composable fun KomaTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Accent,onPrimary=Color.White,primaryContainer=Action,onPrimaryContainer=Color.White,background=Background,onBackground=Ink,surface=Surface,onSurface=Ink,surfaceVariant=Elevated,onSurfaceVariant=Muted,outline=Border,outlineVariant=Border,secondary=Accent,onSecondary=Ink,secondaryContainer=Elevated,onSecondaryContainer=Accent,tertiary=Accent,tertiaryContainer=Elevated,onTertiaryContainer=Ink,surfaceTint=Accent,surfaceContainerLowest=Background,surfaceContainerLow=Surface,surfaceContainer=Surface,surfaceContainerHigh=Elevated,surfaceContainerHighest=Elevated,surfaceDim=Background,surfaceBright=Elevated),content=content)
}
@Composable fun Logo(modifier:Modifier=Modifier) { Image(painterResource(R.drawable.ic_koma),stringResource(R.string.logo),modifier) }
@Composable fun Section(title:String) { Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=16.dp,bottom=8.dp)) }
@Composable fun Primary(text:String,enabled:Boolean=true,onClick:()->Unit) { Button(onClick,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=enabled,shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Action,contentColor=Color.White)) { Text(text,fontSize=16.sp,fontWeight=FontWeight.SemiBold) } }
@Composable fun Panel(modifier:Modifier=Modifier, content:@Composable ColumnScope.()->Unit) { Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=Surface,border=BorderStroke(1.dp,Border)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content) } }
@Composable fun Empty(title:String,body:String,content:@Composable ()->Unit={}) { Column(Modifier.fillMaxWidth().padding(vertical=40.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) { Logo(Modifier.size(64.dp)); Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold); Text(body,color=Muted); content() } }
