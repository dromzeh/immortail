package dev.dromzeh.immortail.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Checks github for newer versions. Compares the running version against the latest release tag,
 * and (for source builds) the build commit against the default branch. Network calls run off the
 * main thread; the result is published to a volatile field read elsewhere.
 */
public class UpdateChecker {

  private static final String API = "https://api.github.com/repos/";
  private static final String DEFAULT_REPO = "dromzeh/immortail";
  private static final String BRANCH = "main";

  private final JavaPlugin plugin;
  private final BuildInfo build;
  private final String repo;
  private final HttpClient http;

  private volatile UpdateStatus status;

  public UpdateChecker(JavaPlugin plugin) {
    this.plugin = plugin;
    this.build = BuildInfo.load(plugin);
    this.repo = repoFromWebsite(plugin.getDescription().getWebsite());
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.status = UpdateStatus.checking(build);
  }

  public BuildInfo buildInfo() {
    return build;
  }

  public UpdateStatus status() {
    return status;
  }

  public void disable() {
    this.status = UpdateStatus.disabled(build);
  }

  /** Runs a check on an async scheduler thread. */
  public void checkAsync() {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, this::check);
  }

  /** Blocking check. Must NOT be called on the main thread. */
  public void check() {
    UpdateStatus result;
    try {
      result = resolve();
    } catch (Exception e) {
      result = UpdateStatus.unknown(build);
    }
    this.status = result;

    switch (result.state()) {
      case RELEASE_AVAILABLE, COMMITS_BEHIND -> plugin.getLogger().warning(result.logLine());
      case UNKNOWN -> plugin.getLogger().fine(result.logLine());
      default -> plugin.getLogger().info(result.logLine());
    }
  }

  private UpdateStatus resolve() {
    JsonObject release = getJson(API + repo + "/releases/latest");
    String latestTag = release != null && release.has("tag_name") ? str(release, "tag_name") : null;
    String releaseUrl =
        release != null && release.has("html_url")
            ? str(release, "html_url")
            : "https://github.com/" + repo + "/releases/latest";

    // only release builds (a clean semver matching a tag) compare by version; dev/source
    // builds carry a git-describe suffix and lean on the commit comparison below instead.
    boolean releaseBuild = isReleaseVersion(build.version());
    int releaseCmp =
        releaseBuild && latestTag != null ? compareVersions(build.version(), latestTag) : 1;

    int commitsBehind = -1;
    if (build.hasCommit()) {
      JsonObject compare = getJson(API + repo + "/compare/" + build.commit() + "..." + BRANCH);
      if (compare != null && compare.has("ahead_by")) {
        // ahead_by = commits the branch head has that our build doesn't = how far we're behind
        commitsBehind = compare.get("ahead_by").getAsInt();
      }
    }

    UpdateStatus.State state;
    if (latestTag != null && releaseCmp < 0) {
      state = UpdateStatus.State.RELEASE_AVAILABLE;
    } else if (commitsBehind > 0) {
      state = UpdateStatus.State.COMMITS_BEHIND;
    } else if (latestTag != null || commitsBehind == 0) {
      state = UpdateStatus.State.UP_TO_DATE;
    } else {
      state = UpdateStatus.State.UNKNOWN;
    }

    return new UpdateStatus(
        state, build.version(), build.commitShort(), latestTag, releaseUrl, commitsBehind);
  }

  private JsonObject getJson(String url) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(10))
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .header("User-Agent", "immortail-update-checker")
              .GET()
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return null;
      }
      return JsonParser.parseString(response.body()).getAsJsonObject();
    } catch (Exception e) {
      return null;
    }
  }

  private static String str(JsonObject obj, String key) {
    return obj.get(key).getAsString();
  }

  private static String repoFromWebsite(String website) {
    if (website != null) {
      int idx = website.indexOf("github.com/");
      if (idx >= 0) {
        String path = website.substring(idx + "github.com/".length());
        if (path.endsWith("/")) {
          path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith(".git")) {
          path = path.substring(0, path.length() - ".git".length());
        }
        if (path.chars().filter(c -> c == '/').count() == 1) {
          return path;
        }
      }
    }
    return DEFAULT_REPO;
  }

  /** Lenient semver compare. Negative when {@code current} is older than {@code latest}. */
  static int compareVersions(String current, String latest) {
    int[] c = parse(current);
    int[] l = parse(latest);
    for (int i = 0; i < 3; i++) {
      if (c[i] != l[i]) {
        return Integer.compare(c[i], l[i]);
      }
    }
    // equal core version: a pre-release (e.g. 1.1.0-dev) is older than the plain release
    boolean cPre = hasPreRelease(current);
    boolean lPre = hasPreRelease(latest);
    if (cPre && !lPre) {
      return -1;
    }
    if (!cPre && lPre) {
      return 1;
    }
    return 0;
  }

  private static int[] parse(String version) {
    String core = strip(version);
    int cut = core.length();
    for (int i = 0; i < core.length(); i++) {
      char ch = core.charAt(i);
      if (ch == '-' || ch == '+') {
        cut = i;
        break;
      }
    }
    String[] parts = core.substring(0, cut).split("\\.");
    int[] out = new int[3];
    for (int i = 0; i < 3; i++) {
      out[i] = i < parts.length ? parseInt(parts[i]) : 0;
    }
    return out;
  }

  private static boolean hasPreRelease(String version) {
    return strip(version).indexOf('-') >= 0;
  }

  /** True for a plain release semver (e.g. 1.2 or 1.2.0); false for git-describe/dev versions. */
  static boolean isReleaseVersion(String version) {
    return strip(version).matches("\\d+(\\.\\d+){1,2}");
  }

  private static String strip(String version) {
    String v = version == null ? "" : version.trim();
    if (v.startsWith("v") || v.startsWith("V")) {
      v = v.substring(1);
    }
    return v;
  }

  private static int parseInt(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
