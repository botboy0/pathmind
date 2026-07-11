package com.pathmind.api.addon;

import java.util.List;

/**
 * Describes one script-invocable Pathmind action — an entry in the catalog returned
 * by {@link PathmindRuntime#listActions()}.
 *
 * <p>The catalog is the machine-readable form of the action surface that
 * {@link PathmindRuntime#invokeAction} accepts: {@code name} is the invocable
 * action name (lowercase node-type name, e.g. {@code "jump"}, {@code "press_key"}),
 * and {@code params} lists the argument names {@code invokeAction} matches
 * case-insensitively, in the order the node shows them in the editor.
 *
 * <p>Addons use this to generate direct per-action bindings and editor completion
 * without hardcoding the node list — new Pathmind actions appear automatically,
 * with signatures and descriptions derived from the node definitions.
 *
 * @param name        lowercase action name, valid as the {@code invokeAction} action name
 * @param displayName the node's localized display name (e.g. {@code "Goto"}); falls back
 *                    to the translation key when no language is loaded (headless)
 * @param description the node's localized one-line description (e.g. {@code "Moves to
 *                    specified coordinates"}); falls back to the translation key headless
 * @param params      parameter descriptors in editor order; empty for parameter-less actions
 */
public record ActionInfo(String name, String displayName, String description, List<Param> params) {

    /**
     * One invocable parameter of an action.
     *
     * @param name         parameter name as shown on the node in the editor — the key
     *                     {@code invokeAction} matches (case-insensitively)
     * @param type         parameter-type discriminator (the {@code ParameterType} enum
     *                     constant name, e.g. {@code "INTEGER"}, {@code "TEXT"},
     *                     {@code "BOOLEAN"}); informational, values are passed as
     *                     number/string/boolean regardless
     * @param defaultValue the node's default value string; may be empty, never null
     */
    public record Param(String name, String type, String defaultValue) {
    }
}
