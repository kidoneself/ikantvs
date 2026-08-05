package com.jyinshi.search.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 网盘分享链加密（对齐老站）：前端只持 token，转存时服务端解密。
 * 格式 {@code saltHex:base64(AES(panType|url|password))}。
 */
public final class LinkEncryptUtil {

    private static final String SECRET_KEY = "jyinshi2025quark";
    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkEncryptUtil() {
    }

    public static String encrypt(String url, String password, String panType) {
        try {
            String data = panType + "|" + url + "|" + (password != null ? password : "");
            byte[] salt = new byte[8];
            RANDOM.nextBytes(salt);
            String saltHex = bytesToHex(salt);

            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            String encryptedBase64 = Base64.getEncoder().encodeToString(
                    cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            return saltHex + ":" + encryptedBase64;
        } catch (Exception e) {
            throw new IllegalStateException("链接加密失败", e);
        }
    }

    /** @return [panType, url, password] */
    public static String[] decrypt(String encryptedData) {
        try {
            String[] parts = encryptedData.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("无效的加密数据格式");
            }
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            String decrypted = new String(
                    cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
            String[] fields = decrypted.split("\\|", 3);
            if (fields.length < 2) {
                throw new IllegalArgumentException("解密数据不完整");
            }
            String pan = fields[0];
            String url = fields[1];
            String pwd = fields.length > 2 ? fields[2] : "";
            return new String[]{pan, url, pwd};
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("链接解密失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
