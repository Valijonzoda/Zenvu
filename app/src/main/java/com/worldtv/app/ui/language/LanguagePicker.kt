package com.worldtv.app.ui.language

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldtv.app.R
import com.worldtv.app.data.LanguageStore
import com.worldtv.app.data.LocaleApplier
import com.worldtv.app.domain.AppLanguages
import com.worldtv.app.ui.components.wtShadow
import com.worldtv.app.ui.theme.LocalWt

@Composable
fun LanguagePicker(onPick: (String) -> Unit) {
    val palette = LocalWt.current
    val current = LocaleApplier.read(LocalContext.current)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Text(
            stringResource(R.string.language_choose),
            color = palette.heading,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            modifier = Modifier.padding(start = 6.dp, top = 20.dp, bottom = 4.dp),
        )
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp)
                .wtShadow(shape, dark = palette.dark)
                .clip(shape)
                .background(palette.surface),
        ) {
            items(AppLanguages.all, key = { it.tag }) { language ->
                val selected = language.tag == current
                if (language != AppLanguages.all.first()) {
                    HorizontalDivider(color = palette.line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(language.tag) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        language.nativeName,
                        color = if (selected) palette.accent else palette.heading,
                        fontFamily = FontFamily.Default,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

fun applyAppLanguage(context: Context, tag: String) {
    if (LocaleApplier.read(context) == tag) return
    LanguageStore(context).select(tag)
    (context as? Activity)?.recreate()
}
