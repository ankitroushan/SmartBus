package com.ankit.bustracking.security;

import com.ankit.bustracking.enums.AccountStatus;
import com.ankit.bustracking.model.Driver;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;


public class CustomUserDetails implements UserDetails {

    private final Driver driver;

    public CustomUserDetails(Driver driver) {
        this.driver = driver;
    }

    @Override
   public String getUsername(){
      return driver.getDriverId();
    }

    @Override
    public String getPassword(){
        return driver.getPinHash();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return driver.getAccountStatus() == AccountStatus.ACTIVE;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return List.of(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }


}
