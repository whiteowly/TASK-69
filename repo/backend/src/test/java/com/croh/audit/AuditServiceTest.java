package com.croh.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void log_savesAuditLogWithCorrectFields() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog result = auditService.log(
                1L, "ADMIN", "LOGIN", "ACCOUNT", "1",
                null, "{\"status\":\"ACTIVE\"}", null, "corr-123"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertNotNull(saved.getEventId());
        assertNotNull(saved.getTimestamp());
        assertEquals(1L, saved.getActorAccountId());
        assertEquals("ADMIN", saved.getActorRole());
        assertEquals("LOGIN", saved.getActionType());
        assertEquals("ACCOUNT", saved.getObjectType());
        assertEquals("1", saved.getObjectId());
        assertEquals("{\"status\":\"ACTIVE\"}", saved.getAfterState());
        assertEquals("corr-123", saved.getCorrelationId());
    }
}
