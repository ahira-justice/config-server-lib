package com.ahirajustice.lib.configserver.utils;

import com.ahirajustice.lib.configserver.exceptions.ConfigServerConfigurationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CipherUtils {

    private static PrivateKey getPrivateKey(String privateKeyString) {
        PKCS8EncodedKeySpec keySpec;
        KeyFactory kf;
        PrivateKey privateKey;

        try {
            byte [] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);
            keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            kf = KeyFactory.getInstance("RSA");
            privateKey = kf.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new ConfigServerConfigurationException(ex.getMessage());
        }

        return privateKey;
    }

    public static String decryptString(String value, String privateKeyString) {
        String decryptedMessage;

        try {
            PrivateKey privateKey = getPrivateKey(privateKeyString);

            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] secretMessageBytes = Base64.getDecoder().decode(value);
            byte[] decryptedMessageBytes = decryptCipher.doFinal(secretMessageBytes);
            decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);
        }
        catch (IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | NoSuchAlgorithmException ex) {
            throw new ConfigServerConfigurationException(ex.getMessage());
        }
        catch (ConfigServerConfigurationException | InvalidKeyException ex) {
            throw new ConfigServerConfigurationException("Configured private key is invalid. Update configured private key");
        }

        return decryptedMessage;
    }
}
