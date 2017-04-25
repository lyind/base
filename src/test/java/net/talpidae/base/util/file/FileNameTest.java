package net.talpidae.base.util.file;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class FileNameTest
{
    @Test
    public void testFileNameSanitize()
    {
        assertEquals("ble-file-blu.exe", FileName.sanitize(" ble  file \nblu.exe \n\t "));
        assertEquals("aou-Datei.exe", FileName.sanitize(" \n\t \u00E4\u00F6\u00FC Datei\".exe "));
    }
}
