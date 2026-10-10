package io.ahad.connectorhub.core;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.function.Function;
/** Single-process encrypted catalog. Copy-on-write transactions and atomic, fsynced snapshots. */
public final class Catalog implements AutoCloseable {
    private final Path dir,file;
    private final byte[] key;
    private final ReadWriteLock lock=new ReentrantReadWriteLock();
    private Map<String,Object> state;
    private final FileChannel lockChannel;
    private final FileLock processLock;
    public Catalog(Path dir,byte[] key) throws IOException {
        this.dir=dir.toAbsolutePath();
        this.file=this.dir.resolve("catalog.enc");
        this.key=key.clone();
        Files.createDirectories(this.dir);
        privateMode(this.dir,true);
        lockChannel=FileChannel.open(this.dir.resolve("process.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock held;
        try {
            held=lockChannel.tryLock();
        }
        catch(OverlappingFileLockException e) {
            lockChannel.close();
            throw new IOException("Catalog already in use");
        }
        if(held==null) {
            lockChannel.close();
            throw new IOException("Catalog already in use");
        }
        processLock=held;
        try {
            state=Files.exists(file)?Json.object(Json.parseStored(new String(Crypto.decrypt(key,Files.readAllBytes(file),"connector-hub/catalog/v1"),StandardCharsets.UTF_8))):empty();
            if(Json.integer(state,"schemaVersion",0)!=1)throw new IOException("Unsupported catalog schema");
        }
        catch(RuntimeException|IOException e) {
            processLock.release();
            lockChannel.close();
            throw e;
        }
    }
    public static Map<String,Object> empty() {
        var s=new LinkedHashMap<String,Object>();
        s.put("schemaVersion",1);
        for(String k:List.of("connections","providers","publications","keys","jobs","idempotency","documents","settings"))s.put(k,new LinkedHashMap<String,Object>());
        s.put("audit",new ArrayList<>());
        return s;
    }
    public <T>T read(Function<Map<String,Object>,T> fn) {
        lock.readLock().lock();
        try {
            return fn.apply(Json.object(Json.copy(state)));
        }
        finally {
            lock.readLock().unlock();
        }
    }
    public <T>T update(Function<Map<String,Object>,T> fn) {
        lock.writeLock().lock();
        try {
            var next=Json.object(Json.copy(state));
            T result=fn.apply(next);
            write(next);
            state=next;
            return detached(result);
        }
        finally {
            lock.writeLock().unlock();
        }
    }
    @SuppressWarnings("unchecked") private static <T>T detached(T value) {
        return value instanceof Map<?,?>||value instanceof List<?>?(T)Json.copy(value):value;
    }
    private void write(Map<String,Object> next) {
        Path temp=dir.resolve("catalog.enc.tmp");
        try {
            byte[] plain=Json.stringify(next).getBytes(StandardCharsets.UTF_8);
            if(plain.length>67_108_864)throw new HubException("CAPACITY",503);
            byte[] bytes=Crypto.encrypt(key,plain,"connector-hub/catalog/v1");
            try(FileChannel c=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                privateMode(temp,false);
                ByteBuffer b=ByteBuffer.wrap(bytes);
                while(b.hasRemaining())c.write(b);
                c.force(true);
            }
            try {
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }
            catch(AtomicMoveNotSupportedException e) {
                throw new IOException("Atomic move required for catalog safety",e);
            }
            try(FileChannel c=FileChannel.open(dir,StandardOpenOption.READ)) {
                c.force(true);
            }
            catch(IOException|UnsupportedOperationException ignored) {
                /* Windows directory fsync is unavailable. */
            }
        }
        catch(IOException e) {
            throw new HubException("STORE_UNAVAILABLE",503);
        }
    }
    public static void privateMode(Path path,boolean directory) {
        try {
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
        }
        catch(IOException|UnsupportedOperationException ignored) {
            /* Operator must configure Windows ACLs; see SECURITY. */
        }
    }
    @Override public void close() throws IOException {
        processLock.release();
        lockChannel.close();
        Arrays.fill(key,(byte)0);
    }
}
