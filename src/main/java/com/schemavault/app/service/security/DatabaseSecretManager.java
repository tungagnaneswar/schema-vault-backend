package com.schemavault.app.service.security;

import com.schemavault.app.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseSecretManager implements SecretManager {

    private final EncryptionUtil encryptionUtil;

    @Override
    public String encrypt(String plainText) {
        return encryptionUtil.encrypt(plainText);
    }

    @Override
    public String decrypt(String encryptedText) {
        return encryptionUtil.decrypt(encryptedText);
    }
}
