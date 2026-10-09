package com.ngs.analytics.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ngs")
public class NgsProperties {

    private final Jwt jwt = new Jwt();
    private final Storage storage = new Storage();
    private final Auth auth = new Auth();
    private final Ai ai = new Ai();

    public Jwt getJwt() {
        return jwt;
    }

    public Storage getStorage() {
        return storage;
    }

    public Auth getAuth() {
        return auth;
    }

    public Ai getAi() {
        return ai;
    }

    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }
    }

    public static class Storage {
        private String localDir = "../data/uploads";
        private long maxUploadBytes = 2147483648L;

        public String getLocalDir() {
            return localDir;
        }

        public void setLocalDir(String localDir) {
            this.localDir = localDir;
        }

        public long getMaxUploadBytes() {
            return maxUploadBytes;
        }

        public void setMaxUploadBytes(long maxUploadBytes) {
            this.maxUploadBytes = maxUploadBytes;
        }
    }

    public static class Auth {
        private boolean disabled = false;
        private String devEmail = "dev@genometrics.local";

        public boolean isDisabled() {
            return disabled;
        }

        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        public String getDevEmail() {
            return devEmail;
        }

        public void setDevEmail(String devEmail) {
            this.devEmail = devEmail;
        }
    }

    public static class Ai {
        private boolean enabled;
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.2";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
