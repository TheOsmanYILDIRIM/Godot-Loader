package com.osman.godotloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicit, non-destructive save copy between versioned PCK profiles.
 * Never automatically assumes two exported packs belong to the same game.
 */
public final class SaveProfiles {
    private SaveProfiles() {}
    private static final int MAX_DEPTH = 24;
    private static final long MAX_TOTAL = 512L * 1024 * 1024;
    private static final int MAX_FILES = 10000;

    public static File profile(File appFiles, String packName) throws IOException {
        if (!PackStore.validName(packName)) throw new IOException("Invalid profile ID");
        File base = new File(appFiles, "profiles").getCanonicalFile();
        File target = new File(base, packName.substring(0,64)).getCanonicalFile();
        if (!target.getParentFile().equals(base)) throw new IOException("Invalid profile path");
        return target;
    }

    public static void copyForUpdate(File appFiles, String oldPack, String newPack) throws IOException {
        if (oldPack.equals(newPack)) return;
        File oldRoot = profile(appFiles, oldPack);
        File newRoot = profile(appFiles, newPack);
        if (!oldRoot.isDirectory()) return; // No previous game saves exist.
        if (Files.isSymbolicLink(oldRoot.toPath()) || Files.isSymbolicLink(newRoot.toPath())) {
            throw new IOException("Symlink profile is unsupported");
        }
        // Fail closed if a previous import or game startup has created destination data.
        if (newRoot.exists()) throw new IOException("New version already has saves; not overwriting");
        File staging = new File(newRoot.getParentFile(), newRoot.getName()+".migration-partial");
        if (staging.exists()) throw new IOException("Existing interrupted migration needs review");
        if (!staging.mkdirs()) throw new IOException("Cannot stage save migration");
        boolean published=false;
        try {
            Budget budget=new Budget();
            transfer(oldRoot,staging,0,budget);
            if (!staging.renameTo(newRoot)) throw new IOException("Cannot publish migrated save safely");
            published=true;
        } finally {
            if (!published) deleteTree(staging);
        }
    }

    private static final class Budget {
        int files;
        long bytes;
    }

    private static void transfer(File source, File destination, int depth, Budget budget) throws IOException {
        if (depth>MAX_DEPTH) throw new IOException("Save tree too deep");
        File[] children=source.listFiles();
        if (children==null) throw new IOException("Cannot read saves");
        for (File child:children) {
            if (Files.isSymbolicLink(child.toPath())) throw new IOException("Symlink in saves");
            File to=new File(destination,child.getName());
            if (child.isDirectory()) {
                if (!to.mkdir()) throw new IOException("Cannot create save directory");
                transfer(child,to,depth+1,budget);
            } else if (child.isFile()) {
                if (++budget.files>MAX_FILES) throw new IOException("Too many save files");
                long size=child.length();
                if (size>MAX_TOTAL-budget.bytes) throw new IOException("Save backup exceeds limit");
                budget.bytes+=size;
                try(FileInputStream in=new FileInputStream(child);FileOutputStream out=new FileOutputStream(to)) {
                    byte[] buf=new byte[65536];int n;long copied=0;
                    while ((n=in.read(buf))!=-1) {
                        copied+=n;
                        if (copied>size) throw new IOException("Concurrent save modification");
                        out.write(buf,0,n);
                    }
                    out.getFD().sync();
                    if (copied!=size) throw new IOException("Concurrent save modification");
                }
            } else throw new IOException("Unsupported save entry");
        }
    }

    private static void deleteTree(File file) {
        File[] children=file.listFiles();
        if (children!=null) for(File c:children) {
            if (!Files.isSymbolicLink(c.toPath())) deleteTree(c);
        }
        file.delete();
    }
}
