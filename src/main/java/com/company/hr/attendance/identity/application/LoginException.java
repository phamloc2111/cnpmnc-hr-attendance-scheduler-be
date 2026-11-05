package com.company.hr.attendance.identity.application;

public class LoginException extends RuntimeException {
    private final boolean locked;

    public LoginException(boolean locked) {
        super(locked ? "Tài khoản đã bị khóa" : "Email hoặc mật khẩu không đúng");
        this.locked = locked;
    }

    public boolean isLocked() { return locked; }
}
