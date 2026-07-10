package com.pathmind.execution;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Semantics of the {@link AddonLiveScripts} hot-reload channel.
 *
 * <p>The registry is a static map, so each test uses a fresh UUID key — entries never
 * collide across tests, mirroring production where node ids are UUIDs and never reused.
 */
class AddonLiveScriptsTest {

    private static String freshId() {
        return UUID.randomUUID().toString();
    }

    @Test
    void getReturnsNullForUnknownNode() {
        assertNull(AddonLiveScripts.get(freshId()));
        assertNull(AddonLiveScripts.get(null));
    }

    @Test
    void publishThenGetRoundTrips() {
        String id = freshId();
        AddonLiveScripts.publish(id, "print('v1')");
        assertEquals("print('v1')", AddonLiveScripts.get(id));
    }

    @Test
    void latestPublishWins() {
        String id = freshId();
        AddonLiveScripts.publish(id, "print('v1')");
        AddonLiveScripts.publish(id, "print('v2')");
        assertEquals("print('v2')", AddonLiveScripts.get(id));
    }

    @Test
    void nullOrBlankNodeIdIsIgnored() {
        AddonLiveScripts.publish(null, "x");
        AddonLiveScripts.publish("", "x");
        AddonLiveScripts.publish("  ", "x");
        assertNull(AddonLiveScripts.get(""));
        assertNull(AddonLiveScripts.get("  "));
    }

    @Test
    void nullScriptDoesNotClobberPublishedText() {
        String id = freshId();
        AddonLiveScripts.publish(id, "print('keep')");
        AddonLiveScripts.publish(id, null); // context without script — ignored
        assertEquals("print('keep')", AddonLiveScripts.get(id));
    }

    @Test
    void removeEvictsTheEntry() {
        String id = freshId();
        AddonLiveScripts.publish(id, "print('gone')");
        AddonLiveScripts.remove(id);
        assertNull(AddonLiveScripts.get(id));
    }

    @Test
    void removeUnknownOrNullIsSafe() {
        AddonLiveScripts.remove(freshId());
        AddonLiveScripts.remove(null);
    }

    @Test
    void emptyStringScriptIsAValidPublish() {
        // An empty script is a deliberate user state (delete-all) and must override
        // the frozen snapshot on the next execution — only null means "no news".
        String id = freshId();
        AddonLiveScripts.publish(id, "print('old')");
        AddonLiveScripts.publish(id, "");
        assertEquals("", AddonLiveScripts.get(id));
    }
}
