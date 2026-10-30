package com.shortvideoscripagent.xhsagentyunying.billing.payment.provider;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * S4-2 PEM 密钥解析工具（支付宝 / 微信 RSA 签名用）。
 * <p>
 * 支持 {@code -----BEGIN PRIVATE KEY-----}（PKCS8）、{@code -----BEGIN RSA PRIVATE KEY-----}（PKCS1）
 * 与 {@code -----BEGIN PUBLIC KEY-----} / {@code -----BEGIN RSA PUBLIC KEY-----}。
 * </p>
 * <p>
 * <b>安全约定</b>：本类不缓存、不记录、不输出任何密钥内容。
 * </p>
 */
final class PemKeyUtils {

    private PemKeyUtils() {
    }

    /**
     * 解析私钥（支持 PKCS8 与 PKCS1）。
     */
    static PrivateKey parsePrivateKey(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("private key missing");
        }
        String body = stripPem(pem);
        byte[] der = Base64.getMimeDecoder().decode(body);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            // PKCS1 兜底：包一层 PKCS8 前缀
            byte[] pkcs8 = wrapPkcs1(der);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        }
    }

    /**
     * 解析公钥（支持 X.509 SubjectPublicKeyInfo 与 PKCS1）。
     */
    static PublicKey parsePublicKey(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("public key missing");
        }
        String body = stripPem(pem);
        byte[] der = Base64.getMimeDecoder().decode(body);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            byte[] x509 = wrapPkcs1Public(der);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(x509));
        }
    }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
    }

    /**
     * PKCS1 私钥 → PKCS8 包装。
     */
    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] algorithmIdentifier = new byte[]{
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octetStringHeader = new byte[]{0x04, (byte) 0x82};
        byte[] length = intToBytes(pkcs1.length);
        byte[] inner = concat(version, algorithmIdentifier, octetStringHeader, length, pkcs1);
        byte[] seqHeader = new byte[]{0x30, (byte) 0x82};
        byte[] outerLength = intToBytes(inner.length);
        return concat(seqHeader, outerLength, inner);
    }

    /**
     * PKCS1 公钥 → X.509 包装。
     */
    private static byte[] wrapPkcs1Public(byte[] pkcs1) {
        byte[] algorithmIdentifier = new byte[]{
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] bitStringHeader = new byte[]{0x03, (byte) 0x82};
        byte[] length = intToBytes(pkcs1.length + 1);
        byte[] zero = new byte[]{0x00};
        byte[] inner = concat(bitStringHeader, length, zero, pkcs1);
        byte[] seqHeader = new byte[]{0x30, (byte) 0x82};
        byte[] outerLength = intToBytes(inner.length);
        return concat(seqHeader, outerLength, inner);
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) {
            total += a.length;
        }
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) (value & 0xff)};
    }
}
