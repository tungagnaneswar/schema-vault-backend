package com.gnanadhan.app.service.security;

public interface SecretManager {
    String encrypt(String plainText);
    String decrypt(String encryptedText);
}
