package com.nezurstandalone.utils;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
/** Stage and fsync before replacement. Unsupported atomic moves fail closed. */
public final class AtomicConfigFiles {
    private AtomicConfigFiles(){}
    public static synchronized void write(Path path,byte[] bytes)throws IOException {
        Map<Path,byte[]> one=new LinkedHashMap<>();one.put(path,bytes);replaceBundle(one);
    }
    public static synchronized void replaceBundle(Map<Path,byte[]> desired)throws IOException {
        Map<Path,byte[]> previous=new LinkedHashMap<>();Map<Path,Path> staged=new LinkedHashMap<>();List<Path> committed=new ArrayList<>();
        try{
            // Read every backup and stage every replacement before touching a destination.
            for(Map.Entry<Path,byte[]> entry:desired.entrySet()){
                Path target=entry.getKey().toAbsolutePath().normalize();byte[] value=entry.getValue();
                if(value==null || value.length>BoundedJson.MAX_BYTES)throw new IOException("Config write limit");
                if(Files.exists(target) && (!Files.isRegularFile(target) || Files.size(target)>BoundedJson.MAX_BYTES))throw new IOException("Invalid config destination");
                byte[] old=Files.exists(target)?Files.readAllBytes(target):null;if(old!=null && old.length>BoundedJson.MAX_BYTES)throw new IOException("Config backup limit");
                previous.put(target,old);staged.put(target,stage(target,value));
            }
            for(Map.Entry<Path,Path> entry:staged.entrySet()){
                Files.move(entry.getValue(),entry.getKey(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);committed.add(entry.getKey());
            }
        }catch(IOException | RuntimeException failure){
            for(int i=committed.size()-1;i>=0;i--){Path target=committed.get(i);try{byte[] old=previous.get(target);if(old==null)Files.deleteIfExists(target);else{Path restore=stage(target,old);try{Files.move(restore,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(restore);}}}catch(IOException restoration){failure.addSuppressed(restoration);}}
            throw failure;
        }finally{for(Path temp:staged.values())Files.deleteIfExists(temp);}
    }
    private static Path stage(Path target,byte[] bytes)throws IOException {
        Path parent=target.getParent();Files.createDirectories(parent);Path temp=Files.createTempFile(parent,".nezur-",".tmp");boolean complete=false;
        try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)){
            ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining())channel.write(data);channel.force(true);complete=true;return temp;
        }finally{if(!complete)Files.deleteIfExists(temp);}
    }
}
