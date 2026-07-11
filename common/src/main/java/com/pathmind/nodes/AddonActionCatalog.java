package com.pathmind.nodes;

import com.pathmind.api.addon.ActionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the machine-readable catalog of script-invocable actions behind
 * {@link com.pathmind.api.addon.PathmindRuntime#listActions()}.
 *
 * <p><strong>Single source of truth:</strong> the catalog is derived from the exact
 * inputs {@link AddonActionInvoker} uses at dispatch time — {@link NodeType} filtered
 * by {@link AddonActionInvoker#isInvocable}, and the parameter list of a synthetic
 * {@code new Node(type, 0, 0)} (the same constructor the invoker builds). Every
 * catalog entry is therefore invocable, and every invocable action is in the catalog;
 * nothing is hand-maintained, so new node types appear automatically.
 *
 * <p><strong>MESSAGE special case:</strong> the Message node stores its text in
 * {@code messageLines}, outside the parameter list; the invoker accepts the synthetic
 * argument {@code "text"} for it, so the catalog reports it the same way.
 *
 * <p><strong>Threading:</strong> {@code Node} construction is headless-safe (plain
 * unit tests construct arbitrary node types), so the catalog can be built on any
 * thread. Display names/descriptions resolve through the MC language manager when
 * one is loaded and fall back to the raw translation key otherwise (headless tests).
 *
 * <p>The result is cached after the first build: node definitions are static for the
 * lifetime of the JVM.
 */
public final class AddonActionCatalog {

    private static volatile List<ActionInfo> cached;

    private AddonActionCatalog() {
    }

    /**
     * Returns the catalog of invocable actions, sorted by name. Built once, cached.
     *
     * @return immutable list of {@link ActionInfo}; never null
     */
    public static List<ActionInfo> list() {
        List<ActionInfo> result = cached;
        if (result == null) {
            synchronized (AddonActionCatalog.class) {
                result = cached;
                if (result == null) {
                    result = build();
                    cached = result;
                }
            }
        }
        return result;
    }

    private static List<ActionInfo> build() {
        List<ActionInfo> actions = new ArrayList<>();
        for (NodeType type : NodeType.values()) {
            if (!AddonActionInvoker.isInvocable(type)) {
                continue;
            }
            actions.add(describe(type));
        }
        actions.sort((a, b) -> a.name().compareTo(b.name()));
        return List.copyOf(actions);
    }

    /** Builds one catalog entry from a synthetic node of the given type. */
    private static ActionInfo describe(NodeType type) {
        List<ActionInfo.Param> params = new ArrayList<>();
        // MESSAGE: the invoker maps the synthetic "text" argument onto messageLines.
        if (type == NodeType.MESSAGE) {
            params.add(new ActionInfo.Param("text", "TEXT", ""));
        }
        try {
            Node node = new Node(type, 0, 0);
            for (NodeParameter param : node.getParameters()) {
                params.add(new ActionInfo.Param(
                    param.getName(),
                    param.getType() != null ? param.getType().name() : "TEXT",
                    param.getDefaultValue() != null ? param.getDefaultValue() : ""));
            }
        } catch (Throwable t) {
            // A node type whose construction fails still dispatches through the invoker
            // (which would surface the same failure) — report it with what we know
            // rather than silently dropping it from the catalog.
            System.err.println("[Pathmind] Action catalog: failed to inspect parameters of "
                + type.name() + ": " + t.getMessage());
        }
        return new ActionInfo(
            type.name().toLowerCase(Locale.ROOT),
            safeTranslate(type, true),
            safeTranslate(type, false),
            List.copyOf(params));
    }

    /**
     * Resolves the display name / description via the MC language manager, falling back
     * to the enum name / empty string when translation is unavailable (headless tests).
     */
    private static String safeTranslate(NodeType type, boolean displayName) {
        try {
            return displayName ? type.getDisplayName() : type.getDescription();
        } catch (Throwable t) {
            return displayName ? type.name().toLowerCase(Locale.ROOT) : "";
        }
    }
}
