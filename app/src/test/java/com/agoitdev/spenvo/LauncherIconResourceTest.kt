package com.agoitdev.spenvo

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherIconResourceTest {

    @Test
    fun `launcher foreground compensates artwork optical offset`() {
        val resource = File("src/main/res/drawable/ic_launcher_foreground.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource)
        val item = document.getElementsByTagName("item").item(0).attributes

        assertEquals("1.25dp", item.getNamedItem("android:left")?.nodeValue)
        assertEquals("-1.25dp", item.getNamedItem("android:right")?.nodeValue)
        assertEquals("-6dp", item.getNamedItem("android:top")?.nodeValue)
        assertEquals("6dp", item.getNamedItem("android:bottom")?.nodeValue)
    }
}
