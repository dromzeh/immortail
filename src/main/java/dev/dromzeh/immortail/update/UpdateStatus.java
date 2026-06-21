package dev.dromzeh.immortail.update;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Outcome of an update check. Immutable; safe to publish across threads. */
public record UpdateStatus(
    State state,
    String currentVersion,
    String currentCommitShort,
    String latestTag,
    String releaseUrl,
    int commitsBehind) {

  public enum State {
    /** check hasn't completed yet. */
    CHECKING,
    /** disabled via config. */
    DISABLED,
    /** the check failed (network, rate limit, unparseable response). */
    UNKNOWN,
    /** running the latest release and level with the default branch. */
    UP_TO_DATE,
    /** a newer release tag exists. */
    RELEASE_AVAILABLE,
    /** on the latest release (or a source build) but the default branch has newer commits. */
    COMMITS_BEHIND
  }

  static UpdateStatus checking(BuildInfo build) {
    return new UpdateStatus(State.CHECKING, build.version(), build.commitShort(), null, null, -1);
  }

  static UpdateStatus disabled(BuildInfo build) {
    return new UpdateStatus(State.DISABLED, build.version(), build.commitShort(), null, null, -1);
  }

  static UpdateStatus unknown(BuildInfo build) {
    return new UpdateStatus(State.UNKNOWN, build.version(), build.commitShort(), null, null, -1);
  }

  public boolean outdated() {
    return state == State.RELEASE_AVAILABLE || state == State.COMMITS_BEHIND;
  }

  /** Short coloured line for /immortail info (value only, no leading label). */
  public Component infoLine() {
    return switch (state) {
      case CHECKING -> Component.text("checking…", NamedTextColor.GRAY);
      case DISABLED -> Component.text("disabled", NamedTextColor.GRAY);
      case UNKNOWN -> Component.text("check failed", NamedTextColor.GRAY);
      case UP_TO_DATE -> Component.text("up to date", NamedTextColor.GREEN);
      case RELEASE_AVAILABLE ->
          link(
              Component.text(latestTag + " available", NamedTextColor.YELLOW)
                  .append(
                      Component.text(" (you have " + currentVersion + ")", NamedTextColor.GRAY)));
      case COMMITS_BEHIND -> Component.text(commitsBehindText(), NamedTextColor.YELLOW);
    };
  }

  /** Multi-line notice pushed to admins on join. Returns null when nothing to report. */
  public Component joinNotice() {
    return switch (state) {
      case RELEASE_AVAILABLE ->
          prefix()
              .append(Component.text("a new version is available: ", NamedTextColor.YELLOW))
              .append(Component.text(latestTag, NamedTextColor.WHITE))
              .append(Component.text(" (you have " + currentVersion + ")", NamedTextColor.GRAY))
              .append(Component.newline())
              .append(linkText());
      case COMMITS_BEHIND ->
          prefix().append(Component.text(commitsBehindText(), NamedTextColor.YELLOW));
      default -> null;
    };
  }

  /** Plain one-liner for the server log on startup. */
  public String logLine() {
    return switch (state) {
      case UP_TO_DATE -> "immortail is up to date (" + currentVersion + ")";
      case RELEASE_AVAILABLE ->
          "immortail is outdated - latest release "
              + latestTag
              + " (running "
              + currentVersion
              + "). "
              + releaseUrl;
      case COMMITS_BEHIND ->
          "immortail: " + commitsBehindText() + " (running " + currentVersion + ")";
      case UNKNOWN -> "immortail update check failed";
      default -> "immortail update check " + state.name().toLowerCase();
    };
  }

  private String commitsBehindText() {
    String tail = commitsBehind > 0 ? commitsBehind + " commits behind main" : "behind main";
    // distinguish "on latest release but main moved" from a plain source build
    if (latestTag != null) {
      return "latest release, but " + tail;
    }
    return "your build is " + tail;
  }

  private Component prefix() {
    return Component.text("[immortail] ", NamedTextColor.GREEN);
  }

  private Component linkText() {
    return link(Component.text(releaseUrl, NamedTextColor.AQUA));
  }

  private Component link(Component component) {
    return releaseUrl == null ? component : component.clickEvent(ClickEvent.openUrl(releaseUrl));
  }
}
