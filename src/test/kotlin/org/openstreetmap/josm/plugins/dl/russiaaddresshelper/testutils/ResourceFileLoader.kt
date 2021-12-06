package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.testutils

import java.io.IOException
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Paths

object ResourceFileLoader {
    @Throws(URISyntaxException::class, IOException::class) fun getResourceBytes(relativeToClass: Class<*>, path: String): ByteArray {
        val res = relativeToClass.getResource(path)
        val uri = res!!.toURI()
        return Files.readAllBytes(Paths.get(uri))
    }
}
