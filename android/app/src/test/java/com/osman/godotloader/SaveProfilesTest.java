package com.osman.godotloader;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class SaveProfilesTest {
    private String id(char c) {return String.valueOf(c).repeat(64)+".pck";}
    @Test public void rejectsTraversal() throws Exception {
        File base=Files.createTempDirectory("profiles").toFile();
        try {SaveProfiles.profile(base,"../bad");fail();} catch(java.io.IOException expected) {}
    }
    @Test public void copiesWithoutOverwritingOriginal() throws Exception {
        File base=Files.createTempDirectory("profiles").toFile();
        File old=SaveProfiles.profile(base,id('a'));
        assertTrue(old.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(old,"save.txt"))) {out.write("old".getBytes());}
        SaveProfiles.copyForUpdate(base,id('a'),id('b'));
        assertTrue(new File(old,"save.txt").isFile());
        assertEquals("old",new String(Files.readAllBytes(new File(SaveProfiles.profile(base,id('b')),"save.txt").toPath()), java.nio.charset.StandardCharsets.UTF_8));
        try {SaveProfiles.copyForUpdate(base,id('a'),id('b'));fail();}catch(java.io.IOException expected){}
    }
}
