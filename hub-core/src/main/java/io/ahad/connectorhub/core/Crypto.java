package io.ahad.connectorhub.core;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** AES-256-GCM authenticated snapshots; SHA-256 hashes for random, high-entropy API tokens. */
public final class Crypto {
    private static final SecureRandom RANDOM=new SecureRandom();
    private Crypto() {
    }
    public static byte[] randomBytes(int size) {
        byte[] b=new byte[size];
        RANDOM.nextBytes(b);
        return b;
    }
    public static String token() {
        return "ch_"+Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
    }
    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        }
        catch(GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
    public static boolean equal(String a,String b) {
        return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    }
    public static byte[] encrypt(byte[] key,byte[] plaintext,String context) {
        try {
            check(key);
            byte[] nonce=randomBytes(12);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=c.doFinal(plaintext);
            byte[] all=new byte[13+encrypted.length];
            all[0]=1;
            System.arraycopy(nonce,0,all,1,12);
            System.arraycopy(encrypted,0,all,13,encrypted.length);
            return all;
        }
        catch(GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed",e);
        }
    }
    public static byte[] decrypt(byte[] key,byte[] bytes,String context) {
        try {
            check(key);
            if(bytes.length<29||bytes[0]!=1)throw new GeneralSecurityException();
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,bytes,1,12));
            c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return c.doFinal(bytes,13,bytes.length-13);
        }
        catch(GeneralSecurityException|IllegalArgumentException e) {
            throw new HubException("STORE_INTEGRITY",500);
        }
    }
    private static void check(byte[] key) {
        if(key==null||key.length!=32)throw new IllegalArgumentException("Master key must contain exactly 32 bytes");
    }
}
