package com.rookie.framework.security.pojo;

import org.springframework.security.core.GrantedAuthority;



public class Permission implements GrantedAuthority {
    private String permKey;

    public Permission(String permKey) {
        this.permKey = permKey;
    }

    @Override
    public String getAuthority() {
        return permKey;
    }

    @Override
    public String toString() {
        return "Permission{" +
                "permKey='" + permKey + '\'' +
                '}';
    }

    public String getPermKey() {
        return permKey;
    }

    public void setPermKey(String permKey) {
        this.permKey = permKey;
    }
}
