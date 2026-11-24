package app.rimlit.model

/**
 * How the user left the app's own screens, remembered across sessions: never how a glow looks or behaves, so it lives
 * apart from [GlowSettings], and changing it wakes neither the engine nor the image clean-up.
 */
data class UiPrefs(
    /** The look editor's tabs showing every control ("Tinker"). */
    val tinkering: Set<LookPart> = emptySet(),
    /** Every editor tab shows every control, whatever its own "Tinker" says ("Always tinker", in Behavior). */
    val alwaysTinker: Boolean = false,
    /**
     * The recommended permissions the user put off ("Not now"), by name (`app.rimlit.permissions.PermissionKind`):
     * shown in Behavior instead of on Home. Names, since the model knows nothing of Android's permissions.
     */
    val dismissedPermissions: Set<String> = emptySet(),
    /** The guided first launch was finished; until then the app opens on it, upgraders included. */
    val setupDone: Boolean = false,
) {
    /** The editor's [part] tab shows every control: "Always tinker", or that tab's own "Tinker". */
    fun tinkers(part: LookPart): Boolean = alwaysTinker || part in tinkering
}
