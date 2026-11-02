package com.smallhairball.deepx;

import android.app.Activity;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between the Compose "MCP tools" page and {@link AgentMcpManager}. Exposes the server
 * list and the editor payload as plain data so the Compose layer can render Miuix rows without
 * touching host internals.
 */
public final class McpPageBridge {
    private McpPageBridge() {}

    /** One MCP server card. */
    public static final class Server {
        public final int index;
        public final String name;
        public final String endpoint;
        public final boolean enabled;
        public final String statusLine;
        public final String statusKind;

        Server(int index, String name, String endpoint, boolean enabled, String statusLine,
               String statusKind) {
            this.index = index;
            this.name = name;
            this.endpoint = endpoint;
            this.enabled = enabled;
            this.statusLine = statusLine;
            this.statusKind = statusKind;
        }
    }

    /** A custom request header. */
    public static final class Header {
        public final String name;
        public final String value;

        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /** Full editor payload for a single server. */
    public static final class Editor {
        public final boolean isNew;
        public final boolean enabled;
        public final String name;
        public final String url;
        public final String transport;
        public final List<Header> headers;

        Editor(boolean isNew, boolean enabled, String name, String url, String transport,
               List<Header> headers) {
            this.isNew = isNew;
            this.enabled = enabled;
            this.name = name;
            this.url = url;
            this.transport = transport;
            this.headers = headers;
        }
    }

    /** Reads the list of configured servers. */
    public static List<Server> servers(Activity activity) {
        List<Server> out = new ArrayList<>();
        if (activity == null) return out;
        List<AgentMcpManager.Config> profiles = AgentMcpManager.loadAll();
        AgentMcpManager.Status state = AgentMcpManager.status();
        for (int i = 0; i < profiles.size(); i++) {
            AgentMcpManager.Config config = profiles.get(i);
            boolean online = config.enabled && state.state == AgentMcpManager.State.CONNECTED;
            String statusLine;
            String kind;
            if (online) {
                statusLine = UiLanguage.text(activity,
                        "● 在线 · 已同步 " + config.tools.size() + " 个工具",
                        "● Online · " + config.tools.size() + " tools synced");
                kind = "online";
            } else if (config.enabled) {
                statusLine = UiLanguage.text(activity,
                        "● 未连接 · 点击卡片重新检测",
                        "● Offline · tap to re-check");
                kind = "offline";
            } else {
                statusLine = UiLanguage.text(activity, "○ 已停用", "○ Disabled");
                kind = "disabled";
            }
            out.add(new Server(
                    i,
                    config.name.length() == 0
                            ? UiLanguage.text(activity, "未命名 MCP", "Unnamed MCP")
                            : config.name,
                    config.url.length() == 0
                            ? UiLanguage.text(activity, "尚未填写地址", "No endpoint yet")
                            : config.url,
                    config.enabled,
                    statusLine,
                    kind));
        }
        return out;
    }

    /** True when no servers are configured. */
    public static boolean isEmpty(Activity activity) {
        if (activity == null) return true;
        return AgentMcpManager.loadAll().isEmpty();
    }

    /** List header copy. */
    public static String listIntro(Activity activity, boolean empty) {
        if (activity == null) return "";
        return empty
                ? UiLanguage.text(activity, "还没有 MCP 服务", "No MCP servers yet")
                : UiLanguage.text(activity, "已配置的 MCP 服务", "Configured MCP servers");
    }

    /** Empty state hint. */
    public static String emptyHint(Context context) {
        return UiLanguage.text(context,
                "点击右上角 ＋ 添加服务。保存并检测成功后，工具会自动同步到当前 Agent。",
                "Tap ＋ to add a server. Once it is saved and connected, its tools sync into the "
                        + "current Agent automatically.");
    }

    /** Loads an editor payload; pass {@code null} index for a new server. */
    public static Editor editor(Activity activity, Integer index) {
        if (activity == null) return null;
        if (index == null) {
            AgentMcpManager.Config value = AgentMcpManager.defaults();
            return new Editor(true, value.enabled, value.name, value.url, value.transport,
                    toHeaders(value.headers));
        }
        List<AgentMcpManager.Config> profiles = AgentMcpManager.loadAll();
        if (index < 0 || index >= profiles.size()) return null;
        AgentMcpManager.Config value = profiles.get(index);
        return new Editor(false, value.enabled, value.name, value.url, value.transport,
                toHeaders(value.headers));
    }

    private static List<Header> toHeaders(List<AgentMcpManager.Header> source) {
        List<Header> out = new ArrayList<>();
        for (AgentMcpManager.Header header : source) {
            out.add(new Header(header.name, header.value));
        }
        return out;
    }

    /** The two transport identifiers, in list order. */
    public static List<Header> transports(Context context) {
        List<Header> out = new ArrayList<>();
        out.add(new Header(AgentMcpManager.TRANSPORT_HTTP, "Streamable HTTP"));
        out.add(new Header(AgentMcpManager.TRANSPORT_SSE, "SSE"));
        return out;
    }

    /** Persists the editor payload. Returns true when the settings were stored. */
    public static boolean save(Activity activity, Integer index, boolean enabled, String name,
            String url, String transport, List<Header> headers) {
        if (activity == null) return false;
        List<AgentMcpManager.Header> stored = new ArrayList<>();
        if (headers != null) {
            for (Header header : headers) {
                String key = header.name == null ? "" : header.name.trim();
                if (key.length() > 0) {
                    stored.add(new AgentMcpManager.Header(key,
                            header.value == null ? "" : header.value));
                }
            }
        }
        AgentMcpManager.Config current = index == null ? AgentMcpManager.defaults()
                : index >= 0 && index < AgentMcpManager.loadAll().size()
                        ? AgentMcpManager.loadAll().get(index)
                        : AgentMcpManager.defaults();
        AgentMcpManager.Config nextConfig = current.with(enabled, name, url, transport, stored);
        List<AgentMcpManager.Config> next = new ArrayList<>(AgentMcpManager.loadAll());
        if (index == null || index < 0) {
            next.add(nextConfig);
        } else if (index < next.size()) {
            next.set(index, nextConfig);
        }
        return AgentMcpManager.saveAll(next);
    }

    /** Removes a configured server. Returns true on success. */
    public static boolean remove(Activity activity, int index) {
        if (activity == null || index < 0) return false;
        List<AgentMcpManager.Config> next = new ArrayList<>(AgentMcpManager.loadAll());
        if (index >= next.size()) return false;
        next.remove(index);
        return AgentMcpManager.saveAll(next);
    }

    /** Flips a server's enabled flag. Returns the resulting state on success. */
    public static boolean setEnabled(Activity activity, int index, boolean enabled) {
        if (activity == null || index < 0) return false;
        List<AgentMcpManager.Config> current = AgentMcpManager.loadAll();
        if (index >= current.size()) return false;
        AgentMcpManager.Config config = current.get(index);
        List<AgentMcpManager.Config> next = new ArrayList<>(current);
        next.set(index, config.with(enabled, config.name, config.url, config.transport,
                config.headers));
        return AgentMcpManager.saveAll(next);
    }

    /** Result listener for the async probe, safe to call from the Compose layer. */
    public interface ProbeListener {
        void onResult(boolean connected, String detail);
    }

    /** Probes every enabled server and reports the aggregate outcome. */
    public static void probeAll(Activity activity, ProbeListener listener) {
        if (activity == null) return;
        AgentMcpManager.probeAllAsync(activity, new AgentMcpManager.StatusCallback() {
            @Override public void onStatus(AgentMcpManager.Status status) {
                if (listener != null) {
                    listener.onResult(status.state == AgentMcpManager.State.CONNECTED,
                            status.detail);
                }
            }
        });
    }

    /** Copy for the request-header privacy hint. */
    public static String headerHint(Context context) {
        return UiLanguage.text(context, "请求值视为敏感信息，不会写入调用日志。",
                "Request values are treated as sensitive and never written to invocation logs.");
    }

    /** Editor button labels. */
    public static String addTitle(Context context) {
        return UiLanguage.text(context, "添加 MCP 服务", "Add MCP server");
    }

    static String editTitle(Context context) {
        return UiLanguage.text(context, "编辑 MCP 服务", "Edit MCP server");
    }
}