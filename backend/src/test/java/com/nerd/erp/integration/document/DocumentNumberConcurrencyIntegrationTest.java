package com.nerd.erp.integration.document;

import com.nerd.erp.application.document.DocumentNumberService;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.domain.organization.Organization;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.organization.OrganizationRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import com.nerd.erp.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N threads demandent un numéro en même temps sur une séquence <b>qui n'existe pas encore</b>
 * (nouveau workspace) : aucun doublon, aucune erreur, numéros 1..N consécutifs.
 */
class DocumentNumberConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final int THREADS = 24;

    @Autowired
    DocumentNumberService documentNumberService;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    WorkspaceRepository workspaceRepository;

    @Test
    void concurrentCallsOnFreshSequenceProduceDistinctConsecutiveNumbers() throws Exception {
        Tenant tenant = tenantRepository.findBySlug("test-tenant").orElseThrow();
        Organization org = organizationRepository.findByTenantIdOrderByLegalNameAsc(tenant.getId()).getFirst();
        Workspace ws = new Workspace();
        ws.setTenantId(tenant.getId());
        ws.setOrganizationId(org.getId());
        ws.setName("Seq " + UUID.randomUUID());
        ws.setSlug("seq-" + UUID.randomUUID().toString().substring(0, 8));
        ws = workspaceRepository.save(ws);
        UUID workspaceId = ws.getId();
        UUID tenantId = tenant.getId();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                Callable<String> task = () -> {
                    start.await(10, TimeUnit.SECONDS);
                    return documentNumberService.nextNumber(tenantId, workspaceId, DocumentSequenceType.QUOTATION, "DEV");
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            Set<String> numbers = new TreeSet<>();
            for (Future<String> f : futures) {
                numbers.add(f.get(30, TimeUnit.SECONDS));
            }
            assertEquals(THREADS, numbers.size(), "Doublons détectés : " + numbers);
            int year = Year.now().getValue();
            for (int n = 1; n <= THREADS; n++) {
                String expected = "DEV-%d-%05d".formatted(year, n);
                assertTrue(numbers.contains(expected), "Numéro manquant : " + expected + " dans " + numbers);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
