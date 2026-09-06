package com.knowhub.service.user;

import com.github.pagehelper.PageInfo;
import com.knowhub.mapper.user.AuthoringUserMapper;
import com.knowhub.pojo.user.quarry.AuthoringUserSearchQuarry;
import com.knowhub.pojo.user.vo.AuthoringUserVo;
import com.knowhub.service.user.impl.AuthoringUserService;
import com.rookie.common.util.PageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 前台选人 Service 实现。
 * <p>
 * 分页由 PageHelper 在 {@code PageUtil.startPage()} 接管，{@code searchAuthoringUsers} 仅返回当前页数据
 * （PageHelper 通过 ThreadLocal 注入 limit 与 count，配合 PageInfo 拼装 total/pageNum）。
 *
 * @author knowhub
 */
@Service
public class AuthoringUserServiceImpl implements AuthoringUserService {

    @Autowired
    AuthoringUserMapper authoringUserMapper;

    @Override
    public PageInfo<AuthoringUserVo> search(AuthoringUserSearchQuarry quarry) {
        PageUtil.startPage();
        List<AuthoringUserVo> list = authoringUserMapper.searchAuthoringUsers(quarry);
        return new PageInfo<>(list);
    }
}