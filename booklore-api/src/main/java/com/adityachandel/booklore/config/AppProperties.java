package com.adityachandel.booklore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {
    private String pathConfig;
    private String bookdropFolder;
    private String version;
    private RemoteAuth remoteAuth;
    private Swagger swagger = new Swagger();
    private Boolean forceDisableOidc = false;

    @Getter
    @Setter
    public static class RemoteAuth {
        private boolean enabled;
        private boolean createNewUsers;
        private String headerName;
        private String headerUser;
        private String headerEmail;
        private String headerGroups;
        private String adminGroup;
    }

    @Getter
    @Setter
    public static class Swagger {
        private boolean enabled = true;
    }
}
