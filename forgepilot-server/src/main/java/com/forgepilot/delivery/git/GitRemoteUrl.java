package com.forgepilot.delivery.git;

import java.net.URI;
import java.util.Locale;

/**
 * 解析 Git clone URL（https、ssh、git@、file://），并识别 github.com。
 * 用于托管类型推断和 GitHub API 的 owner/repo 提取。
 */
public final class GitRemoteUrl {

    public static final String GITHUB = "GITHUB";
    public static final String GITLAB = "GITLAB";

    private final String original;
    private final String host;
    private final String owner;
    private final String repo;
    private final boolean file;

    private GitRemoteUrl(String original, String host, String owner, String repo, boolean file) {
        this.original = original;
        this.host = host;
        this.owner = owner;
        this.repo = repo;
        this.file = file;
    }

    public static GitRemoteUrl parse(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw new IllegalArgumentException("仓库地址不能为空");
        }
        String raw = cloneUrl.trim();
        if (isFileUrl(raw)) {
            return new GitRemoteUrl(raw, null, null, null, true);
        }
        // scp-like：git@github.com:owner/repo.git
        if (!raw.contains("://") && raw.contains("@") && raw.contains(":")) {
            int at = raw.indexOf('@');
            String rest = raw.substring(at + 1);
            int colon = rest.indexOf(':');
            String host = rest.substring(0, colon);
            String path = rest.substring(colon + 1);
            return fromHostPath(raw, host, path);
        }
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("无法解析仓库地址", ex);
        }
        if (uri.getScheme() != null && "file".equalsIgnoreCase(uri.getScheme())) {
            return new GitRemoteUrl(raw, null, null, null, true);
        }
        return fromHostPath(raw, uri.getHost(), uri.getPath());
    }

    public static GitRemoteUrl tryParse(String cloneUrl) {
        try {
            return parse(cloneUrl);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static boolean isGitHub(String cloneUrl) {
        GitRemoteUrl parsed = tryParse(cloneUrl);
        return parsed != null && parsed.isGitHub();
    }

    public static boolean isFile(String cloneUrl) {
        return cloneUrl != null && isFileUrl(cloneUrl.trim());
    }

    /**
     * 能从地址明确判断时返回 {@code GITHUB}，否则返回 {@code null}（由调用方决定默认值）。
     */
    public static String detectProvider(String cloneUrl) {
        return isGitHub(cloneUrl) ? GITHUB : null;
    }

    public String original() {
        return original;
    }

    public String host() {
        return host;
    }

    public String owner() {
        return owner;
    }

    public String repo() {
        return repo;
    }

    public boolean isFile() {
        return file;
    }

    public boolean isGitHub() {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return "github.com".equals(h) || h.endsWith(".github.com");
    }

    public String ownerRepo() {
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            return "";
        }
        return owner + "/" + repo;
    }

    public boolean isGithubDotCom() {
        return isGitHub() && "github.com".equalsIgnoreCase(host);
    }

    private static boolean isFileUrl(String raw) {
        return raw.startsWith("file:") || raw.startsWith("/");
    }

    private static GitRemoteUrl fromHostPath(String original, String host, String path) {
        String cleaned = path == null ? "" : path.trim();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.endsWith(".git")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        String owner = "";
        String repo = "";
        if (!cleaned.isEmpty()) {
            String[] parts = cleaned.split("/");
            owner = parts[0];
            if (parts.length > 1) {
                repo = parts[1];
            }
        }
        String normalizedHost = host == null || host.isBlank() ? null : host;
        return new GitRemoteUrl(original, normalizedHost, owner, repo, false);
    }
}
