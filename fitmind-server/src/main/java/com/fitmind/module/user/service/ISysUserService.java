package com.fitmind.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.user.entity.SysUser;

public interface ISysUserService extends IService<SysUser> {
    String login(String username, String password);
    void register(SysUser user);
    void changePassword(String username, String oldPassword, String newPassword);
}
