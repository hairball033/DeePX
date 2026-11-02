package com.smallhairball.deepx;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.security.KeyChain;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/** Per-install TLS identity, trust export and HTTPS listener support for the Local API. */
public final class z5 {
    private static final Object LOCK = new Object();
    private static final String ENABLED = "dq0_tls";
    private static final String VERIFIED = "dq0_tls_verified";
    private static final String PASSWORD = "dq0_tls_password";
    private static final String CA_STORE = "dq0_ca.p12";
    private static final String SERVER_STORE = "dq0_server.p12";
    private static final String CA_CERT = "dq0_ca.cer";
    private static final String CA_ALIAS = "deekseep-local-ca";
    private static final String SERVER_ALIAS = "dq0-tls";
    private static final String REPOSITORY = "https://github.com/hairball033/DeePX";

    private z5() {}

    public static boolean isEnabled(Context context) {
        return context != null && new File(context.getFilesDir(), ENABLED).isFile();
    }

    static void setEnabled(Context context, boolean enabled) throws Exception {
        if (context == null) throw new IllegalArgumentException("missing context");
        File marker = new File(context.getFilesDir(), ENABLED);
        if (enabled) {
            prepare(context);
            writeAtomic(marker, new byte[]{'1'});
        } else if (marker.exists() && !marker.delete()) {
            throw new java.io.IOException("could not remove HTTPS marker");
        }
        if (!enabled) {
            File verified = new File(context.getFilesDir(), VERIFIED);
            if (verified.exists() && !verified.delete()) {
                throw new java.io.IOException("could not remove HTTPS verification marker");
            }
        }
    }

    static void prepare(Context context) throws Exception {
        if (context == null) throw new IllegalArgumentException("missing context");
        Context app = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        synchronized (LOCK) {
            char[] password = password(app);
            File caStoreFile = new File(app.getFilesDir(), CA_STORE);
            File caCertFile = new File(app.getFilesDir(), CA_CERT);
            z6.Authority authority;
            if (caStoreFile.isFile() && caCertFile.isFile()) {
                KeyStore caStore = loadStore(caStoreFile, password);
                PrivateKey caKey = (PrivateKey) caStore.getKey(CA_ALIAS, password);
                X509Certificate ca = (X509Certificate) caStore.getCertificate(CA_ALIAS);
                if (caKey == null || ca == null) throw new java.io.IOException(
                        "Local API CA store is incomplete");
                authority = new z6.Authority(
                        new KeyPair(ca.getPublicKey(), caKey), ca);
            } else {
                authority = z6.createAuthority();
                storeAuthority(caStoreFile, password, authority);
                writeAtomic(caCertFile, authority.certificate.getEncoded());
            }

            File serverStoreFile = new File(app.getFilesDir(), SERVER_STORE);
            Set<String> addresses = currentAddresses();
            boolean renew = true;
            if (serverStoreFile.isFile()) {
                try {
                    KeyStore existing = loadStore(serverStoreFile, password);
                    X509Certificate server =
                            (X509Certificate) existing.getCertificate(SERVER_ALIAS);
                    server.verify(authority.certificate.getPublicKey());
                    renew = !z6.covers(server, addresses);
                } catch (Throwable ignored) {
                    renew = true;
                }
            }
            if (renew) {
                z6.ServerIdentity server =
                        z6.createServer(authority, addresses);
                storeServer(serverStoreFile, password, server, authority.certificate);
                File verified = new File(app.getFilesDir(), VERIFIED);
                if (verified.exists()) verified.delete();
            }
        }
    }

    public static ServerSocket createUnboundServerSocket(Context context) throws Exception {
        prepare(context);
        char[] password = password(context);
        KeyStore store = loadStore(new File(context.getFilesDir(), SERVER_STORE), password);
        KeyManagerFactory managers = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, password);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(managers.getKeyManagers(), null, new SecureRandom());
        SSLServerSocket server = (SSLServerSocket)
                ssl.getServerSocketFactory().createServerSocket();
        String[] supported = server.getSupportedProtocols();
        java.util.ArrayList<String> enabled = new java.util.ArrayList<String>();
        for (String protocol : supported) {
            if ("TLSv1.3".equals(protocol) || "TLSv1.2".equals(protocol)) {
                enabled.add(protocol);
            }
        }
        if (!enabled.isEmpty()) server.setEnabledProtocols(
                enabled.toArray(new String[enabled.size()]));
        server.setUseClientMode(false);
        server.setNeedClientAuth(false);
        server.setWantClientAuth(false);
        return server;
    }

    static String scheme(Context context) {
        return isEnabled(context) ? "https" : "http";
    }

    static String status(Context context) {
        if (context == null) return UiLanguage.text(
                "DeepSeek 上下文尚未就绪", "DeepSeek context is not ready");
        if (!isEnabled(context)) return UiLanguage.text(
                "HTTPS：已关闭", "HTTPS: off");
        try {
            prepare(context);
            X509Certificate ca = caCertificate(context);
            X509Certificate server = serverCertificate(context);
            boolean verified = new File(context.getFilesDir(), VERIFIED).isFile();
            return (verified
                    ? UiLanguage.text(context, "HTTPS：CA 校验已通过",
                            "HTTPS: CA verification passed")
                    : UiLanguage.text(context, "HTTPS：已配置，但 CA 尚未校验",
                            "HTTPS: configured, but CA is not verified"))
                    + UiLanguage.text(context, "\nCA 指纹：", "\nCA fingerprint: ")
                    + fingerprint(ca)
                    + UiLanguage.text(context, "\n服务器证书有效期至：",
                            "\nServer certificate expires: ")
                    + new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .format(server.getNotAfter())
                    + UiLanguage.text(context,
                            "\n私钥：仅保存在 DeepSeek 私有目录",
                            "\nPrivate key: stored only in DeepSeek private storage");
        } catch (Throwable error) {
            return UiLanguage.text(context, "HTTPS 配置异常：", "HTTPS error: ")
                    + safe(error);
        }
    }

    /** Verifies the running listener against the per-install CA and the certificate SAN. */
    static String validate(Context context) throws Exception {
        if (context == null) throw new IllegalArgumentException("missing context");
        if (!isEnabled(context)) throw new java.io.IOException("HTTPS is not configured");
        if (!z13.isRunning()) {
            throw new java.io.IOException("Local API listener is not running");
        }
        int port = z13.tlsPort();
        // A user-installed CA is NOT part of Android's default trust store for apps on API 24+,
        // so a plain default-trust handshake fails even after the CA is installed.  Build a fresh
        // context that trusts our per-install CA for the self-check, verifying the running
        // listener's chain + SAN instead of the app's system-trust policy.
        X509Certificate ca = caCertificate(context);
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry(CA_ALIAS, ca);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trustStore);
        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trust.getTrustManagers(), new SecureRandom());
        SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setSoTimeout(3000);
            socket.startHandshake();
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(
                    "127.0.0.1", socket.getSession())) {
                throw new javax.net.ssl.SSLPeerUnverifiedException(
                        "server certificate does not cover 127.0.0.1");
            }
        } finally {
            try { socket.close(); } catch (Throwable ignored) {}
        }
        writeAtomic(new File(context.getFilesDir(), VERIFIED), new byte[]{'1'});
        return UiLanguage.text(context,
                "校验通过：系统已信任 CA，HTTPS 监听与证书地址均正常",
                "Verification passed: Android trusts the CA and the HTTPS listener is valid");
    }

    static void openUserCertificateInstaller(Activity activity) throws Exception {
        if (activity == null) throw new IllegalArgumentException("missing activity");
        prepare(activity);
        Intent intent = KeyChain.createInstallIntent();
        intent.putExtra(KeyChain.EXTRA_NAME, "DeePX Local API CA");
        intent.putExtra(KeyChain.EXTRA_CERTIFICATE, caCertificate(activity).getEncoded());
        activity.startActivity(intent);
    }

    static String exportCaCertificate(Context context) throws Exception {
        prepare(context);
        return writeDownload(context, "DeePX-Local-API-CA.cer",
                "application/x-x509-ca-cert", caCertificate(context).getEncoded());
    }

    static String exportRootModule(Context context) throws Exception {
        prepare(context);
        X509Certificate ca = caCertificate(context);
        String hash = z6.subjectHashOld(ca);
        byte[] pem = pem(ca);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        add(zip, "module.prop",
                "id=dq0_ca\n"
                        + "name=DeePX Local API CA\n"
                        + "version=1.0\nversionCode=1\n"
                        + "author=Smallhairball\n"
                        + "description=Trust the per-device DeePX Local API HTTPS CA\n");
        add(zip, "customize.sh", rootInstallerScript(hash));
        add(zip, "system/etc/security/cacerts/" + hash + ".0", pem);
        zip.finish();
        zip.close();
        return writeDownload(context, "DeePX-CA-Root.zip",
                "application/zip", bytes.toByteArray());
    }

    /** Installs the public CA as a systemless Root module; never writes the read-only system. */
    static String installRootSystemCertificate(Context context) throws Exception {
        if (context == null) throw new IllegalArgumentException("missing context");
        prepare(context);
        X509Certificate ca = caCertificate(context);
        String hash = z6.subjectHashOld(ca);
        if (!hash.matches("[0-9a-fA-F]{8}")) throw new java.io.IOException("invalid CA hash");
        File stage = new File(context.getFilesDir(), "dq0_ca_root_stage");
        if (!stage.exists() && !stage.mkdirs()) throw new java.io.IOException(
                "could not create Root certificate staging directory");
        File certificate = new File(stage, hash + ".0");
        File property = new File(stage, "module.prop");
        File service = new File(stage, "service.sh");
        writeAtomic(certificate, pem(ca));
        writeAtomic(property, ("id=dq0_ca\nname=DeePX Local API System CA\n"
                + "version=1.1\nversionCode=2\nauthor=Smallhairball\n"
                + "description=System trust for the per-device DeePX Local API CA\n")
                .getBytes("UTF-8"));
        writeAtomic(service, rootBootService(hash).getBytes("UTF-8"));
        String module = "/data/adb/modules/dq0_ca";
        String command = "set -eu; test \"$(id -u)\" = 0; "
                + "mkdir -p " + module + "/system/etc/security/cacerts; "
                + "cp " + shellQuote(property.getAbsolutePath()) + " " + module + "/module.prop; "
                + "cp " + shellQuote(service.getAbsolutePath()) + " " + module + "/service.sh; "
                + "cp " + shellQuote(certificate.getAbsolutePath()) + " "
                + module + "/system/etc/security/cacerts/" + hash + ".0; "
                + "chown -R 0:0 " + module + "; chmod 0755 " + module + " "
                + module + "/service.sh; chmod 0644 " + module + "/module.prop "
                + module + "/system/etc/security/cacerts/" + hash + ".0; "
                + "touch " + module + "/auto_mount; rm -f " + module + "/disable "
                + module + "/remove; echo installed:" + hash;
        String output = runRoot(command);
        return UiLanguage.text(context,
                "系统 CA 模块已安装，重启设备后生效。证书：" + hash + ".0",
                "System CA module installed. Reboot the device to apply it. Certificate: "
                        + hash + ".0") + (output.length() == 0 ? "" : "\n" + output);
    }

    private static String rootBootService(String hash) {
        return "#!/system/bin/sh\n"
                + "CERT=/data/adb/modules/dq0_ca/system/etc/security/cacerts/" + hash + ".0\n"
                + "[ -f \"$CERT\" ] || exit 0\n"
                + "chmod 0644 \"$CERT\"; chown 0:0 \"$CERT\"\n"
                + "APEX=/apex/com.android.conscrypt/cacerts\n"
                + "if [ -d \"$APEX\" ]; then\n"
                + "  WORK=/data/adb/dq0-ca-conscrypt\n"
                + "  rm -rf \"$WORK\"; mkdir -p \"$WORK\"\n"
                + "  cp -af \"$APEX/.\" \"$WORK/\"\n"
                + "  cp -f \"$CERT\" \"$WORK/" + hash + ".0\"\n"
                + "  chown -R 0:0 \"$WORK\"; chmod 0755 \"$WORK\"; chmod 0644 \"$WORK\"/*\n"
                + "  mount --bind \"$WORK\" \"$APEX\"\n"
                + "fi\n";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String runRoot(String command) throws Exception {
        String[] candidates = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "su"};
        Throwable last = null;
        for (String candidate : candidates) {
            try {
                Process process = new ProcessBuilder(candidate, "-c", command)
                        .redirectErrorStream(true).start();
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroy();
                    throw new java.io.IOException("Root certificate installation timed out");
                }
                InputStream input = process.getInputStream();
                ByteArrayOutputStream captured = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while (captured.size() < 8192
                        && (count = input.read(buffer, 0,
                        Math.min(buffer.length, 8192 - captured.size()))) > 0) {
                    captured.write(buffer, 0, count);
                }
                input.close();
                byte[] data = captured.toByteArray();
                String output = new String(data, "UTF-8").trim();
                if (process.exitValue() != 0) throw new java.io.IOException(
                        "Root certificate installation failed: " + output);
                return output;
            } catch (Throwable error) {
                last = error;
            }
        }
        throw new java.io.IOException("Root unavailable or permission was denied", last);
    }

    private static String rootInstallerScript(String hash) {
        return "#!/system/bin/sh\n"
                + "ui_print \"- Repository: " + REPOSITORY + "\"\n"
                + "ENVIRONMENT=\"Unknown root manager\"\n"
                + "if [ \"$APATCH\" = \"true\" ] || [ -x /data/adb/ap/bin/apd ]; then\n"
                + "  ENVIRONMENT=\"APatch\"\n"
                + "elif [ \"$KSU\" = \"true\" ] || [ -x /data/adb/ksu/bin/ksud ]; then\n"
                + "  KSU_NAME=\"$(/data/adb/ksu/bin/ksud -V 2>/dev/null)\"\n"
                + "  case \"$KSU_NAME\" in *Suki*|*suki*) ENVIRONMENT=\"SukiSU Ultra\" ;;\n"
                + "    *) ENVIRONMENT=\"KernelSU\" ;; esac\n"
                + "elif command -v magisk >/dev/null 2>&1; then\n"
                + "  MAGISK_NAME=\"$(magisk -V 2>/dev/null)\"\n"
                + "  case \"$MAGISK_NAME\" in *alpha*|*Alpha*) ENVIRONMENT=\"Magisk Alpha\" ;;\n"
                + "    *) ENVIRONMENT=\"Magisk\" ;; esac\n"
                + "fi\n"
                + "ui_print \"- Detected: $ENVIRONMENT\"\n"
                + "ui_print \"- CA file: " + hash + ".0\"\n"
                + "set_perm_recursive $MODPATH 0 0 0755 0644\n"
                + "set_perm $MODPATH/system/etc/security/cacerts/" + hash
                + ".0 0 0 0644\n"
                + "ui_print \"- Reboot after installation\"\n";
    }

    private static X509Certificate caCertificate(Context context) throws Exception {
        char[] password = password(context);
        return (X509Certificate) loadStore(
                new File(context.getFilesDir(), CA_STORE), password)
                .getCertificate(CA_ALIAS);
    }

    private static X509Certificate serverCertificate(Context context) throws Exception {
        char[] password = password(context);
        return (X509Certificate) loadStore(
                new File(context.getFilesDir(), SERVER_STORE), password)
                .getCertificate(SERVER_ALIAS);
    }

    private static void storeAuthority(File file, char[] password,
                                       z6.Authority authority) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry(CA_ALIAS, authority.keyPair.getPrivate(), password,
                new Certificate[]{authority.certificate});
        storeAtomic(file, store, password);
    }

    private static void storeServer(File file, char[] password,
                                    z6.ServerIdentity server,
                                    X509Certificate ca) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry(SERVER_ALIAS, server.keyPair.getPrivate(), password,
                new Certificate[]{server.certificate, ca});
        storeAtomic(file, store, password);
    }

    private static KeyStore loadStore(File file, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        InputStream input = new FileInputStream(file);
        try { store.load(input, password); }
        finally { input.close(); }
        return store;
    }

    private static void storeAtomic(File file, KeyStore store, char[] password)
            throws Exception {
        File temporary = new File(file.getPath() + ".new");
        FileOutputStream output = new FileOutputStream(temporary, false);
        try {
            store.store(output, password);
            output.getFD().sync();
        } finally {
            output.close();
        }
        replace(temporary, file);
    }

    private static char[] password(Context context) throws Exception {
        File file = new File(context.getFilesDir(), PASSWORD);
        if (file.isFile()) return new String(readLimited(file, 256), "US-ASCII").toCharArray();
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String encoded = Base64.encodeToString(random, Base64.NO_WRAP);
        writeAtomic(file, encoded.getBytes("US-ASCII"));
        return encoded.toCharArray();
    }

    private static Set<String> currentAddresses() {
        LinkedHashSet<String> addresses = new LinkedHashSet<String>();
        addresses.add("127.0.0.1");
        try {
            String root = z13.lanRootEndpoint();
            if (root != null) {
                String host = Uri.parse(root).getHost();
                if (host != null && host.length() > 0) addresses.add(host);
            }
        } catch (Throwable ignored) {}
        return addresses;
    }

    private static byte[] pem(X509Certificate certificate) throws Exception {
        String encoded = Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP);
        StringBuilder value = new StringBuilder("-----BEGIN CERTIFICATE-----\n");
        for (int i = 0; i < encoded.length(); i += 64) {
            value.append(encoded, i, Math.min(encoded.length(), i + 64)).append('\n');
        }
        value.append("-----END CERTIFICATE-----\n");
        return value.toString().getBytes("US-ASCII");
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        byte[] value = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length; i++) {
            if (i > 0) result.append(':');
            result.append(String.format(Locale.US, "%02X", value[i] & 0xff));
        }
        return result.toString();
    }

    private static String writeDownload(Context context, String name, String mime, byte[] data)
            throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = context.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new java.io.IOException("Downloads provider rejected file");
            try {
                OutputStream output = context.getContentResolver().openOutputStream(uri, "w");
                if (output == null) throw new java.io.IOException("could not open Downloads file");
                try { output.write(data); output.flush(); }
                finally { output.close(); }
                return name;
            } catch (Throwable error) {
                try { context.getContentResolver().delete(uri, null, null); }
                catch (Throwable ignored) {}
                throw error;
            }
        }
        File directory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new java.io.IOException("could not create Downloads directory");
        }
        File target = new File(directory, name);
        FileOutputStream output = new FileOutputStream(target, false);
        try { output.write(data); output.getFD().sync(); }
        finally { output.close(); }
        return target.getAbsolutePath();
    }

    private static void add(ZipOutputStream zip, String path, String value) throws Exception {
        add(zip, path, value.getBytes("UTF-8"));
    }

    private static void add(ZipOutputStream zip, String path, byte[] value) throws Exception {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(value);
        zip.closeEntry();
    }

    private static byte[] readLimited(File file, int limit) throws Exception {
        if (!file.isFile() || file.length() <= 0L || file.length() > limit) {
            throw new java.io.IOException("invalid private TLS file");
        }
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] value = new byte[(int) file.length()];
            int offset = 0;
            while (offset < value.length) {
                int count = input.read(value, offset, value.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != value.length) throw new java.io.IOException("short TLS file read");
            return value;
        } finally { input.close(); }
    }

    private static void writeAtomic(File file, byte[] value) throws Exception {
        File temporary = new File(file.getPath() + ".new");
        FileOutputStream output = new FileOutputStream(temporary, false);
        try { output.write(value); output.getFD().sync(); }
        finally { output.close(); }
        replace(temporary, file);
    }

    private static void replace(File temporary, File target) throws Exception {
        if (target.exists() && !target.delete()) {
            throw new java.io.IOException("could not replace private TLS file");
        }
        if (!temporary.renameTo(target)) {
            throw new java.io.IOException("could not commit private TLS file");
        }
    }

    private static String safe(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getSimpleName()
                + (message == null || message.length() == 0 ? "" : ": " + message);
    }
}
