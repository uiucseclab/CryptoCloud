package com.truszko1.cs460.cryptocloud.app;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

public class Crypto {

    public static final String PBKDF2_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int PKCS5_SALT_LENGTH = 8;
    private static SecureRandom random = new SecureRandom();

    private Crypto() {
    }

    public static SecretKey deriveKeyPbkdf2(byte[] salt, String password) {
        try {
            long start = System.currentTimeMillis();
            int ITERATION_COUNT = 10000;
            int KEY_LENGTH = 256;
            KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt,
                    ITERATION_COUNT, KEY_LENGTH);
            SecretKeyFactory keyFactory = SecretKeyFactory
                    .getInstance(PBKDF2_DERIVATION_ALGORITHM);
            byte[] keyBytes = keyFactory.generateSecret(keySpec).getEncoded();

            SecretKey result = new SecretKeySpec(keyBytes, "AES");
            long elapsed = System.currentTimeMillis() - start;

            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] generateIv(int length) {
        byte[] b = new byte[length];
        random.nextBytes(b);

        return b;
    }

    public static byte[] generateSalt() {
        byte[] b = new byte[PKCS5_SALT_LENGTH];
        random.nextBytes(b);

        return b;
    }


    public static byte[][] encrypt(byte[] plaintext, SecretKey key, byte[] salt) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

            byte[] iv = generateIv(cipher.getBlockSize());
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
            byte[] cipherText = cipher.doFinal(plaintext);

            return new byte[][]{salt, iv, cipherText};

        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[][] decrypt(byte[] cipherBytes, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, key, ivParams);
            byte[] plaintext = cipher.doFinal(cipherBytes);
            return new byte[][]{plaintext};
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[][] decryptPbkdf2(byte[][] encryptedInfo, String password) {
        byte[] salt = encryptedInfo[0];
        byte[] iv = encryptedInfo[1];
        byte[] cipherBytes = encryptedInfo[2];
        SecretKey key = deriveKeyPbkdf2(salt, password);

        return decrypt(cipherBytes, key, iv);
    }

}
