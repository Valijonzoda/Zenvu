package com.worldtv.app.ui.language

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldtv.app.R
import com.worldtv.app.data.LanguageStore
import com.worldtv.app.data.LocaleApplier
import com.worldtv.app.domain.AppLanguages
import com.worldtv.app.ui.theme.LocalWt

@Composable
fun LanguagePicker(onPick: (String) -> Unit) {
    val palette = LocalWt.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(
            stringResource(R.string.language_choose),
            color = palette.heading,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )
        LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
            items(AppLanguages.all, key = { it.tag }) { language ->
                Text(
                    language.nativeName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(language.tag) }
                        .padding(vertical = 14.dp),
                    color = palette.heading,
                    fontFamily = FontFamily.Default,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

fun applyAppLanguage(context: Context, tag: String) {
    if (LocaleApplier.read(context) == tag) return
    LanguageStore(context).select(tag)
    (context as? Activity)?.recreate()
}
