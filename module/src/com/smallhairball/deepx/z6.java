package com.smallhairball.deepx;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Pure-Java generator for a per-install Local API CA and server certificate. */
final class z6 {
    static final class Authority {
        final KeyPair keyPair;
        final X509Certificate certificate;

        Authority(KeyPair keyPair, X509Certificate certificate) {
            this.keyPair = keyPair;
            this.certificate = certificate;
        }
    }

    static final class ServerIdentity {
        final KeyPair keyPair;
        final X509Certificate certificate;

        ServerIdentity(KeyPair keyPair, X509Certificate certificate) {
            this.keyPair = keyPair;
            this.certificate = certificate;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] SHA256_WITH_RSA = sequence(
            oid("1.2.840.113549.1.1.11"), der(0x05, new byte[0]));

    private z6() {}

    static Authority createAuthority() throws Exception {
        KeyPair pair = rsaKeyPair();
        byte[] name = commonName("DeePX dq0 CA");
        Date before = new Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
        Date after = new Date(System.currentTimeMillis()
                + 10L * 365L * 24L * 60L * 60L * 1000L);
        byte[] extensions = explicit(3, sequence(
                extension("2.5.29.19", true, sequence(bool(true))),
                extension("2.5.29.15", true, bitString(1, new byte[]{0x06}))));
        X509Certificate certificate = issue(pair.getPublic().getEncoded(), name, name,
                pair.getPrivate(), before, after, extensions);
        certificate.verify(pair.getPublic());
        return new Authority(pair, certificate);
    }

    static ServerIdentity createServer(Authority authority, Set<String> ipAddresses)
            throws Exception {
        if (authority == null || authority.keyPair == null
                || authority.certificate == null) {
            throw new IllegalArgumentException("missing certificate authority");
        }
        KeyPair pair = rsaKeyPair();
        byte[] issuer = authority.certificate.getSubjectX500Principal().getEncoded();
        byte[] subject = commonName("DeePX dq0");
        Date before = new Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
        Date after = new Date(System.currentTimeMillis()
                + 825L * 24L * 60L * 60L * 1000L);
        byte[] extensions = explicit(3, sequence(
                extension("2.5.29.19", true, sequence()),
                extension("2.5.29.15", true,
                        bitString(5, new byte[]{(byte) 0xA0})),
                extension("2.5.29.37", false,
                        sequence(oid("1.3.6.1.5.5.7.3.1"))),
                extension("2.5.29.17", false, subjectAlternativeNames(ipAddresses))));
        X509Certificate certificate = issue(pair.getPublic().getEncoded(), issuer, subject,
                authority.keyPair.getPrivate(), before, after, extensions);
        certificate.verify(authority.certificate.getPublicKey());
        return new ServerIdentity(pair, certificate);
    }

    static boolean covers(X509Certificate certificate, Set<String> requiredIps) {
        if (certificate == null) return false;
        try {
            certificate.checkValidity();
            Set<String> covered = new LinkedHashSet<String>();
            java.util.Collection<List<?>> alternatives =
                    certificate.getSubjectAlternativeNames();
            if (alternatives != null) {
                for (List<?> item : alternatives) {
                    if (item == null || item.size() < 2 || !(item.get(0) instanceof Number)) {
                        continue;
                    }
                    int type = ((Number) item.get(0)).intValue();
                    if (type == 7 && item.get(1) != null) {
                        Object value = item.get(1);
                        if (value instanceof byte[]) {
                            covered.add(InetAddress.getByAddress((byte[]) value).getHostAddress());
                        } else {
                            covered.add(String.valueOf(value));
                        }
                    }
                }
            }
            Set<String> required = normalizedIps(requiredIps);
            return covered.containsAll(required);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String subjectHashOld(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(
                certificate.getSubjectX500Principal().getEncoded());
        long value = ((long) digest[0] & 0xffL)
                | (((long) digest[1] & 0xffL) << 8)
                | (((long) digest[2] & 0xffL) << 16)
                | (((long) digest[3] & 0xffL) << 24);
        return String.format(Locale.US, "%08x", value);
    }

    private static X509Certificate issue(byte[] publicKeyInfo, byte[] issuer, byte[] subject,
                                         PrivateKey signer, Date notBefore, Date notAfter,
                                         byte[] extensions) throws Exception {
        byte[] serialBytes = new byte[16];
        RANDOM.nextBytes(serialBytes);
        serialBytes[0] &= 0x7f;
        BigInteger serial = new BigInteger(1, serialBytes);
        if (BigInteger.ZERO.equals(serial)) serial = BigInteger.ONE;
        byte[] tbs = sequence(
                explicit(0, integer(BigInteger.valueOf(2L))),
                integer(serial),
                SHA256_WITH_RSA,
                issuer,
                sequence(time(notBefore), time(notAfter)),
                subject,
                publicKeyInfo,
                extensions);
        byte[] encoded = sequence(
                tbs, SHA256_WITH_RSA, bitString(0, signatureBytes(tbs, signer)));
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new java.io.ByteArrayInputStream(encoded));
    }

    private static byte[] signatureBytes(byte[] value, PrivateKey signer) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signer, RANDOM);
        signature.update(value);
        return signature.sign();
    }

    private static byte[] subjectAlternativeNames(Set<String> ipAddresses) throws Exception {
        List<byte[]> names = new ArrayList<byte[]>();
        names.add(der(0x82, "localhost".getBytes("US-ASCII")));
        for (String ip : normalizedIps(ipAddresses)) {
            names.add(der(0x87, InetAddress.getByName(ip).getAddress()));
        }
        return sequence(names.toArray(new byte[names.size()][]));
    }

    private static Set<String> normalizedIps(Set<String> ipAddresses) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        result.add("127.0.0.1");
        if (ipAddresses != null) {
            for (String value : ipAddresses) {
                try {
                    InetAddress parsed = InetAddress.getByName(
                            value == null ? "" : value.trim());
                    if (parsed.getAddress().length == 4) {
                        result.add(parsed.getHostAddress());
                    }
                } catch (Throwable ignored) {}
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, RANDOM);
        return generator.generateKeyPair();
    }

    private static byte[] commonName(String value) throws Exception {
        return sequence(set(sequence(oid("2.5.4.3"),
                der(0x0c, value.getBytes("UTF-8")))));
    }

    private static byte[] extension(String id, boolean critical, byte[] value) {
        return critical
                ? sequence(oid(id), bool(true), der(0x04, value))
                : sequence(oid(id), der(0x04, value));
    }

    private static byte[] integer(BigInteger value) {
        return der(0x02, value.toByteArray());
    }

    private static byte[] bool(boolean value) {
        return der(0x01, new byte[]{value ? (byte) 0xff : 0x00});
    }

    private static byte[] bitString(int unusedBits, byte[] value) {
        byte[] body = new byte[value.length + 1];
        body[0] = (byte) unusedBits;
        System.arraycopy(value, 0, body, 1, value.length);
        return der(0x03, body);
    }

    private static byte[] time(Date value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return der(0x17, format.format(value).getBytes("US-ASCII"));
    }

    private static byte[] oid(String value) {
        String[] parts = value.split("\\.");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        body.write(first * 40 + second);
        for (int i = 2; i < parts.length; i++) {
            long part = Long.parseLong(parts[i]);
            byte[] encoded = new byte[10];
            int index = encoded.length;
            encoded[--index] = (byte) (part & 0x7f);
            while ((part >>>= 7) != 0L) encoded[--index] = (byte) (0x80 | (part & 0x7f));
            body.write(encoded, index, encoded.length - index);
        }
        return der(0x06, body.toByteArray());
    }

    private static byte[] sequence(byte[]... values) {
        return der(0x30, concat(values));
    }

    private static byte[] set(byte[]... values) {
        return der(0x31, concat(values));
    }

    private static byte[] explicit(int number, byte[] value) {
        return der(0xa0 + number, value);
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (values != null) {
            for (byte[] value : values) {
                if (value != null) output.write(value, 0, value.length);
            }
        }
        return output.toByteArray();
    }

    private static byte[] der(int tag, byte[] value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(tag);
        int length = value == null ? 0 : value.length;
        if (length < 128) {
            output.write(length);
        } else {
            int bytes = 0;
            for (int n = length; n != 0; n >>>= 8) bytes++;
            output.write(0x80 | bytes);
            for (int shift = (bytes - 1) * 8; shift >= 0; shift -= 8) {
                output.write((length >>> shift) & 0xff);
            }
        }
        if (length > 0) output.write(value, 0, length);
        return output.toByteArray();
    }
}
