package com.github.avbox.osc.player.engine

import android.net.Uri
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import com.github.avbox.osc.util.AppLog

internal class DiagnosticExtractorsFactory : ExtractorsFactory {
    private val delegate = DefaultExtractorsFactory()
    override fun createExtractors(): Array<Extractor> = wrap(delegate.createExtractors())
    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        wrap(delegate.createExtractors(uri, responseHeaders))

    private fun wrap(extractors: Array<Extractor>): Array<Extractor> = extractors.map { extractor ->
        object : Extractor by extractor {
            override fun init(output: ExtractorOutput) {
                AppLog.i("echo-player-extractor: ${extractor.javaClass.name}")
                extractor.init(output)
            }
        }
    }.toTypedArray()
}
