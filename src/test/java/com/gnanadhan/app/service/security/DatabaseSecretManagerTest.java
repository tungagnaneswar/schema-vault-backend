package com.schemavault.app.service.security;

import com.schemavault.app.service.security.DatabaseSecretManager;
import com.schemavault.app.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DatabaseSecretManagerTest {

    @Mock
    private EncryptionUtil encryptionUtil;

    @InjectMocks
    private DatabaseSecretManager secretManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void encrypt_ShouldCallEncryptionUtil() {
        String plainText = "password";
        String encryptedText = "encrypted";
        when(encryptionUtil.encrypt(plainText)).thenReturn(encryptedText);

        String result = secretManager.encrypt(plainText);

        assertEquals(encryptedText, result);
        verify(encryptionUtil, times(1)).encrypt(plainText);
    }

    @Test
    void decrypt_ShouldCallEncryptionUtil() {
        String encryptedText = "encrypted";
        String plainText = "password";
        when(encryptionUtil.decrypt(encryptedText)).thenReturn(plainText);

        String result = secretManager.decrypt(encryptedText);

        assertEquals(plainText, result);
        verify(encryptionUtil, times(1)).decrypt(encryptedText);
    }
}
