package com.hitstudio.syncup.client.network.discovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ServerDiscoveryRepositoryTest {

    @Test
    public void prefersAppVersionWhenPresent() {
        assertEquals(
                "2.4.1",
                ServerDiscoveryRepository.readApplicationVersion(
                        "2.4.1",
                        "2.4.0",
                        "2.3.9",
                        "2.3.8"
                )
        );
    }

    @Test
    public void fallsBackToApplicationVersionThenLegacyFields() {
        assertEquals(
                "2.4.0",
                ServerDiscoveryRepository.readApplicationVersion(
                        null,
                        "2.4.0",
                        "2.3.9",
                        "2.3.8"
                )
        );
    }

    @Test
    public void trimsAndIgnoresBlankValues() {
        assertEquals(
                "2.4.0",
                ServerDiscoveryRepository.readApplicationVersion(
                        "   ",
                        "  2.4.0  ",
                        " 2.3.9 ",
                        " "
                )
        );
    }

    @Test
    public void returnsNullWhenNoVersionIsAvailable() {
        assertNull(ServerDiscoveryRepository.readApplicationVersion(null, null, null, null));
    }
}
