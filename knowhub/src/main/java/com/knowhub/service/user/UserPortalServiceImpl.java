package com.knowhub.service.user;

import com.knowhub.mapper.user.UserPortalMapper;
import com.knowhub.pojo.user.vo.UserPortalVo;
import com.knowhub.service.user.impl.UserPortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户公开主页 Service 实现：薄封装 UserPortalMapper，按 userId 单查公开信息。
 *
 * @author knowhub
 */
@Service
public class UserPortalServiceImpl implements UserPortalService {

    @Autowired
    UserPortalMapper userPortalMapper;

    @Override
    public UserPortalVo getPublicUserById(Long userId) {
        return userPortalMapper.getPublicUserById(userId);
    }
}
