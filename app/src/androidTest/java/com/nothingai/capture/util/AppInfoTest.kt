package com.nothingai.capture.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppInfoTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun resolvesOwnPackageLabelAndIcon() {
        assertThat(AppInfo.label(ctx, ctx.packageName)).isNotEmpty()
        assertThat(AppInfo.icon(ctx, ctx.packageName)).isNotNull()
    }

    @Test fun fallsBackForUnknownOrNull() {
        assertThat(AppInfo.label(ctx, "com.does.not.exist")).isEqualTo("com.does.not.exist")
        assertThat(AppInfo.label(ctx, null)).isEqualTo("Unknown app")
        assertThat(AppInfo.icon(ctx, "com.does.not.exist")).isNull()
        assertThat(AppInfo.icon(ctx, null)).isNull()
    }
}
