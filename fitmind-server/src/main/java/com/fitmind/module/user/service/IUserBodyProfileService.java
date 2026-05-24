package com.fitmind.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.user.entity.UserBodyProfile;

public interface IUserBodyProfileService extends IService<UserBodyProfile> {
    UserBodyProfile getByUsername(String username);
    void saveOrUpdateProfile(String username, UserBodyProfile profile);
}
