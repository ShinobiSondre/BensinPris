package com.bensinpris.app.util

import java.util.Locale

/** Norwegian for nb/nn/no phones, English otherwise. */
fun tr(en: String, no: String): String =
    if (Locale.getDefault().language in listOf("nb", "no", "nn")) no else en
