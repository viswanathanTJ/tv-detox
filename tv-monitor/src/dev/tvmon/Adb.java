package dev.tvmon;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Minimal adb client for this TV's own adb daemon on 127.0.0.1:5555. Runs shell commands, nothing else. */
final class Adb {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257;
    static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    final Socket sock = new Socket();
    InputStream in;
    OutputStream out;
    final KeyPair key;

    /** Thrown when the TV is still waiting for the user to press Allow. */
    static final class NeedsAllow extends Exception { NeedsAllow() { super("Press Allow on the TV"); } }

    Adb(Context c) throws Exception { key = loadKey(c); }

    static KeyPair loadKey(Context c) throws Exception {
        File priv = new File(c.getFilesDir(), "adbkey.pk8"), pub = new File(c.getFilesDir(), "adbkey.x509");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.exists() && pub.exists()) {
            try {
                return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(read(pub))), kf.generatePrivate(new PKCS8EncodedKeySpec(read(priv))));
            } catch (Exception ignored) { }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        try (FileOutputStream o = new FileOutputStream(priv)) { o.write(kp.getPrivate().getEncoded()); }
        try (FileOutputStream o = new FileOutputStream(pub)) { o.write(kp.getPublic().getEncoded()); }
        return kp;
    }

    static byte[] read(File f) throws Exception {
        try (FileInputStream i = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    static final class Msg { int cmd, arg0, arg1; byte[] data; }

    void send(int cmd, int a0, int a1, byte[] data) throws Exception {
        int len = data == null ? 0 : data.length, sum = 0;
        for (int i = 0; i < len; i++) sum += data[i] & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(a0).putInt(a1).putInt(len).putInt(sum).putInt(cmd ^ 0xffffffff);
        out.write(h.array());
        if (len > 0) out.write(data);
        out.flush();
    }

    void readFully(byte[] b) throws Exception {
        int o = 0;
        while (o < b.length) { int n = in.read(b, o, b.length - o); if (n < 0) throw new java.io.EOFException(); o += n; }
    }

    Msg recv() throws Exception {
        byte[] hb = new byte[24];
        readFully(hb);
        ByteBuffer h = ByteBuffer.wrap(hb).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = h.getInt(); m.arg0 = h.getInt(); m.arg1 = h.getInt();
        int len = h.getInt();
        m.data = new byte[len];
        if (len > 0) readFully(m.data);
        return m;
    }

    byte[] publicKeyBlob() {
        RSAPublicKey pk = (RSAPublicKey) key.getPublic();
        BigInteger n = pk.getModulus(), r32 = BigInteger.ONE.shiftLeft(32), mask = r32.subtract(BigInteger.ONE);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt(n.mod(r32).modInverse(r32).negate().mod(r32).intValue());
        for (int i = 0; i < 64; i++) b.putInt(n.shiftRight(32 * i).and(mask).intValue());
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        for (int i = 0; i < 64; i++) b.putInt(rr.shiftRight(32 * i).and(mask).intValue());
        b.putInt(pk.getPublicExponent().intValue());
        byte[] enc = Base64.encode(b.array(), Base64.NO_WRAP);
        byte[] tail = " tvmon@tv\0".getBytes();
        byte[] all = new byte[enc.length + tail.length];
        System.arraycopy(enc, 0, all, 0, enc.length);
        System.arraycopy(tail, 0, all, enc.length, tail.length);
        return all;
    }

    byte[] sign(byte[] token) throws Exception {
        Signature s = Signature.getInstance("NONEwithRSA");
        s.initSign(key.getPrivate());
        s.update(SHA1_PREFIX);
        s.update(token);
        return s.sign();
    }

    void connect() throws Exception {
        sock.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
        sock.setSoTimeout(8000);
        in = sock.getInputStream();
        out = sock.getOutputStream();
        send(CNXN, 0x01000000, 256 * 1024, "host::\0".getBytes());
        Msg m = recv();
        if (m.cmd == AUTH) {
            send(AUTH, 2, 0, sign(m.data));
            m = recv();
            if (m.cmd == AUTH) {                 // key not known yet: offer it, the TV shows "Allow debugging?"
                send(AUTH, 3, 0, publicKeyBlob());
                sock.setSoTimeout(45000);
                try { m = recv(); } catch (java.net.SocketTimeoutException e) { throw new NeedsAllow(); }
                sock.setSoTimeout(8000);
            }
        }
        if (m.cmd != CNXN) throw new NeedsAllow();
    }

    String shell(String cmd) throws Exception {
        send(OPEN, 1, 0, ("shell:" + cmd + "\0").getBytes());
        Msg m = recv();
        if (m.cmd != OKAY) throw new java.io.IOException("shell refused");
        int remote = m.arg0;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        while (true) {
            m = recv();
            if (m.cmd == WRTE) { b.write(m.data); send(OKAY, 1, remote, null); }
            else if (m.cmd == CLSE) break;
        }
        return b.toString("UTF-8");
    }

    void close() { try { sock.close(); } catch (Exception ignored) { } }
}
